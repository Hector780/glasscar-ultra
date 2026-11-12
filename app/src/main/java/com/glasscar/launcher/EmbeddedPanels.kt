package com.glasscar.launcher

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.pm.PackageManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

@Composable
fun HostedWidget(id: Int, actions: LauncherActions, modifier: Modifier): Boolean {
    val context = LocalContext.current
    val info = remember(id) { AppWidgetManager.getInstance(context).getAppWidgetInfo(id) }
    val host = actions.widgetHost()
    if (info == null || host == null) return false
    key(id) {
        AndroidView(factory = { host.createView(it, id, info).apply { setPadding(0, 0, 0, 0) } },
            modifier = modifier, update = { view ->
                view.post {
                    val density = context.resources.displayMetrics.density
                    val width = (view.width / density).toInt().coerceAtLeast(1)
                    val height = (view.height / density).toInt().coerceAtLeast(1)
                    @Suppress("DEPRECATION")
                    view.updateAppWidgetSize(null, width, height, width, height)
                }
            })
    }
    return true
}

@Composable
fun NavigationPanel(drive: DriveState, config: LauncherConfig, apps: List<AppEntry>, actions: LauncherActions,
    onConfigure: (String) -> Unit, modifier: Modifier) {
    val navigation by MediaListenerService.navigation.collectAsState()
    var follow by remember { mutableStateOf(true) }
    SpatialGlassSurface(modifier, cornerRadius = 26.dp) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(if (BuildConfig.GOOGLE_MAPS_CONFIGURED) "Google Maps · SDK" else "Ubicación · OSM", 18.sp, modifier = Modifier.weight(1f))
                SmallAction(if (follow) "Seguir GPS" else "Centrar") { follow = !follow }
                SmallAction("Abrir") { actions.openSlot("maps", DockShortcuts[0], apps) }
                SmallAction("⋯") { onConfigure("map_panel") }
            }
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp))) {
                val widget = config.widgets["map"]
                val shown = widget != null && HostedWidget(widget, actions, Modifier.fillMaxSize())
                if (!shown) {
                    if (drive.lat == null || drive.lon == null) {
                        Box(Modifier.fillMaxSize().background(LocalBackdrop.current.palette.pillFill), contentAlignment = Alignment.Center) {
                            GlyphIcon(Glyph.Map, Modifier.size(72.dp), LocalBackdrop.current.palette.textSecondary)
                        }
                    } else if (BuildConfig.GOOGLE_MAPS_CONFIGURED) GoogleLiveMap(drive, follow, Modifier.fillMaxSize())
                    else OsmLiveMap(drive, follow, Modifier.fillMaxSize())
                }
                if (!drive.hasFix) {
                    Txt("Esperando ubicación GPS", 14.sp, modifier = Modifier.align(Alignment.TopCenter)
                        .background(LocalBackdrop.current.palette.glassFill).padding(8.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Txt(if (drive.hasFix) "${drive.speedKmh.toInt()} km/h · GPS" else "GPS sin señal", 17.sp,
                    modifier = Modifier.weight(1f))
                SmallAction("Vista") { onConfigure("dashboard") }
            }
            if (navigation.title.isNotBlank() || navigation.instruction.isNotBlank()) {
                Txt(navigation.title, 16.sp)
                Txt(navigation.instruction, 13.sp, color = Ultra.Secondary)
            } else {
                Txt("Inicia una ruta en Google Maps para ver las indicaciones", 12.sp, color = Ultra.Secondary)
            }
        }
    }
}

@Composable
private fun OsmLiveMap(drive: DriveState, follow: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = "com.glasscar.launcher/4.0"
            osmdroidBasePath = File(context.cacheDir, "maps")
            osmdroidTileCache = File(context.cacheDir, "maps/tiles")
            tileFileSystemCacheMaxBytes = 80L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 60L * 1024 * 1024
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            setBuiltInZoomControls(false)
        }
    }
    val marker = remember(map) { Marker(map).apply { title = "Ubicación GPS"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) } }
    var initialized by remember { mutableStateOf(false) }
    Box(modifier) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize(), update = {
            val lat = drive.lat; val lon = drive.lon
            if (lat != null && lon != null) {
                val position = GeoPoint(lat, lon)
                if (follow || !initialized) { it.controller.setCenter(position); initialized = true }
                if (drive.hasFix) {
                    marker.position = position
                    if (!it.overlays.contains(marker)) it.overlays.add(marker)
                } else it.overlays.remove(marker)
                it.invalidate()
            }
        })
        Txt("© OpenStreetMap · mapa de ubicación", 10.sp, color = Color(0xFF17243A),
            modifier = Modifier.align(Alignment.BottomStart).background(Color(0xEFFFFFFF)).padding(4.dp))
    }
    DisposableEffect(map, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) { Lifecycle.Event.ON_RESUME -> map.onResume(); Lifecycle.Event.ON_PAUSE -> map.onPause(); else -> Unit }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) map.onResume()
        onDispose { lifecycle.removeObserver(observer); map.onPause(); map.onDetach() }
    }
}

