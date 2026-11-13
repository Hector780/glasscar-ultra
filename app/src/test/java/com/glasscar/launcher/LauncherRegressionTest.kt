package com.glasscar.launcher

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherRegressionTest {
    private lateinit var context: Context
    private fun app(label: String, pkg: String) = AppEntry(label, pkg, "$pkg.Main", Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).asImageBitmap())
    @Before fun reset() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("glasscar_v4", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun appleMusicWinsEvenWhenNativeMusicAndSpotifySortFirst() {
        val apps = listOf(app("Music", "com.ts.music"), app("Spotify", "com.spotify.music"), app("Apple Music", "com.apple.android.music"))
        assertEquals("com.apple.android.music", AppsRepository.find(apps, DockShortcuts[1])?.packageName)
    }
    @Test fun cameraDoesNotFallBackToAux() {
        assertNull(AppsRepository.find(listOf(app("AUX", "com.ts.avin")), QuickShortcuts[3]))
    }
    @Test fun phoneGoogleFilesAndBluetoothHaveCorrectIconAssociations() {
        assertEquals("icon_phone_correct", AppsRepository.findRule("com.ts.bt", "Phone")?.resName)
        assertEquals("icon_bluetooth_correct", AppsRepository.findRule("com.tw.bt", "Bluetooth")?.resName)
        assertNull(AppsRepository.findRule("com.google.android.googlequicksearchbox", "Google"))
        assertEquals("icon_files_correct", AppsRepository.findRule("vendor.file", "File manager")?.resName)
        assertEquals("icon_maps_consistent", AppsRepository.findRule("com.google.android.apps.maps", "Maps")?.resName)
    }
    @Test fun appChoiceSurvivesRestartAndMissingAppDoesNotLaunchAnUnrelatedFallback() {
        val chosen = app("Mi reproductor", "test.player")
        val prefs = LauncherPreferences(context)
        prefs.assign("music", chosen)
        val restored = LauncherPreferences(context).state.value
        assertEquals(chosen, resolveApp(listOf(chosen), restored, "music", DockShortcuts[1]))
        assertNull(resolveApp(listOf(app("Music", "com.ts.music")), restored, "music", DockShortcuts[1]))
    }
    @Test fun recentsAreOrderedDeduplicatedAndPersisted() {
        val a = app("A", "a"); val b = app("B", "b")
        val prefs = LauncherPreferences(context)
        prefs.record(a); prefs.record(b); prefs.record(a)
        assertEquals(listOf(a, b), recentApps(listOf(a,b), LauncherPreferences(context).state.value))
    }
    @Test fun brightnessNeverWritesZeroAndClampsOutOfRangeInput() {
        assertEquals(31, HeadUnitControls.brightnessValue(0f))
        assertEquals(255, HeadUnitControls.brightnessValue(2f))
        assertEquals(128, HeadUnitControls.brightnessValue(0.5f))
    }
    @Test fun weatherKeepsSourceCoordinatesObservationTimeAndRealValues() {
        val json = JSONObject("""{"current":{"time":1794330000,"temperature_2m":23.4,"weather_code":0,"wind_speed_10m":11.4,"relative_humidity_2m":52}}""")
        val weather = parseWeather(json, 32.5, -117.0, 1794330100000)
        assertEquals(23, weather.tempC); assertEquals("Open-Meteo", weather.source)
        assertEquals(1794330000000, weather.observedAt); assertEquals(32.5, weather.lat!!, 0.0001)
        assertEquals(52, weather.humidityPct)
    }
    @Test fun invalidWeatherIsNotPublishedAsZeroDegrees() {
        assertThrows(Exception::class.java) { parseWeather(JSONObject("""{"current":{"weather_code":0}}"""), 0.0, 0.0, 1) }
    }
    @Test fun musicPauseLifecycleDoesNotClearLastTrack() {
        val media = MediaSource(context)
        @Suppress("UNCHECKED_CAST")
        val flow = MediaSource::class.java.getDeclaredField("_state").apply { isAccessible = true }.get(media)
            as kotlinx.coroutines.flow.MutableStateFlow<MediaInfo>
        flow.value = MediaInfo(title = "Track", artist = "Artist", hasSession = true, hasAccess = true, isPlaying = true)
        val before = media.state.value
        media.stop()
        assertEquals(before, media.state.value)
    }
    @Test fun transientMissingSessionHasGracePeriodButEventuallyClears() {
        val media = MediaSource(context)
        @Suppress("UNCHECKED_CAST")
        val flow = MediaSource::class.java.getDeclaredField("_state").apply { isAccessible = true }.get(media)
            as kotlinx.coroutines.flow.MutableStateFlow<MediaInfo>
        flow.value = MediaInfo(title = "Track", hasSession = true)
        MediaSource::class.java.getDeclaredMethod("attach", android.media.session.MediaController::class.java)
            .apply { isAccessible = true }.invoke(media, null)
        assertEquals("Track", media.state.value.title)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2100))
        assertFalse(media.state.value.hasSession)
    }
    @Test fun activityCanOpenPauseAndResumeWithoutDevicePermissions() {
        // This generic device has no Topway service; do not synthesize a null service connection.
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        org.robolectric.Shadows.shadowOf(application).apply {
            declareActionUnbindable(TopwayCarInfo.ACTION)
            // Android does not call onServiceDisconnected when the client unbinds.
            setUnbindServiceCallsOnServiceDisconnected(false)
        }
        val activity = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        activity.pause().stop().restart().start().resume()
        assertFalse(activity.get().isFinishing)
        activity.pause().stop().destroy()
    }
    @Test fun unrelatedCanUpdatesCannotKeepAnOldSpeedAlive() {
        val can = CanState(speedKmh = 70f, updatedAt = 20000, voltageV = 12.4f, voltageAt = 20000, speedAt = 1000)
        assertEquals(SpeedSource.NONE, resolveSpeed(DriveState(), can, 21000).source)
    }
    @Test fun staleGpsDoesNotContinueShowingOldSpeed() {
        val drive = DriveState(speedKmh = 80f, hasFix = true, fixAt = 1000)
        assertEquals(SpeedSource.NONE, resolveSpeed(drive, CanState(), 22000).source)
    }
    @Test fun absentVoltageIsUnknownNotTwelveVolts() { assertNull(CanState().voltageV) }
    @Test fun timedLyricsSupportMultipleStampsFractionsAndOffset() {
        val lines = parseLrc("[offset:100]\n[00:02.5][00:04.050]Test line\n[ar:Artist]")
        assertEquals(listOf(2600L,4150L), lines.map { it.atMs })
        assertTrue(lines.all { it.text == "Test line" })
    }
    @Test fun allUserPreferencesPersist() {
        val prefs = LauncherPreferences(context)
        prefs.setNight(false); prefs.setCamera("usb1"); prefs.setDashboardCamera(true); prefs.setNativeAudio(true); prefs.setWidget("map", 7)
        val restored = LauncherPreferences(context).state.value
        assertFalse(restored.night); assertEquals("usb1", restored.cameraId); assertTrue(restored.dashboardCamera)
        assertTrue(restored.nativeAudio); assertEquals(7, restored.widgets["map"])
    }
    @Test fun missingOrStaleCanNeverInventsParkOrDrive() {
        assertNull(resolveGear(null, CanState(), 0f, 10000L))
        assertNull(resolveGear(null, CanState(), 80f, 10000L))
        assertNull(resolveGear(null, CanState(gear = Gear.D, gearAt = 1000), 80f, 10000L))
        assertEquals(Gear.N, resolveGear(null, CanState(gear = Gear.N, gearAt = 9000), 0f, 10000L))
        assertEquals(Gear.R, resolveGear(Gear.S, CanState(gear = Gear.R, gearAt = 9000), 0f, 10000L))
    }
    @Test fun interfaceScaleSurvivesRestartAndIsBounded() {
        val prefs = LauncherPreferences(context)
        prefs.setUiScale(3f)
        assertEquals(1.15f, LauncherPreferences(context).state.value.uiScale, 0.001f)
        prefs.setUiScale(0f)
        assertEquals(1f, LauncherPreferences(context).state.value.uiScale, 0.001f)
    }
    @Test fun hardwareReportNamesTheSelectedAppsAndDoesNotInventCanReadings() {
        val report = HardwareDiagnostics.report(context, emptyList(), LauncherConfig(), CanState())
        assertTrue(report.contains("CAN marcha: sin lectura vigente"))
        assertTrue(report.contains("STREAM_MUSIC:"))
        assertTrue(report.contains("carplay: no asignada"))
        assertTrue(matchingWidgets(context, null).isEmpty())
    }
}

