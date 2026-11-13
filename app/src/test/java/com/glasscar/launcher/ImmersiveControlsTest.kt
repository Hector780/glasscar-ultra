package com.glasscar.launcher

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1024dp-h600dp-land-mdpi")
class ImmersiveControlsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val event = mutableIntStateOf(0)
    private fun show() {
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().testTag("guest"))
                ImmersiveControls(event.intValue,
                    top = { Txt("Pestañas", 18.sp) }, bottom = { Txt("Volumen y brillo", 18.sp) })
            }
        }
    }
    @Test fun edgesRevealOverlaysWithoutResizingGuestAndHideAfterIdle() {
        show()
        compose.onNodeWithText("Pestañas").assertDoesNotExist()
        compose.onNodeWithText("Volumen y brillo").assertDoesNotExist()
        val before = compose.onNodeWithTag("guest").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Mostrar pestañas").performTouchInput {
            swipe(center, center + Offset(0f, 90f), 250)
        }
        compose.onNodeWithText("Pestañas").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mostrar volumen y brillo").performTouchInput {
            swipe(center, center - Offset(0f, 90f), 250)
        }
        compose.onNodeWithText("Volumen y brillo").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithTag("guest").fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(4_500)
        compose.onNodeWithText("Pestañas").assertDoesNotExist()
        compose.onNodeWithText("Volumen y brillo").assertDoesNotExist()
    }
    @Test fun wheelVolumeRevealsBothControlsAndRefreshesTimeout() {
        show()
        compose.runOnIdle { event.intValue++ }
        compose.onNodeWithText("Volumen y brillo").assertIsDisplayed()
        compose.onNodeWithText("Pestañas").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(3_000)
        compose.runOnIdle { event.intValue++ }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("Volumen y brillo").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2_500)
        compose.onNodeWithText("Volumen y brillo").assertDoesNotExist()
    }
}