@Composable
private fun GoogleLiveMap(drive: DriveState, follow: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
    val view = remember { com.google.android.gms.maps.MapView(context).apply { onCreate(null); getMapAsync { googleMap = it } } }
    var marker by remember { mutableStateOf<com.google.android.gms.maps.model.Marker?>(null) }
    var initialized by remember { mutableStateOf(false) }
    AndroidView(factory = { view }, modifier = modifier, update = {
        val lat = drive.lat; val lon = drive.lon
        if (lat != null && lon != null) googleMap?.let { map ->
            val point = LatLng(lat, lon)
            if (follow || !initialized) { map.moveCamera(CameraUpdateFactory.newLatLngZoom(point, 16f)); initialized = true }
            if (drive.hasFix) {
                if (marker == null) marker = map.addMarker(MarkerOptions().position(point).title("Ubicación GPS"))
                else marker?.position = point
            } else { marker?.remove(); marker = null }
        }
    })
    DisposableEffect(view, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart(); Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause(); Lifecycle.Event.ON_STOP -> view.onStop(); else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onResume()
        onDispose { lifecycle.removeObserver(observer); view.onPause(); view.onStop(); view.onDestroy() }
    }
}

@Composable
fun CameraPanel(config: LauncherConfig, apps: List<AppEntry>, actions: LauncherActions, onConfigure: (String) -> Unit,
    permissionVersion: Int, modifier: Modifier) {
    val context = LocalContext.current
    val allowed = remember(permissionVersion) { ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED }
    val cameras = remember(permissionVersion) { availableCameras(context) }
    val cameraId = config.cameraId?.takeIf { it in cameras } ?: cameras.firstOrNull()
    var error by remember(cameraId, permissionVersion) { mutableStateOf<String?>(null) }
    var ready by remember(cameraId, permissionVersion) { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val dvrApp = resolveApp(apps, config, "camera", QuickShortcuts[3])
    val hasWidgets = matchingWidgets(context, dvrApp).isNotEmpty()
    SpatialGlassSurface(modifier, cornerRadius = 26.dp) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("Cámara frontal", 18.sp, modifier = Modifier.weight(1f))
                SmallAction("Abrir DVR") { actions.openSlot("camera", QuickShortcuts[3], apps) }
                SmallAction("⋯") { onConfigure("camera_panel") }
            }
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                val widget = config.widgets["camera"]
                val shown = widget != null && HostedWidget(widget, actions, Modifier.fillMaxSize())
                if (!shown) when {
                    !allowed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Txt("Permite el acceso para ver la cámara aquí", 15.sp)
                        SmallAction("Permitir cámara") { actions.requestCamera() }
                    }
                    cameraId == null || error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Txt(error ?: "El estéreo no expone una cámara a Android", 15.sp)
                        Txt("Revisa la conexión o abre la aplicación DVR", 15.sp, color = Ultra.Secondary)
                        if (error != null) SmallAction("Reintentar") { error = null; ready = false; attempt++ }
                        if (hasWidgets) SmallAction("Elegir widget DVR") { actions.chooseWidget("camera") }
                        SmallAction("Diagnóstico del estéreo") { onConfigure("diagnostics") }
                    }
                    else -> key(cameraId, attempt, permissionVersion) {
                        CameraPreview(cameraId, Modifier.fillMaxSize(), onReady = { ready = true }, onError = { error = it })
                        if (!ready) Txt("Conectando cámara…", 20.sp,
                            modifier = Modifier.background(LocalBackdrop.current.palette.glassFill).padding(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CarPlayPanel(config: LauncherConfig, apps: List<AppEntry>, actions: LauncherActions, onConfigure: (String) -> Unit,
    modifier: Modifier) {
    val app = resolveApp(apps, config, "carplay", QuickShortcuts[6])
    val hasWidgets = matchingWidgets(LocalContext.current, app).isNotEmpty()
    SpatialGlassSurface(modifier, cornerRadius = 30.dp) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("CarPlay · Tlink5", 22.sp, modifier = Modifier.weight(1f))
                SmallAction("Configurar") { onConfigure("carplay_panel") }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val id = config.widgets["carplay"]
                val shown = id != null && HostedWidget(id, actions, Modifier.fillMaxSize())
                if (!shown) Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (app != null) Image(app.icon, app.label, Modifier.size(100.dp))
                    Txt(app?.label ?: "Selecciona Tlink5 o tu app de CarPlay", 24.sp)
                    Txt("Conecta el iPhone desde la aplicación del estéreo", 16.sp, color = Ultra.Secondary)
                    SmallAction("Abrir CarPlay") { actions.openSlot("carplay", QuickShortcuts[6], apps) }
                    if (hasWidgets) SmallAction("Añadir widget de CarPlay") { actions.chooseWidget("carplay") }
                    else {
                        Txt("Esta app no ofrece un widget", 17.sp, color = Ultra.Secondary)
                        SmallAction("Diagnóstico de integración") { onConfigure("diagnostics") }
                    }
                }
            }
        }
    }
}

@Composable
fun SmallAction(label: String, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp).bounceClick(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Txt(label, 15.sp)
    }
}

