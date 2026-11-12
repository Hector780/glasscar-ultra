package com.glasscar.launcher

import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun weatherTime(time: Long): String = if (time > 0) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time)) else "sin lectura"

@Composable
fun LauncherSheet(sheet: String, apps: List<AppEntry>, config: LauncherConfig, preferences: LauncherPreferences,
    drive: DriveState, weather: WeatherInfo, status: DeviceStatus, can: CanState, actions: LauncherActions,
    onDismiss: () -> Unit, onChoose: (String) -> Unit, onRefreshWeather: () -> Unit) {
    val context = LocalContext.current
    val menus = setOf("settings", "status", "weather_detail", "map_panel", "camera_panel", "carplay_panel", "dashboard", "diagnostics")
    val titles = mapOf("settings" to "Personalizar tablero", "status" to "Conexiones y vehículo",
        "weather_detail" to "Clima exterior", "map_panel" to "Panel del mapa", "camera_panel" to "Cámara frontal",
        "carplay_panel" to "CarPlay / Tlink5", "dashboard" to "Vista principal",
        "diagnostics" to "Diagnóstico del estéreo",
        "music" to "Aplicación de música", "maps" to "Aplicación de mapas", "camera" to "Aplicación DVR",
        "phone" to "Aplicación de teléfono", "carplay" to "Aplicación de CarPlay", "weather" to "Aplicación de clima")
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SpaceBox(Modifier.fillMaxWidth(0.88f).fillMaxHeight(0.88f), LocalBackdrop.current.palette) {
            SpatialGlassSurface(Modifier.fillMaxSize(), cornerRadius = 28.dp) {
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Txt(titles[sheet] ?: "Elegir aplicación", 23.sp, FontWeight.Bold, modifier = Modifier.weight(1f))
                        SmallAction("Cerrar", onDismiss)
                    }
                    if (sheet !in menus) {
                        AppPicker(sheet, apps, config, preferences, onDismiss, Modifier.weight(1f))
                    } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (sheet) {
                            "settings" -> {
                                BodyText("Mantén presionado un acceso para cambiar su aplicación. La barra lateral muestra tus apps recientes.")
                                listOf("maps" to "Mapas", "music" to "Música", "phone" to "Teléfono", "camera" to "Cámara DVR",
                                    "carplay" to "CarPlay / Tlink5", "weather" to "Clima", "obd" to "Telemetría").forEach { (slot, label) ->
                                    SettingRow(label, configuredLabel(slot, config, apps)) { onChoose(slot) }
                                }
                                SettingRow("Tamaño de interfaz", if (config.uiScale > 1f) "Grande" else "Adaptado a pantalla") {
                                    preferences.setUiScale(if (config.uiScale > 1f) 1f else 1.15f)
                                }
                                SettingRow("Diagnóstico del estéreo", "Modelo, audio, cámara y CAN") { onChoose("diagnostics") }
                                SettingRow("Control de volumen", if (config.nativeAudio) "Panel real del estéreo" else "Volumen Android") {
                                    preferences.setNativeAudio(!config.nativeAudio)
                                }
                                BodyText("Si el volumen Android es independiente del estéreo, elige su panel real. Así no se muestra una segunda barra como si fuera el volumen del vehículo.")
                                SettingRow("Música e indicaciones de Maps", "Permisos de notificaciones") { actions.openNotificationAccess() }
                                SettingRow("Recientes de todo Android", "Permiso opcional de uso de aplicaciones") { actions.openUsageAccess() }
                                SettingRow("Conexiones y voltaje", "Ver estado") { onChoose("status") }
                            }
                            "diagnostics" -> {
                                var report by remember { mutableStateOf("") }
                                LaunchedEffect(apps, config, can) {
                                    while (true) {
                                        report = HardwareDiagnostics.report(context, apps, config, can)
                                        kotlinx.coroutines.delay(1000)
                                    }
                                }
                                BodyText("Con el auto estacionado, usa los botones de volumen del volante y observa si cambia STREAM_MUSIC. El reporte permite identificar el equipo y preparar su integración.")
                                SmallAction("Copiar diagnóstico") {
                                    context.getSystemService(android.content.ClipboardManager::class.java)
                                        .setPrimaryClip(android.content.ClipData.newPlainText("GlassCar diagnóstico", report))
                                    android.widget.Toast.makeText(context, "Diagnóstico copiado", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                BodyText(report)
                            }
                            "status" -> {
                                BodyText("Wi-Fi: ${if (status.wifi) "conectado" else "sin conexión Wi-Fi"}")
                                BodyText("Bluetooth: ${if (!status.bluetoothKnown) "falta permiso para consultar" else if (status.bluetooth) "encendido; no confirma conexión al teléfono" else "apagado"}")
                                SmallAction("Permitir / abrir Bluetooth") { actions.requestBluetooth() }
                                BodyText("GPS: ${if (drive.hasFix) "ubicación vigente" else if (status.gps) "encendido, esperando señal" else "desactivado"}")
                                drive.accuracyM?.takeIf { drive.hasFix }?.let { BodyText("Precisión aproximada: ${it.toInt()} m") }
                                BodyText("Rumbo: ${drive.bearing?.let { "${it.toInt()}° · movimiento GPS" } ?: "sin dato; no se inventa una dirección"}")
                                BodyText("Altitud GPS: ${drive.altitude?.let { "${it.toInt()} m" } ?: "sin dato"}")
                                val voltage = can.voltageV?.takeIf { can.voltageAt > 0 && android.os.SystemClock.elapsedRealtime() - can.voltageAt < 5000 }
                                BodyText("Voltaje del vehículo: ${voltage?.let { String.format(Locale.US, "%.1f V · recibido de CAN", it) } ?: "no disponible; el estéreo no ha enviado una lectura vigente"}")
                                status.batteryPct?.let { BodyText("Batería del dispositivo Android: $it% (no es la batería del vehículo)") }
                                SmallAction("Abrir controles del estéreo") { actions.openNativeControls() }
                            }
                            "weather_detail" -> {
                                BodyText("${weather.tempC?.let { "$it °C" } ?: "Sin temperatura"} · ${weather.description}")
                                BodyText("Fuente: ${weather.source}. Estimación meteorológica exterior; no es un sensor del automóvil.")
                                BodyText("Dato correspondiente a ${weatherTime(weather.observedAt)} · consulta ${weatherTime(weather.fetchedAt)}")
                                if (weather.fetchedAt > 0 && System.currentTimeMillis() - weather.fetchedAt > 30 * 60_000L) BodyText("Lectura antigua: actualiza cuando tengas conexión.")
                                weather.error?.let { BodyText(it) }
                                if (weather.lat != null && weather.lon != null) BodyText(String.format(Locale.US, "Ubicación consultada: %.4f, %.4f", weather.lat, weather.lon))
                                BodyText("Viento: ${weather.windKmh?.let { "$it km/h" } ?: "sin dato"} · Humedad: ${weather.humidityPct?.let { "$it%" } ?: "sin dato"}")
                                SmallAction("Actualizar ahora", onRefreshWeather)
                                SmallAction("Elegir app de clima") { onChoose("weather") }
                                SmallAction("Abrir app de clima") { actions.openSlot("weather", WeatherShortcut, apps) }
                                SmallAction("Buscar app de clima en Play Store") { actions.findWeatherApp() }
                            }
                            "dashboard" -> {
                                SettingRow("Panel principal", if (config.dashboardCamera) "Cámara frontal" else "Mapa en vivo") {
                                    preferences.setDashboardCamera(!config.dashboardCamera)
                                }
                                SettingRow("Mapa", "Personalizar panel") { onChoose("map_panel") }
                                SettingRow("Cámara", "Personalizar panel") { onChoose("camera_panel") }
                            }
                            "map_panel" -> {
                                BodyText(if (BuildConfig.GOOGLE_MAPS_CONFIGURED) "Mapa integrado: Google Maps." else "Mapa de ubicación: OpenStreetMap. Las indicaciones de tu ruta se leen de Google Maps cuando habilitas el acceso a notificaciones.")
                                SettingRow("Aplicación de navegación", configuredLabel("maps", config, apps)) { onChoose("maps") }
                                SmallAction("Permitir indicaciones de Google Maps") { actions.openNotificationAccess() }
                                if (matchingWidgets(context, resolveApp(apps, config, "maps", DockShortcuts[0])).isNotEmpty())
                                    SmallAction("Usar widget de la app seleccionada") { actions.chooseWidget("map"); onDismiss() }
                                if (config.widgets["map"] != null) SmallAction("Volver al mapa integrado") { actions.removeWidget("map") }
                            }
                            "camera_panel" -> {
                                SettingRow("Aplicación DVR", configuredLabel("camera", config, apps)) { onChoose("camera") }
                                SmallAction("Permitir acceso a cámara") { actions.requestCamera() }
                                val cameras = availableCameras(context)
                                if (cameras.isEmpty()) BodyText("La cámara DVR no está expuesta a Android. Revisa el diagnóstico para identificar la integración del fabricante.")
                                cameras.forEach { id -> SettingRow("Cámara Android $id", if (config.cameraId == id) "Seleccionada" else "Seleccionar") { preferences.setCamera(id); actions.removeWidget("camera") } }
                                if (matchingWidgets(context, resolveApp(apps, config, "camera", QuickShortcuts[3])).isNotEmpty())
                                    SmallAction("Usar widget de DVR") { actions.chooseWidget("camera"); onDismiss() }
                                SmallAction("Diagnóstico de cámara") { onChoose("diagnostics") }
                                if (config.widgets["camera"] != null) SmallAction("Quitar widget") { actions.removeWidget("camera") }
                            }
                            "carplay_panel" -> {
                                SettingRow("Aplicación", configuredLabel("carplay", config, apps)) { onChoose("carplay") }
                                BodyText("La aplicación se abre al pasar a esta página. La integración dentro del tablero todavía requiere comprobar las capacidades del estéreo.")
                                if (matchingWidgets(context, resolveApp(apps, config, "carplay", QuickShortcuts[6])).isNotEmpty())
                                    SmallAction("Elegir widget de CarPlay") { actions.chooseWidget("carplay"); onDismiss() }
                                SmallAction("Diagnóstico de integración") { onChoose("diagnostics") }
                                if (config.widgets["carplay"] != null) SmallAction("Quitar widget") { actions.removeWidget("carplay") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun configuredLabel(slot: String, config: LauncherConfig, apps: List<AppEntry>): String =
    config.assignments[slot]?.let { key -> apps.firstOrNull { it.componentKey == key }?.label ?: "App no disponible" } ?: "Automática · cambiar"

@Composable
private fun AppPicker(slot: String, apps: List<AppEntry>, config: LauncherConfig, preferences: LauncherPreferences,
    onDismiss: () -> Unit, modifier: Modifier) {
    var query by remember { mutableStateOf("") }
    Column(modifier) {
        SmallAction("Restablecer selección automática") { preferences.assign(slot, null); onDismiss() }
        BasicTextField(query, { query = it }, Modifier.fillMaxWidth().heightIn(min = 46.dp)
            .background(LocalBackdrop.current.palette.pillFill, RoundedCornerShape(14.dp)).padding(12.dp),
            textStyle = TextStyle(color = LocalBackdrop.current.palette.textPrimary, fontSize = 17.sp), singleLine = true,
            decorationBox = { field -> if (query.isBlank()) Txt("Buscar aplicación…", 17.sp, color = Ultra.Secondary); field() })
        LazyColumn(Modifier.weight(1f)) {
            items(apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }, key = { it.componentKey }) { app ->
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).bounceClick { preferences.assign(slot, app); onDismiss() }
                    .padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AppIcon(app, Modifier.size(48.dp))
                    Txt(app.label + if (config.assignments[slot] == app.componentKey) " ✓" else "", 17.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).bounceClick(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Txt(label, 17.sp, modifier = Modifier.weight(1f))
        Txt("$value ›", 14.sp, color = Ultra.Secondary, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun BodyText(text: String) {
    BasicText(text, style = TextStyle(color = LocalBackdrop.current.palette.textPrimary, fontSize = 16.sp, lineHeight = 22.sp))
}

@Composable
fun LyricsContent(media: MediaInfo, position: Long, modifier: Modifier) {
    var lyrics by remember(media.title, media.artist, media.durationMs) { mutableStateOf<Lyrics?>(null) }
    var plainPage by remember(media.title, media.artist) { mutableIntStateOf(0) }
    LaunchedEffect(media.title, media.artist, media.durationMs) { lyrics = LyricsSource.load(media) }
    val current = lyrics
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            current == null -> BodyText("Buscando letra…")
            current.message.isNotBlank() -> BodyText(current.message)
            current.lines.isNotEmpty() -> {
                val active = current.lines.indexOfLast { it.atMs <= position }
                AnimatedContent(active, modifier = Modifier.weight(1f).fillMaxWidth(), label = "lyric-line") { index ->
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        current.lines.getOrNull(index - 1)?.let { LyricText(it.text, false) }
                        if (index < 0) LyricText("♪", true)
                        else current.lines.getOrNull(index)?.let { LyricText(it.text.ifBlank { "♪" }, true) }
                        current.lines.getOrNull(index + 1)?.let { LyricText(it.text, false) }
                    }
                }
            }
            current.plain.isNotBlank() -> {
                val pages = current.plain.lineSequence().filter(String::isNotBlank).toList().chunked(2)
                pages.getOrNull(plainPage)?.forEach { LyricText(it, true) }
                if (pages.size > 1) SmallAction("Sin sincronización · siguiente") { plainPage = (plainPage + 1) % pages.size }
            }
            else -> BodyText("No hay letra disponible para esta canción")
        }
    }
}

@Composable
private fun LyricText(text: String, active: Boolean) {
    BasicText(text, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        style = TextStyle(fontSize = if (active) 26.sp else 16.sp, lineHeight = if (active) 30.sp else 19.sp,
            textAlign = TextAlign.Center, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) LocalBackdrop.current.palette.textPrimary else LocalBackdrop.current.palette.textSecondary),
        maxLines = if (active) 2 else 1, overflow = TextOverflow.Ellipsis)
}
