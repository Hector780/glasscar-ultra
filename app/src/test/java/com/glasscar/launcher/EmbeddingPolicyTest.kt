package com.glasscar.launcher

import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class EmbeddingPolicyTest {
    private val maps = "com.google.android.apps.maps"
    @Test fun serviceRejectsPrimaryDisplayWithoutLaunchingAnything() {
        val service = EmbeddedAppService()
        assertTrue(service.startApp(0, maps, android.os.Binder()).startsWith("ERROR:"))
        assertEquals("Sin sesión", service.status(0))
    }
    @Test fun cannotTargetPrimaryDisplayOrAnotherAppsDisplay() {
        val name = EmbeddingPolicy.displayName(10001, maps)
        assertFalse(EmbeddingPolicy.allowsDisplay(0, 10001, 10001, name, maps))
        assertFalse(EmbeddingPolicy.allowsDisplay(-1, 10001, 10001, name, maps))
        assertFalse(EmbeddingPolicy.allowsDisplay(4, 10002, 10001, name, maps))
        assertTrue(EmbeddingPolicy.allowsDisplay(4, 10001, 10001, name, maps))
    }
    @Test fun arbitraryPackagesAndWrongDisplayNamesAreRejected() {
        assertFalse(EmbeddingPolicy.allowsDisplay(4, 10001, 10001, "Presentation", maps))
        assertFalse(EmbeddingPolicy.allowsDisplay(4, 10001, 10001,
            EmbeddingPolicy.displayName(10001, "com.other"), "com.other"))
        assertThrows(IllegalArgumentException::class.java) { EmbeddingPolicy.launchCommand(4, "com.other;reboot") }
        assertThrows(IllegalArgumentException::class.java) { EmbeddingPolicy.launchCommand(0, maps) }
    }
    @Test fun commandPreservesExplicitDisplayAndOnlyKnownComponent() {
        val command = EmbeddingPolicy.launchCommand(8, maps)
        assertEquals("8", command[command.indexOf("--display") + 1])
        assertEquals("com.google.android.apps.maps/com.google.android.maps.MapsActivity", command[command.indexOf("-n") + 1])
        assertFalse(command.contains("sh"))
        assertFalse(command.contains("-S")) // no force-stop of an active projection/navigation session
    }
    @Test fun invalidTouchDoesNotReachGuestInput() {
        for ((action, x, expected) in listOf(Triple(MotionEvent.ACTION_DOWN, 12f, true),
            Triple(MotionEvent.ACTION_CANCEL, 12f, true), Triple(MotionEvent.ACTION_HOVER_MOVE, 12f, false),
            Triple(MotionEvent.ACTION_MOVE, Float.NaN, false), Triple(MotionEvent.ACTION_MOVE, Float.POSITIVE_INFINITY, false))) {
            val event = MotionEvent.obtain(1, 2, action, x, 10f, 0)
            try { assertEquals(expected, EmbeddedAppService.validTouch(event)) } finally { event.recycle() }
        }
    }
}

