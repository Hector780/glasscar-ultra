package com.glasscar.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
internal fun AppDrawer(apps: List<AppEntry>, onUninstall: (AppEntry) -> Unit, onOpen: (AppEntry) -> Unit) {
    var selected by remember { mutableStateOf<AppEntry?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var moved by remember { mutableStateOf(false) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var trash by remember { mutableStateOf(Rect.Zero) }
    val uninstall by rememberUpdatedState(onUninstall)
    val open by rememberUpdatedState(onOpen)
    val overTrash = dragging && trash.contains(finger)
    fun requestUninstall() { selected?.let { selected = null; dragging = false; uninstall(it) } }
    BackHandler(selected != null) { selected = null; dragging = false }
    LaunchedEffect(apps) { if (selected != null && apps.none { it.componentKey == selected?.componentKey }) selected = null }

    SpatialGlassSurface(Modifier.fillMaxSize(), cornerRadius = 32.dp) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Txt(selected?.label.orEmpty(), 16.sp,
                        modifier = Modifier.weight(1f))
                    if (selected != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SmallAction("Cancelar") { selected = null; dragging = false }
                            Box(Modifier.width(200.dp).height(52.dp)
                                .onGloballyPositioned { trash = it.boundsInRoot() }
                                .background(if (overTrash) Color(0xFFB52D42) else Color(0x554C2630), RoundedCornerShape(16.dp))
                                .semantics { contentDescription = "Papelera: desinstalar aplicación" }
                                .clickable { requestUninstall() }, contentAlignment = Alignment.Center) {
                                Txt(if (overTrash) "Soltar para desinstalar" else "Papelera · Desinstalar", 15.sp)
                            }
                        }
                    }
                }
                if (apps.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Txt("No hay aplicaciones", 16.sp) }
                else LazyVerticalGrid(columns = GridCells.Adaptive(120.dp), modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(apps, key = { it.componentKey }) { app ->
                        var itemOrigin by remember { mutableStateOf(Offset.Zero) }
                        Column(Modifier.onGloballyPositioned { itemOrigin = it.positionInRoot() }
                            .semantics { onLongClick("Desinstalar ${app.label}") { selected = app; true } }
                            .pointerInput(app.componentKey) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { selected = app; finger = itemOrigin + it; dragging = true; moved = false },
                                    onDrag = { change, amount ->
                                        change.consume(); finger += amount
                                        if (amount.getDistance() > 0f) moved = true
                                    },
                                    onDragEnd = {
                                        if (trash.contains(finger)) requestUninstall()
                                        else if (moved) selected = null
                                        dragging = false
                                    },
                                    onDragCancel = { if (moved) selected = null; dragging = false }
                                )
                            }.clickable { if (selected != null) selected = app else open(app) },
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(88.dp).spatialGlass(RoundedCornerShape(24)), contentAlignment = Alignment.Center) {
                                val icon = (if (LocalBackdrop.current.palette.night) app.iconDark else app.iconLight) ?: app.icon
                                Image(icon, app.label, Modifier.size(64.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Txt(app.label, 14.sp)
                        }
                    }
                }
            }
            if (dragging) selected?.let { app ->
                Image(app.icon, null, Modifier.offset { IntOffset((finger.x - origin.x).roundToInt(),
                    (finger.y - origin.y).roundToInt()) }.size(48.dp))
            }
        }
    }
}
