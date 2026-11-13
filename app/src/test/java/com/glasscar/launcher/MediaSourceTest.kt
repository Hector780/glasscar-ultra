package com.glasscar.launcher

import android.app.Notification
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Looper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 34])
class MediaSourceTest {
    private lateinit var context: Context
    private lateinit var source: MediaSource
    private val sessions = mutableListOf<MediaSession>()
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners",
            "${context.packageName}/${MediaListenerService::class.java.name}")
        ProjectionMedia.clear()
        source = MediaSource(context)
    }
    @After fun cleanup() { source.stop(); ProjectionMedia.clear(); sessions.forEach { it.release() } }
    private fun metadata(title: String) = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title).build()
    private fun state(playing: Boolean) = PlaybackState.Builder()
        .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, 1200, 1f).build()
    private fun controller(pkg: String, title: String, playing: Boolean): MediaController {
        val session = MediaSession(context, pkg).also { sessions += it }
        return MediaController(context, session.sessionToken).also {
            shadowOf(it).setPackageName(pkg)
            shadowOf(it).setMetadata(metadata(title))
            shadowOf(it).setPlaybackState(state(playing))
            shadowOf(context.getSystemService(MediaSessionManager::class.java)).addController(it)
        }
    }
    @Test fun carPlayStartingAndChangingTrackUpdatesLauncherWithoutSessionListChange() {
        val native = controller("com.apple.android.music", "Anterior", true)
        val carplay = controller(TLINK_PACKAGE, "Del iPhone", false)
        source.start()
        assertEquals("Anterior", source.state.value.title)
        shadowOf(native).setPlaybackState(state(false))
        shadowOf(native).executeOnPlaybackStateChanged(state(false))
        shadowOf(carplay).setPlaybackState(state(true))
        shadowOf(carplay).executeOnPlaybackStateChanged(state(true))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Del iPhone", source.state.value.title)
        shadowOf(carplay).setMetadata(metadata("Siguiente canción"))
        shadowOf(carplay).executeOnMetadataChanged(metadata("Siguiente canción"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Siguiente canción", source.state.value.title)
        assertEquals(TLINK_PACKAGE, source.state.value.appPackage)
        assertTrue(source.state.value.canControl)
        source.stop()
        assertTrue(shadowOf(native).callbacks.isEmpty())
        assertTrue(shadowOf(carplay).callbacks.isEmpty())
    }
    @Test fun repeatedResumeDoesNotDuplicateCallbacksAndDisconnectClearsTrack() {
        val carplay = controller(TLINK_PACKAGE, "Del iPhone", true)
        source.start(); source.start()
        assertEquals(1, shadowOf(carplay).callbacks.size)
        shadowOf(carplay).executeOnSessionDestroyed()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(3))
        assertFalse(source.state.value.hasSession)
    }
    @Test fun connectionNotificationsAreNotMistakenForSongs() {
        assertFalse(ProjectionMedia.accepts(TLINK_PACKAGE, "Conectado", null, false, false))
        assertFalse(ProjectionMedia.accepts(TLINK_PACKAGE, "Tlink5", Notification.CATEGORY_TRANSPORT, false, false))
        assertFalse(ProjectionMedia.accepts("other.app", "Canción", Notification.CATEGORY_TRANSPORT, true, false))
        assertTrue(ProjectionMedia.accepts(TLINK_PACKAGE, "Canción", Notification.CATEGORY_TRANSPORT, false, false))
    }
    @Test fun notificationOnlyTrackHasNoFakeControlsAndDisappearsOnDisconnect() {
        source.start()
        val notification = Notification.Builder(context, "media").setContentTitle("Canción del iPhone")
            .setContentText("Artista").setCategory(Notification.CATEGORY_TRANSPORT).build()
        val posted = android.service.notification.StatusBarNotification(TLINK_PACKAGE, TLINK_PACKAGE,
            1, null, 10001, 0, 0, notification, android.os.Process.myUserHandle(), 1000L)
        ProjectionMedia.post(context, posted)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Canción del iPhone", source.state.value.title)
        assertEquals("Artista", source.state.value.artist)
        assertFalse(source.state.value.canControl)
        assertFalse(source.state.value.isPlaying)
        ProjectionMedia.remove(posted.key)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(3))
        assertFalse(source.state.value.hasSession)
    }
    @Test fun rearPreviewUsesFactoryActivityAndNeverAnEmbeddedDisplay() {
        val intent = RearCameraPolicy.intent()
        assertEquals("com.tw.reverse.ReverseActivity", intent.component?.className)
        assertEquals(1, intent.getIntExtra("iskeypad", 0))
        assertEquals(setOf("iskeypad"), intent.extras!!.keySet())
        assertFalse(EmbeddingPolicy.components.containsKey("com.tw.reverse"))
    }
}
