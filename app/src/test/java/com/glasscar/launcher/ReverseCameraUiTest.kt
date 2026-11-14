package com.glasscar.launcher

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 4.7: the launcher follows the real reverse signal; simulated edges only validate navigation, not the vehicle. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1024dp-h600dp-land-mdpi")
class ReverseCameraUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val drawer = mutableStateOf(false)
    private val actions = object : LauncherActions {
        override fun open(app: AppEntry) {}
        override fun open(shortcut: Shortcut, apps: List<AppEntry>) {}
        override fun openPackage(packageName: String) {}
        override fun openNotificationAccess() {}
        override fun setVolume(fraction: Float) {}
        override fun setBrightness(fraction: Float) {}
        override fun playPause() {}
        override fun next() {}
        override fun previous() {}
    }
    @Before fun setup() {
        ReverseSignal.reset()
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("glasscar_v4", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = LauncherPreferences(context)
        val app = AppEntry("Aplicación de prueba", "test.app", "test.app.Main", Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).asImageBitmap())
        compose.setContent {
            LauncherRoot(listOf(app), MutableStateFlow(DriveState()), MutableStateFlow(MediaInfo(hasAccess = true)),
                MutableStateFlow(CanState()), WeatherSource(), drawer.value, { drawer.value = !drawer.value }, { drawer.value = false }, actions, prefs)
        }
    }
    @After fun restore() = ReverseSignal.reset()

    private fun signal(source: ReverseSource, raw: Int, at: Long) {
        compose.runOnIdle { ReverseSignal.report(source, raw, at) }
        compose.waitForIdle()
    }

    @Test fun realReverseOpensCameraOnceAndReturnsWhenTheDriverTouchedNothing() {
        compose.onNodeWithText("Clima exterior").assertIsDisplayed()
        compose.onNodeWithText("Cámara frontal").assertDoesNotExist()
        signal(ReverseSource.TW_STATE, 1, 1_000L)
        compose.onNodeWithText("Cámara frontal").assertIsDisplayed()
        // The exit arrives through the TWService broadcast, as it does while the OEM camera covers the launcher.
        signal(ReverseSource.TW_BROADCAST, 0, 2_000L)
        compose.onNodeWithText("Cámara frontal").assertDoesNotExist()
        compose.onNodeWithText("Clima exterior").assertIsDisplayed()
    }

    @Test fun repeatedReadingsDoNotPullTheDriverBackAndManualNavigationIsKept() {
        signal(ReverseSource.TW_STATE, 1, 1_000L)
        compose.onNodeWithText("Cámara frontal").assertIsDisplayed()
        compose.onNodeWithText("Instrumentos").performClick()
        compose.waitForIdle()
        signal(ReverseSource.TW_STATE, 1, 2_000L)
        signal(ReverseSource.TW_MCU, 1, 2_500L)
        compose.onNodeWithText("SIN LECTURA DE MARCHA CAN").assertIsDisplayed()
        signal(ReverseSource.TW_STATE, 0, 3_000L)
        compose.onNodeWithText("SIN LECTURA DE MARCHA CAN").assertIsDisplayed()
        compose.onNodeWithText("Cámara frontal").assertDoesNotExist()
    }

    @Test fun invalidOrMissingReadingsNeverNavigate() {
        signal(ReverseSource.TW_STATE, -1, 1_000L)
        signal(ReverseSource.TW_STATE, 0, 2_000L)
        signal(ReverseSource.TW_BROADCAST, 7, 3_000L)
        compose.onNodeWithText("Clima exterior").assertIsDisplayed()
        compose.onNodeWithText("Cámara frontal").assertDoesNotExist()
    }
}
