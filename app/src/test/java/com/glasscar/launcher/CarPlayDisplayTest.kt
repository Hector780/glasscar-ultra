package com.glasscar.launcher

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.os.Looper
import android.view.Surface
import androidx.compose.ui.unit.IntSize
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CarPlayDisplayTest {
    private val tlink = "com.zjinnova.zlink"

    @Test fun offscreenCarPlayStartsAtTheFullHostSizeInPixels() {
        assertEquals(IntSize(1920, 1080),
            embeddedDisplaySize(tlink, IntSize(1680, 810), IntSize(1920, 1080)))
        assertEquals(IntSize(1024, 600),
            embeddedDisplaySize(tlink, IntSize(896, 450), IntSize(1024, 600)))
    }

    @Test fun carPlayWaitsForTheRootMeasurementInsteadOfLockingInTheSmallViewport() {
        assertNull(embeddedDisplaySize(tlink, IntSize(896, 450), IntSize.Zero))
        assertNull(embeddedDisplaySize(tlink, IntSize(896, 450), IntSize(1024, 0)))
    }

    @Test fun mapsAndDvrKeepTheirExistingPanelDimensions() {
        for (target in listOf("com.google.android.apps.maps", "com.williexing.android.apps.xcdvr1")) {
            assertEquals(IntSize(896, 450),
                embeddedDisplaySize(target, IntSize(896, 450), IntSize(1024, 600)))
            assertEquals(IntSize(896, 450),
                embeddedDisplaySize(target, IntSize(896, 450), IntSize.Zero))
        }
    }

    private class RecordingTexture : SurfaceTexture(0) {
        val sizes = mutableListOf<IntSize>()
        override fun setDefaultBufferSize(width: Int, height: Int) {
            sizes.add(IntSize(width, height))
            super.setDefaultBufferSize(width, height)
        }
    }

    @Test fun tabDrawerAndSurfaceCallbacksKeepTheFullBufferAndSameDisplay() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val texture = RecordingTexture()
        val surface = Surface(texture)
        val display = context.getSystemService(DisplayManager::class.java)
            .createVirtualDisplay("carplay-test", 1024, 600, 160, surface, 0)
        val controller = RealAppController(context, tlink)
        // Seed an already connected session without Shizuku or launching a real guest app.
        fun field(name: String) = RealAppController::class.java.getDeclaredField(name).apply { isAccessible = true }
        field("display").set(controller, display)
        field("retainedTexture").set(controller, texture)
        field("bufferWidth").setInt(controller, 1024)
        field("bufferHeight").setInt(controller, 600)
        try {
            for (viewport in listOf(IntSize(1024, 600), IntSize(896, 450), IntSize(1024, 600))) {
                controller.onSurfaceTextureSizeChanged(texture, viewport.width, viewport.height)
            }
            assertFalse(controller.onSurfaceTextureDestroyed(texture))
            controller.onSurfaceTextureAvailable(texture, 896, 450)
            assertEquals(List(4) { IntSize(1024, 600) }, texture.sizes)
            assertSame(display, field("display").get(controller))
            assertSame(texture, field("retainedTexture").get(controller))
            assertFalse(texture.isReleased)
        } finally {
            controller.dispose()
            shadowOf(Looper.getMainLooper()).idle()
            surface.release()
        }
    }
}
