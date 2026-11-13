package com.glasscar.launcher

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1024dp-h600dp-land-mdpi")
class AppDrawerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app = AppEntry("App de prueba", "test.app", "test.app.Main",
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).asImageBitmap())
    private var opened = 0
    private var removed = 0
    private fun show() { compose.setContent { AppDrawer(listOf(app), { removed++ }, { opened++ }) } }
    @Test fun tapOpensAndLongPressOnlySelectsUntilTrashIsConfirmed() {
        show()
        compose.onNodeWithContentDescription(app.label).performClick()
        assertEquals(1, opened)
        compose.onNodeWithContentDescription(app.label).performTouchInput { longClick() }
        assertEquals(0, removed)
        assertEquals(1, opened)
        compose.onNodeWithContentDescription("Papelera: desinstalar aplicación").performClick()
        assertEquals(1, removed)
    }
    @Test fun longDragIntoTrashRequestsUninstallOnce() {
        show()
        val icon = compose.onNodeWithContentDescription(app.label)
        val start = icon.fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(start); advanceEventTime(700); moveTo(start) }
        compose.waitForIdle()
        val end = compose.onNodeWithContentDescription("Papelera: desinstalar aplicación").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { moveTo(end, 300); up() }
        assertEquals(1, removed)
        assertEquals(0, opened)
    }
    @Test fun droppingOutsideTrashDoesNotDeleteOrOpen() {
        show()
        val start = compose.onNodeWithContentDescription(app.label).fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(start); advanceEventTime(700); moveBy(androidx.compose.ui.geometry.Offset(30f, 100f)); up() }
        assertEquals(0, removed)
        assertEquals(0, opened)
    }
}
