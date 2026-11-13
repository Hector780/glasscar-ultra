package com.glasscar.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Bars overlay the guest: revealing them never changes its display size. */
@Composable
internal fun ImmersiveControls(volumeEvent: Int, top: @Composable () -> Unit, bottom: @Composable () -> Unit) {
    var upper by remember { mutableStateOf(false) }
    var lower by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    var touching by remember { mutableStateOf(false) }
    var lastVolumeEvent by remember { mutableIntStateOf(volumeEvent) }
    LaunchedEffect(volumeEvent) {
        if (volumeEvent != lastVolumeEvent) { lower = true; interaction++ }
        lastVolumeEvent = volumeEvent
    }
    LaunchedEffect(upper, lower, interaction, touching) {
        if (!touching && (upper || lower)) { delay(4_000); upper = false; lower = false }
    }
    fun Modifier.observeTouches() = pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                touching = event.changes.any { it.pressed }
                interaction++
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        EdgeReveal(true, Modifier.align(Alignment.TopCenter)) { upper = true; interaction++ }
        EdgeReveal(false, Modifier.align(Alignment.BottomCenter)) { lower = true; interaction++ }
        if (upper) Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(52.dp)
            .background(LocalBackdrop.current.palette.abyss).observeTouches()) { top() }
        if (lower) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(64.dp)
            .background(LocalBackdrop.current.palette.abyss).observeTouches()) { bottom() }
    }
}

@Composable
private fun EdgeReveal(top: Boolean, modifier: Modifier, onReveal: () -> Unit) {
    val reveal by rememberUpdatedState(onReveal)
    Box(modifier.fillMaxWidth().height(18.dp)
        .semantics { contentDescription = if (top) "Mostrar pestañas" else "Mostrar volumen y brillo" }
        .pointerInput(top) {
            var distance = 0f
            var revealed = false
            detectVerticalDragGestures(onDragStart = { distance = 0f; revealed = false }) { change, amount ->
                change.consume()
                distance += if (top) amount else -amount
                if (!revealed && distance >= 12.dp.toPx()) { revealed = true; reveal() }
            }
        })
}
