package com.glasscar.launcher

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
class EmbeddedPanelUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun connectedGuestUsesEntirePanelWithoutDebugChrome() {
        compose.setContent {
            EmbeddedPanelFrame("Tlink5", EmbeddedPanelStatus("OK: App en el panel", running = true),
                Modifier.fillMaxSize().testTag("panel"), {}) {
                Box(Modifier.fillMaxSize().background(Color.Black).testTag("guest"))
            }
        }
        assertEquals(compose.onNodeWithTag("panel").fetchSemanticsNode().boundsInRoot,
            compose.onNodeWithTag("guest").fetchSemanticsNode().boundsInRoot)
        for (text in listOf("Iniciar aquí", "Atrás", "Cerrar", "Comprobar interacción", "OK: App en el panel", "Tlink5")) {
            compose.onNodeWithText(text).assertDoesNotExist()
        }
    }

    @Test fun connectionHelpDisappearsAfterRetrySucceeds() {
        val state = mutableStateOf(EmbeddedPanelStatus("Autoriza GlassCar en Shizuku", retry = true))
        compose.setContent {
            EmbeddedPanelFrame("HD Car DVR", state.value, Modifier.fillMaxSize(),
                { state.value = EmbeddedPanelStatus(running = true) }) { Box(Modifier.fillMaxSize()) }
        }
        compose.onNodeWithText("Autoriza GlassCar en Shizuku").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()
        compose.onNodeWithText("Reintentar").assertDoesNotExist()
        compose.onNodeWithText("Autoriza GlassCar en Shizuku").assertDoesNotExist()
    }

    @Test fun cameraSelectorFloatsOverFullSizeVideoWithoutASeparateRow() {
        val source = mutableStateOf(CameraSource.FRONT)
        compose.setContent {
            EmbeddedCameraPanel(source.value, { source.value = it }, false, Modifier.fillMaxSize().testTag("panel")) {
                Box(Modifier.fillMaxSize().background(Color.Black).testTag("video"))
            }
        }
        val panel = compose.onNodeWithTag("panel").fetchSemanticsNode().boundsInRoot
        assertEquals(panel, compose.onNodeWithTag("video").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Cámara trasera · original").assertDoesNotExist()
        compose.onNodeWithContentDescription("Estado de la cámara trasera").assertDoesNotExist()
        compose.onNodeWithContentDescription("Cámara frontal").assertIsSelected()
        val rear = compose.onNodeWithContentDescription("Cámara trasera").fetchSemanticsNode().boundsInRoot
        assertTrue("touch target ${rear.height}", rear.height >= 48f && rear.width >= 48f)
        assertTrue("capsule stays inside the video", rear.top >= panel.top && rear.right <= panel.right)
        compose.onNodeWithContentDescription("Cámara trasera").performClick()
        assertEquals(CameraSource.REAR, source.value)
        compose.onNodeWithContentDescription("Cámara trasera").assertIsSelected()
        compose.onNodeWithContentDescription("Estado de la cámara trasera").assertIsDisplayed()
        compose.onNodeWithText("Sin imagen fuera de reversa", substring = true).assertIsDisplayed()
        // The DVR stays composed at the same size underneath: its session and recording are not torn down.
        assertEquals(panel, compose.onNodeWithTag("video").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("Cámara frontal").performClick()
        assertEquals(CameraSource.FRONT, source.value)
        compose.onNodeWithContentDescription("Estado de la cámara trasera").assertDoesNotExist()
    }

    @Test fun rearStateNamesRealReverseAndSelectorHidesWithoutACallback() {
        compose.setContent {
            EmbeddedCameraPanel(CameraSource.REAR, null, true, Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) }
        }
        compose.onNodeWithText("Reversa detectada", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Sin imagen fuera de reversa", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Cámara frontal").assertDoesNotExist()
        compose.onNodeWithContentDescription("Cámara trasera").assertDoesNotExist()
    }

    @Test fun mapExpansionKeepsFullGuestAreaAndOffersReturn() {
        val expanded = mutableStateOf(false)
        compose.setContent {
            EmbeddedPanelFrame("Google Maps", EmbeddedPanelStatus(running = true),
                Modifier.fillMaxSize().testTag("panel"), {}, expanded.value, { expanded.value = !expanded.value }) {
                Box(Modifier.fillMaxSize().testTag("guest"))
            }
        }
        compose.onNodeWithContentDescription("Ampliar mapa").performClick()
        compose.onNodeWithContentDescription("Reducir mapa").assertIsDisplayed()
        assertEquals(compose.onNodeWithTag("panel").fetchSemanticsNode().boundsInRoot,
            compose.onNodeWithTag("guest").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("Reducir mapa").performClick()
        compose.onNodeWithContentDescription("Ampliar mapa").assertIsDisplayed()
    }
}
