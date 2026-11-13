package com.glasscar.launcher

import android.os.SystemClock
import android.appwidget.AppWidgetHost
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Acciones que la interfaz le pide a la Activity. */
interface LauncherActions {
    fun open(app: AppEntry)
    fun open(shortcut: Shortcut, apps: List<AppEntry>)
    fun openSlot(slot: String, shortcut: Shortcut, apps: List<AppEntry>) { open(shortcut, apps) }
    fun openNativeControls() {}
    fun requestCamera() {}
    fun requestBluetooth() {}
    fun openUsageAccess() {}
    fun findWeatherApp() {}
    fun chooseWidget(slot: String) {}
    fun removeWidget(slot: String) {}
    fun widgetHost(): AppWidgetHost? = null
    fun openPackage(packageName: String)
    fun openNotificationAccess()
    fun setVolume(fraction: Float)
    fun setBrightness(fraction: Float)
    fun playPause()
    fun next()
    fun previous()
}

/** Acento del cluster: azul normal, ámbar en Reversa, rojo neón en Sport. */
private data class Accent(val start: Color, val end: Color)
private val AccentNormal = Accent(Ultra.Electric, Ultra.Cyan)
private val AccentSport = Accent(Ultra.Magenta, Color(0xFFFF6482))
private val AccentReverse = Accent(Ultra.Amber, Color(0xFFFF453A))

// =====================================================================
//  Raíz
// =====================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherRoot(
    apps: List<AppEntry>,
    driveFlow: StateFlow<DriveState>,
    mediaFlow: StateFlow<MediaInfo>,
    canFlow: StateFlow<CanState>,
    weatherSource: WeatherSource,
    showDrawer: Boolean,
    onToggleDrawer: () -> Unit,
    onCloseDrawer: () -> Unit,
    actions: LauncherActions,
    preferences: LauncherPreferences? = null,
    permissionVersion: Int = 0
) {
    val context = LocalContext.current
    val prefs = preferences ?: remember { LauncherPreferences(context) }
    val config by prefs.state.collectAsState()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Some head units report mdpi even on a 1920px panel. Keep a usable cockpit scale.
        val fit = min(maxWidth.value / 1024f, maxHeight.value / 600f).coerceAtLeast(1f)
        val scale = fit * config.uiScale
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            LauncherContent(apps, driveFlow, mediaFlow, canFlow, weatherSource, showDrawer,
                onToggleDrawer, onCloseDrawer, actions, prefs, permissionVersion)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherContent(
    apps: List<AppEntry>,
    driveFlow: StateFlow<DriveState>,
    mediaFlow: StateFlow<MediaInfo>,
    canFlow: StateFlow<CanState>,
    weatherSource: WeatherSource,
    showDrawer: Boolean,
    onToggleDrawer: () -> Unit,
    onCloseDrawer: () -> Unit,
    actions: LauncherActions,
    preferences: LauncherPreferences? = null,
    permissionVersion: Int = 0
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val prefs = preferences ?: remember { LauncherPreferences(context) }
    val config by prefs.state.collectAsState()
    val scope = rememberCoroutineScope()
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val configure: (String) -> Unit = { sheet = it }
    val pager = rememberPagerState(pageCount = { 4 })
    var mapExpanded by rememberSaveable { mutableStateOf(false) }
    val immersive = !showDrawer && (pager.currentPage == 1 ||
        (pager.currentPage == 0 && mapExpanded && !config.dashboardCamera))
    var carPlayOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(pager.settledPage, apps.isNotEmpty(), config.assignments["carplay"], config.widgets["carplay"]) {
        if (pager.settledPage != 1) carPlayOpened = false
        else if (!BuildConfig.REAL_APP_EMBEDDING && !carPlayOpened && config.widgets["carplay"] == null) {
            if (resolveApp(apps, config, "carplay", QuickShortcuts[6]) != null) {
                carPlayOpened = true
                actions.openSlot("carplay", QuickShortcuts[6], apps)
            }
        }
    }
    val realDrive by driveFlow.collectAsState()
    val realMedia by mediaFlow.collectAsState()
    val can by canFlow.collectAsState()
    val realWeather by weatherSource.state.collectAsState()

    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val demoStart = remember { SystemClock.elapsedRealtime() }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = SystemClock.elapsedRealtime(); delay(if (DEMO_MODE) 250L else 500L) }
        }
    }
    val drive = if (DEMO_MODE) Demo.drive(now) else realDrive.copy(hasFix = realDrive.isFresh(now))
    val media = if (DEMO_MODE) Demo.media(demoStart) else realMedia
    val weather = if (DEMO_MODE) Demo.weather else realWeather

    // ---------- Velocidad y marcha (lógica híbrida GPS + RP5-FD-002) ----------
    val speed = resolveSpeed(drive, can, now)
    var manualGear by rememberSaveable { mutableStateOf<Gear?>(null) }
    val moving = speed.kmh >= 1.5f
    LaunchedEffect(moving) {
        if (manualGear != null && manualGear != Gear.S) manualGear = null
    }
    val gear = resolveGear(manualGear, can, speed.kmh, now)

    // ---------- Clima ----------
    val currentDrive by rememberUpdatedState(drive)
    LaunchedEffect(lifecycle, drive.lat != null && drive.lon != null) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (!DEMO_MODE) {
                val latest = currentDrive
                if (latest.lat != null && latest.lon != null) weatherSource.refresh(latest.lat, latest.lon)
                delay(60_000L)
            }
        }
    }

    // ---------- Estado del dispositivo ----------
    var status by remember { mutableStateOf(if (DEMO_MODE) Demo.status else DeviceStatus()) }
    if (!DEMO_MODE) {
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    status = withContext(Dispatchers.Default) { readDeviceStatus(context) }
                    delay(1000L)
                }
            }
        }
    }

    val night = config.night
    // Si el módulo CANbus RP5-FD-002 reporta cambio en las luces (ILL) de la Escape, ajusta automático
    LaunchedEffect(can.lightsOn) {
        can.lightsOn?.let { prefs.setNight(it) }
    }

    val palette = animatedPalette(night)
    var rootSize by remember { mutableStateOf(Size.Zero) }


    BackHandler(enabled = sheet != null || showDrawer || mapExpanded || pager.currentPage != 0) {
        when {
            sheet != null -> sheet = null
            showDrawer -> onCloseDrawer()
            mapExpanded -> mapExpanded = false
            else -> scope.launch { pager.scrollToPage(0) }
        }
    }
    CompositionLocalProvider(LocalBackdrop provides Backdrop(rootSize, palette)) {
        Box(Modifier.fillMaxSize().onSizeChanged { rootSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .drawBehind { drawSpaceBackground(palette, size) }
            .pointerInput(showDrawer, immersive) {
                if (!showDrawer && !immersive && !BuildConfig.REAL_APP_EMBEDDING) {
                    var distance = 0f
                    detectVerticalDragGestures(onDragStart = { distance = 0f }, onDragEnd = {
                        if (kotlin.math.abs(distance) > 64.dp.toPx()) onToggleDrawer()
                    }) { _, amount -> distance += amount }
                }
            }) {
            Row(Modifier.fillMaxSize().padding(if (immersive) 4.dp else 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!immersive) SpatialDock(status, drive.hasFix, apps, config, actions, onToggleDrawer, configure,
                    Modifier.width(92.dp).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(if (immersive) 2.dp else 10.dp)) {
                    Row(Modifier.fillMaxWidth().height(if (immersive) 44.dp else 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf("Tablero", "CarPlay", "Cámara", "Instrumentos").forEachIndexed { index, name ->
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                                .background(if (!showDrawer && pager.currentPage == index) palette.pillFill else Color.Transparent)
                                .bounceClick { onCloseDrawer(); mapExpanded = false; scope.launch { pager.scrollToPage(index) } }
                                .padding(vertical = 10.dp), contentAlignment = Alignment.Center) { Txt(name, 17.sp, FontWeight.SemiBold) }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (showDrawer) AppDrawer(apps) { actions.open(it); onCloseDrawer() }
                        else HorizontalPager(pager, Modifier.fillMaxSize(), beyondBoundsPageCount = 0,
                            userScrollEnabled = !BuildConfig.REAL_APP_EMBEDDING) { page ->
                            when (page) {
                                0 -> if (pager.settledPage == 0) Dashboard(drive, speed, gear, manualGear != null, { manualGear = it }, { manualGear = null },
                                    media, weather, apps, status, actions, config, configure, can, permissionVersion,
                                    mapExpanded, { mapExpanded = !mapExpanded })
                                1 -> if (pager.settledPage == 1) CarPlayPanel(config, apps, actions, configure, Modifier.fillMaxSize())
                                2 -> if (pager.settledPage == 2) CameraPanel(config, apps, actions, configure, permissionVersion, Modifier.fillMaxSize())
                                3 -> ClusterCard(drive, speed, gear, manualGear != null, { manualGear = it }, { manualGear = null },
                                    apps, actions, Modifier.fillMaxSize())
                            }
                        }
                    }
                    QuickControlBar(status, night, { prefs.setNight(!night) }, actions,
                        Modifier.fillMaxWidth().height(if (immersive) 48.dp else 58.dp), config.nativeAudio)
                }
            }
            sheet?.let { selected ->
                LauncherSheet(selected, apps, config, prefs, drive, weather, status, can, actions,
                    onDismiss = { sheet = null }, onChoose = configure,
                    onRefreshWeather = { scope.launch { drive.lat?.let { lat -> drive.lon?.let { lon -> weatherSource.refresh(lat, lon, true) } } } })
            }
        }
    }
}

@Composable
private fun animatedPalette(night: Boolean): SpacePalette {
    val target = if (night) NightPalette else DayPalette
    val abyss by animateColorAsState(target.abyss, tween(650), label = "abyss")
    val midnight by animateColorAsState(target.midnight, tween(650), label = "midnight")
    val aurora by animateFloatAsState(target.aurora, tween(650), label = "aurora")
    val textPrimary by animateColorAsState(target.textPrimary, tween(650), label = "textPrimary")
    val textSecondary by animateColorAsState(target.textSecondary, tween(650), label = "textSecondary")
    val glassFill by animateColorAsState(target.glassFill, tween(650), label = "glassFill")
    val pillFill by animateColorAsState(target.pillFill, tween(650), label = "pillFill")
    val trackFill by animateColorAsState(target.trackFill, tween(650), label = "trackFill")
    return SpacePalette(night, abyss, midnight, aurora, textPrimary, textSecondary, glassFill, pillFill, trackFill)
}

// =====================================================================
//  Texto base (sin Material)
// =====================================================================

@Composable
internal fun Txt(
    text: String,
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    color: Color? = null,
    modifier: Modifier = Modifier,
    spacing: TextUnit = TextUnit.Unspecified,
    style: TextStyle = TextStyle.Default
) {
    val p = LocalBackdrop.current.palette
    val resolvedColor = when (color) {
        null -> p.textPrimary
        Ultra.Secondary -> p.textSecondary
        else -> color
    }
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(
            TextStyle(color = resolvedColor, fontSize = size, fontWeight = weight, letterSpacing = spacing)
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

// =====================================================================
//  A) Dock espacial con squircles
// =====================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpatialDock(status: DeviceStatus, hasFix: Boolean, apps: List<AppEntry>, config: LauncherConfig,
    actions: LauncherActions, onGrid: () -> Unit, configure: (String) -> Unit, modifier: Modifier) {
    val recents = recentApps(apps, config)
    val slots = listOf("maps", "music", "phone", "obd")
    val defaultApps = DockShortcuts.mapIndexedNotNull { i, shortcut -> resolveApp(apps, config, slots[i], shortcut) }
    val shown = (recents + defaultApps).distinctBy { it.packageName }.take(4)
    SpatialGlassSurface(modifier, cornerRadius = 30.dp) {
        Column(Modifier.fillMaxSize().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ClockText()
            Row(Modifier.height(40.dp).bounceClick { configure("status") }, horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                StatusIcon(Glyph.Wifi, status.wifi); StatusIcon(Glyph.Bluetooth, status.bluetooth); StatusIcon(Glyph.Navigation, hasFix)
            }
            Spacer(Modifier.weight(1f))
            if (shown.isEmpty()) DockShortcuts.forEachIndexed { i, shortcut ->
                Box(Modifier.height(70.dp).fillMaxWidth().combinedClickable(onClick = { actions.openSlot(slots[i], shortcut, apps) },
                    onLongClick = { configure(slots[i]) }), contentAlignment = Alignment.Center) {
                    AppSquircleIcon(shortcut.glyph, shortcut.colors, 46.dp, iconResName = shortcut.iconResName)
                }
            } else shown.forEach { app ->
                Box(Modifier.height(70.dp).fillMaxWidth().combinedClickable(onClick = { actions.open(app) },
                    onLongClick = { configure("settings") }).semantics { contentDescription = "Reciente: ${app.label}" }, contentAlignment = Alignment.Center) {
                    AppIcon(app, Modifier.size(60.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(46.dp).bounceClick { configure("settings") }, contentAlignment = Alignment.Center) { GlyphIcon(Glyph.Gear, Modifier.size(24.dp), LocalBackdrop.current.palette.textPrimary) }
            Box(Modifier.size(48.dp).bounceClick(onClick = onGrid).semantics { contentDescription = "Abrir aplicaciones" }
                .spatialGlass(CircleShape), contentAlignment = Alignment.Center) { GlyphIcon(Glyph.Grid, Modifier.size(24.dp), LocalBackdrop.current.palette.textPrimary) }
        }
    }
}

@Composable
internal fun AppIcon(app: AppEntry, modifier: Modifier) {
    val night = LocalBackdrop.current.palette.night
    Image((if (night) app.iconDark else app.iconLight) ?: app.icon, app.label, modifier, contentScale = ContentScale.Fit)
}

@Composable
private fun ClockText() {
    var date by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            date = Date()
            delay(1000L)
        }
    }
    val format = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Txt(format.format(date), 20.sp, FontWeight.Bold, spacing = (-0.5).sp, style = TextStyle(fontFeatureSettings = "tnum"))
}

@Composable
private fun StatusIcon(glyph: Glyph, on: Boolean) {
    GlyphIcon(glyph, Modifier.size(18.dp), tint = if (on) (if (LocalBackdrop.current.palette.night) Ultra.Cyan else Color(0xFF0067A0)) else LocalBackdrop.current.palette.textSecondary.copy(alpha = 0.4f))
}

@Composable
private fun DockButton(shortcut: Shortcut, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        if (active) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(4.dp)
                    .height(16.dp)
                    .shadow(10.dp, RoundedCornerShape(2.dp), ambientColor = Ultra.Cyan, spotColor = Ultra.Cyan)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Ultra.White)
            )
        }
        AppSquircleIcon(
            glyph = shortcut.glyph,
            colors = shortcut.colors,
            size = 48.dp,
            iconResName = shortcut.iconResName,
            modifier = Modifier.align(Alignment.Center),
            onClick = onClick
        )
    }
}

// =====================================================================
//  B) Bento grid
// =====================================================================

@Composable
private fun Dashboard(drive: DriveState, speed: ResolvedSpeed, gear: Gear?, gearIsManual: Boolean,
    onGearSelected: (Gear) -> Unit, onGearAuto: () -> Unit, media: MediaInfo, weather: WeatherInfo,
    apps: List<AppEntry>, status: DeviceStatus, actions: LauncherActions,
    config: LauncherConfig = LauncherConfig(), configure: (String) -> Unit = {}, can: CanState = CanState(), permissionVersion: Int = 0,
    mapExpanded: Boolean = false, onExpandMap: () -> Unit = {}) {
    val expanded = mapExpanded && !config.dashboardCamera
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(if (BuildConfig.REAL_APP_EMBEDDING) 0.64f else 0.52f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (config.dashboardCamera) CameraPanel(config, apps, actions, configure, permissionVersion, Modifier.weight(1f).fillMaxWidth())
            else NavigationPanel(drive, config, apps, actions, configure, Modifier.weight(1f).fillMaxWidth(), speed, expanded, onExpandMap)
            if (!expanded) QuickAppsWidget(apps, status, actions, Modifier.fillMaxWidth().height(100.dp), config, configure)
        }
        if (!expanded) Column(Modifier.weight(if (BuildConfig.REAL_APP_EMBEDDING) 0.36f else 0.48f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NowPlayingCard(media, apps, actions, Modifier.weight(0.64f).fillMaxWidth(), configure)
            WeatherWidget(weather, drive, Modifier.weight(0.36f).fillMaxWidth()) { configure("weather_detail") }
        }
    }
}

// ---------- Columna 1: Ultra Cluster ----------

@Composable
private fun ClusterCard(
    drive: DriveState,
    speed: ResolvedSpeed,
    gear: Gear?,
    gearIsManual: Boolean,
    onGearSelected: (Gear) -> Unit,
    onGearAuto: () -> Unit,
    apps: List<AppEntry>,
    actions: LauncherActions,
    modifier: Modifier
) {
    val target = when (gear) {
        Gear.S -> AccentSport
        Gear.R -> AccentReverse
        else -> AccentNormal
    }
    val accentStart by animateColorAsState(target.start, tween(450), label = "accentStart")
    val accentEnd by animateColorAsState(target.end, tween(450), label = "accentEnd")

    SpatialGlassSurface(modifier, cornerRadius = 32.dp) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Row(
                Modifier
                    .spatialGlass(RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlyphIcon(
                    Glyph.Navigation,
                    Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = drive.bearing ?: 0f },
                    tint = accentStart
                )
                Spacer(Modifier.width(10.dp))
                Txt(tripText(drive, speed.source), 15.sp, FontWeight.SemiBold)
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val animatedSpeed by animateFloatAsState(speed.kmh, tween(650), label = "speed")
                SpeedArc(animatedSpeed, accentStart, accentEnd, Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val isNight = LocalBackdrop.current.palette.night
                    val speedBrush = if (isNight) {
                        Brush.verticalGradient(listOf(Ultra.White, Ultra.Silver))
                    } else {
                        Brush.verticalGradient(listOf(Color(0xFF0B132B), Color(0xFF1D3557)))
                    }
                    Txt(
                        if (speed.source == SpeedSource.NONE) "—" else animatedSpeed.roundToInt().toString(), 96.sp, FontWeight.Bold,
                        color = Color.Unspecified,
                        style = TextStyle(
                            brush = speedBrush,
                            fontFeatureSettings = "tnum",
                            shadow = Shadow(accentEnd.copy(alpha = if (isNight) 0.62f else 0.35f), Offset.Zero, 38f)
                        )
                    )
                    val unit = when (speed.source) {
                        SpeedSource.GPS -> "KM/H  ·  GPS"
                        SpeedSource.CAN -> "KM/H  ·  CAN"
                        SpeedSource.NONE -> "KM/H"
                    }
                    Txt(unit, 14.sp, FontWeight.Bold, Ultra.Secondary, spacing = 3.sp)
                    Spacer(Modifier.height(18.dp))
                    GearSelector(gear, gearIsManual, onGearSelected, onGearAuto)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MapPill("Google Maps", accentEnd) { actions.openSlot("maps", DockShortcuts[0], apps) }
                MapPill("Cámara", accentEnd) { actions.openSlot("camera", QuickShortcuts[3], apps) }
            }
        }
    }
}

private fun tripText(drive: DriveState, source: SpeedSource): String {
    if (!drive.hasFix) return if (source == SpeedSource.CAN) "Sin GPS  ·  velocidad por CAN" else "Buscando señal GPS"
    val heading = drive.bearing?.let { "Rumbo ${cardinal(it)}" } ?: "En camino"
    val altitude = drive.altitude?.let { "  ·  ${it.roundToInt()} m" } ?: ""
    return heading + altitude
}

private fun cardinal(degrees: Float): String {
    val names = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    val normalized = ((degrees % 360f) + 360f) % 360f
    return names[((normalized + 22.5f) / 45f).toInt() % 8]
}

@Composable
private fun SpeedArc(speed: Float, accentStart: Color, accentEnd: Color, modifier: Modifier, maxSpeed: Float = 200f) {
    val p = LocalBackdrop.current.palette
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = p.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    Canvas(modifier) {
        val strokeWidth = 14.dp.toPx()
        val diameter = min(size.width * 0.86f, size.height / 0.80f)
        val r = diameter / 2f
        val center = Offset(size.width / 2f, size.height / 2f + r * 0.22f)
        val topLeft = Offset(center.x - r, center.y - r)
        val arcSize = Size(diameter, diameter)

        drawArc(
            p.trackFill, startAngle = 150f, sweepAngle = 240f, useCenter = false,
            topLeft = topLeft, size = arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Round)
        )
        val majorTickColor = if (p.night) Color(0x8CFFFFFF) else Color(0x8C0B132B)
        val minorTickColor = if (p.night) Color(0x46FFFFFF) else Color(0x400B132B)
        for (i in 0..20) {
            val a = Math.toRadians(150.0 + 12.0 * i)
            val c = cos(a).toFloat()
            val s = sin(a).toFloat()
            val major = i % 5 == 0
            val r1 = r - strokeWidth * 1.5f
            val r2 = r1 - (if (major) 14.dp.toPx() else 7.dp.toPx())
            drawLine(
                if (major) majorTickColor else minorTickColor,
                center + Offset(c * r1, s * r1), center + Offset(c * r2, s * r2),
                strokeWidth = (if (major) 2.2.dp else 1.5.dp).toPx(), cap = StrokeCap.Round
            )
            if (major) {
                val rl = r1 - 28.dp.toPx()
                val layout = measurer.measure("${i * 10}", labelStyle)
                drawText(
                    layout,
                    topLeft = center + Offset(c * rl, s * rl) - Offset(layout.size.width / 2f, layout.size.height / 2f)
                )
            }
        }

        val fraction = (speed / maxSpeed).coerceIn(0f, 1f)
        if (fraction > 0.002f) {
            val sweep = 240f * fraction
            drawArc(
                accentStart.copy(alpha = 0.14f), startAngle = 150f, sweepAngle = sweep, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(44.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                accentStart.copy(alpha = 0.35f), startAngle = 150f, sweepAngle = sweep, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(28.dp.toPx(), cap = StrokeCap.Round)
            )
            val startFrac = 150f / 360f
            val endFrac = (150f + sweep) / 360f
            val brush = if (endFrac <= 1f) {
                Brush.sweepGradient(
                    0.0f to accentEnd,
                    startFrac to accentStart,
                    endFrac to accentEnd,
                    1.0f to accentEnd,
                    center = center
                )
            } else {
                val atZero = lerp(accentStart, accentEnd, (360f - 150f) / sweep)
                Brush.sweepGradient(
                    0.0f to atZero,
                    (endFrac - 1f) to accentEnd,
                    startFrac to accentStart,
                    1.0f to atZero,
                    center = center
                )
            }
            drawArc(
                brush, startAngle = 150f, sweepAngle = sweep, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun GearSelector(gear: Gear?, manual: Boolean, onSelect: (Gear) -> Unit, onAuto: () -> Unit) {
    val select by rememberUpdatedState(onSelect)
    val auto by rememberUpdatedState(onAuto)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .spatialGlass(RoundedCornerShape(22.dp), fill = Color(0x14FFFFFF))
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (g in Gear.values()) {
                val selected = g == gear
                val pillFill = when {
                    selected && g == Gear.S -> Ultra.Magenta.copy(alpha = 0.38f)
                    selected && g == Gear.R -> Ultra.Amber.copy(alpha = 0.38f)
                    selected -> Color(0x47FFFFFF)
                    else -> Color.Transparent
                }
                Box(
                    Modifier
                        .size(38.dp)
                        .then(
                            if (selected) Modifier.spatialGlass(
                                RoundedCornerShape(14.dp),
                                fill = pillFill
                            ) else Modifier
                        )
                        .pointerInput(g) {
                            detectTapGestures(
                                onTap = { select(g) },
                                onLongPress = { auto() }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val p = LocalBackdrop.current.palette
                    Txt(
                        g.name, 16.sp, FontWeight.Bold,
                        color = if (selected) p.textPrimary else p.textSecondary.copy(alpha = 0.55f)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Txt(
            if (manual) "SELECCIÓN VISUAL · NO CONTROLA EL VEHÍCULO" else if (gear == null) "SIN LECTURA DE MARCHA CAN" else "MARCHA RECIBIDA · CAN",
            10.sp, FontWeight.Bold, Ultra.Secondary, spacing = 1.5.sp
        )
    }
}

@Composable
private fun MapPill(label: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .bounceClick(onClick = onClick)
            .spatialGlass(RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphIcon(Glyph.Map, Modifier.size(18.dp), tint = accent)
        Spacer(Modifier.width(8.dp))
        Txt(label, 15.sp, FontWeight.SemiBold)
    }
}

// ---------- Columna 2, fila superior: Now Playing ----------

private val PlaceholderArt = Brush.linearGradient(
    listOf(Color(0xFFFF9F0A), Ultra.Magenta, Ultra.Violet),
    start = Offset.Zero,
    end = Offset.Infinite
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlayingCard(media: MediaInfo, apps: List<AppEntry>, actions: LauncherActions, modifier: Modifier,
    configure: (String) -> Unit = {}) {
    var clock by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(media.isPlaying, media.updateTime) { clock = SystemClock.elapsedRealtime(); while (media.isPlaying) { delay(500); clock = SystemClock.elapsedRealtime() } }
    val position = media.livePosition(clock)
    val fraction = if (media.durationMs > 0) position.toFloat() / media.durationMs else 0f
    val title = if (!media.hasSession) "Apple Music" else media.title.ifBlank { "Sin título" }
    val subtitle = when { !media.hasAccess -> "Permite mostrar tu música"; !media.hasSession -> "Toca para reproducir"; else -> media.artist }
    val openMusic = { actions.openSlot("music", DockShortcuts[1], apps) }
    SpatialGlassSurface(modifier, cornerRadius = 26.dp, glow = media.dominant ?: Ultra.Magenta) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).combinedClickable(onClick = openMusic, onLongClick = { configure("music") })) {
                    Txt(title, 22.sp, FontWeight.Bold)
                    Txt(subtitle, 15.sp, color = Ultra.Secondary)
                }
                SmallAction(if (showLyrics) "Carátula" else "Letra") { showLyrics = !showLyrics }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    !media.hasAccess -> SmallAction("Permitir acceso a música") { actions.openNotificationAccess() }
                    showLyrics && media.hasSession -> LyricsContent(media, position, Modifier.fillMaxSize())
                    else -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(media.dominant?.copy(alpha = 0.25f) ?: LocalBackdrop.current.palette.pillFill)
                        .combinedClickable(onClick = openMusic, onLongClick = { configure("music") }), contentAlignment = Alignment.Center) {
                        media.art?.let {
                            Image(it, null, Modifier.fillMaxSize().blur(24.dp), contentScale = ContentScale.Crop, alpha = 0.4f)
                            Image(it, "Carátula de ${media.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                            ?: GlyphIcon(Glyph.Note, Modifier.size(48.dp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(LocalBackdrop.current.palette.trackFill)) {
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(LocalBackdrop.current.palette.textPrimary))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Txt(formatTime(position), 11.sp, color = Ultra.Secondary)
                RoundGlassButton(Glyph.Prev, 42.dp, Color(0x14FFFFFF)) { actions.previous() }
                RoundGlassButton(if (media.isPlaying) Glyph.Pause else Glyph.Play, 48.dp, Color(0x40FFFFFF)) {
                    if (media.hasSession) actions.playPause() else openMusic()
                }
                RoundGlassButton(Glyph.Next, 42.dp, Color(0x14FFFFFF)) { actions.next() }
                Txt(if (media.durationMs > 0) formatTime(media.durationMs) else "—", 11.sp, color = Ultra.Secondary)
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun RoundGlassButton(glyph: Glyph, size: Dp, fill: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .bounceClick(onClick = onClick)
            .spatialGlass(CircleShape, fill = fill),
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(glyph, Modifier.size(size * 0.42f), tint = LocalBackdrop.current.palette.textPrimary)
    }
}

// ---------- Columna 2, fila inferior: widgets gemelos ----------

@Composable
private fun WeatherWidget(weather: WeatherInfo, drive: DriveState, modifier: Modifier, onClick: () -> Unit) {
    SpatialGlassSurface(modifier.bounceClick(onClick = onClick), cornerRadius = 24.dp) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WeatherGlyph(weather.code, Modifier.size(42.dp))
                Txt(weather.tempC?.let { "$it°" } ?: "—°", 40.sp, FontWeight.Bold)
                Column(Modifier.weight(1f)) {
                    Txt(weather.description, 17.sp, FontWeight.Medium)
                    Txt("Clima exterior", 13.sp, color = Ultra.Secondary)
                }
            }
            Txt(listOfNotNull(weather.windKmh?.let { "Viento $it km/h" }, weather.humidityPct?.let { "Humedad $it%" }).joinToString(" · "), 16.sp)
            Txt(if (weather.error != null) "Sin conexión · ver detalle" else "${weather.source} · ${weatherTime(weather.observedAt)}", 12.sp, color = Ultra.Secondary)
        }
    }
}

@Composable
private fun CompassBadge(bearing: Float?) {
    Box(
        Modifier
            .size(44.dp)
            .spatialGlass(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            Modifier
                .size(28.dp)
                .graphicsLayer { rotationZ = -(bearing ?: 0f) }
        ) {
            val c = center
            val h = size.height / 2f
            val north = Path().apply {
                moveTo(c.x, c.y - h)
                lineTo(c.x + h * 0.28f, c.y)
                lineTo(c.x - h * 0.28f, c.y)
                close()
            }
            val south = Path().apply {
                moveTo(c.x, c.y + h)
                lineTo(c.x + h * 0.28f, c.y)
                lineTo(c.x - h * 0.28f, c.y)
                close()
            }
            drawPath(north, Ultra.Electric)
            drawPath(south, Color(0x78FFFFFF))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickAppsWidget(apps: List<AppEntry>, status: DeviceStatus, actions: LauncherActions, modifier: Modifier,
    config: LauncherConfig, configure: (String) -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val defaults = listOf(QuickShortcuts[1], QuickShortcuts[4], QuickShortcuts[3], QuickShortcuts[6],
        QuickShortcuts[0], QuickShortcuts[2], QuickShortcuts[5], QuickShortcuts[7])
    SpatialGlassSurface(modifier, cornerRadius = 24.dp) {
        Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (column in 0..3) {
                val index = page * 4 + column
                val default = defaults[index]
                val slot = listOf("music", "maps", "camera", "carplay").getOrNull(index) ?: "quick.$index"
                val target = resolveApp(apps, config, slot, default)
                Column(Modifier.weight(1f).fillMaxHeight().combinedClickable(
                    onClick = { if (target == null) configure(slot) else actions.open(target) }, onLongClick = { configure(slot) }),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (target != null) AppIcon(target, Modifier.size(56.dp))
                    else AppSquircleIcon(default.glyph, default.colors, 56.dp, iconResName = default.iconResName)
                    Txt(target?.label ?: default.title, 13.sp)
                }
            }
            Box(Modifier.width(38.dp).fillMaxHeight().bounceClick { page = 1 - page }
                .semantics { contentDescription = "Cambiar página de accesos" }, contentAlignment = Alignment.Center) { Txt("${page + 1}/2", 14.sp) }
        }
    }
}

// =====================================================================
//  C) Barra inferior: tubos de cristal
// =====================================================================

@Composable
private fun QuickControlBar(
    status: DeviceStatus,
    night: Boolean,
    onNight: () -> Unit,
    actions: LauncherActions,
    modifier: Modifier,
    nativeAudio: Boolean = false
) {
    val tw = TopwayAudio.controller.state
    val nativeReady = nativeAudio && tw.usable(android.os.SystemClock.elapsedRealtime())
    val shownVolume = if (nativeAudio) tw.volume?.fraction ?: 0f else status.volume
    var volume by remember { mutableFloatStateOf(shownVolume) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(shownVolume, dragging, nativeAudio, tw.pendingLevel) {
        if (!dragging) volume = shownVolume
    }
    var brightness by remember { mutableFloatStateOf(status.brightness) }
    var draggingBrightness by remember { mutableStateOf(false) }
    LaunchedEffect(status.brightness) { if (!draggingBrightness) brightness = status.brightness }

    SpatialGlassSurface(modifier, cornerRadius = 28.dp) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (nativeAudio && (!nativeReady || tw.pendingLevel != null)) Box(
                Modifier.weight(1f).height(44.dp).bounceClick { actions.openNativeControls() }, contentAlignment = Alignment.Center
            ) {
                Txt(if (tw.pendingLevel != null) "Confirmando volumen…" else "Volumen del estéreo · Abrir", 14.sp, FontWeight.Medium)
            } else GlassTube(
                value = volume,
                glyph = Glyph.Speaker,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                onDragState = { dragging = it },
                onCommit = { if (nativeReady) actions.setVolume(it) },
                label = if (nativeReady) {
                    val max = tw.volume!!.maximum
                    if (tw.volume?.muted == true && !dragging) "Silencio" else "${(volume * max).roundToInt()}/$max"
                } else null
            ) {
                volume = it
                if (!nativeAudio) actions.setVolume(it)
            }
            GlassTube(
                value = brightness,
                glyph = Glyph.Sun,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                onDragState = { draggingBrightness = it }
            ) {
                brightness = it.coerceAtLeast(0.12f)
                actions.setBrightness(brightness)
            }
            Box(
                Modifier
                    .size(40.dp)
                    .bounceClick(onClick = onNight)
                    .spatialGlass(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(if (night) Glyph.Moon else Glyph.Sun, Modifier.size(20.dp), tint = if (night) Ultra.Cyan else Ultra.Yellow)
            }
        }
    }
}

@Composable
internal fun GlassTube(
    value: Float,
    glyph: Glyph,
    modifier: Modifier,
    onDragState: (Boolean) -> Unit = {},
    onCommit: (Float) -> Unit = {},
    label: String? = null,
    onChange: (Float) -> Unit
) {
    val change by rememberUpdatedState(onChange)
    val dragState by rememberUpdatedState(onDragState)
    val commit by rememberUpdatedState(onCommit)
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(Color(0x14FFFFFF))
            .pointerInput(Unit) {
                detectTapGestures {
                    val fraction = (it.x / size.width).coerceIn(0f, 1f)
                    change(fraction)
                    commit(fraction)
                }
            }
            .pointerInput(Unit) {
                var fraction = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragState(true)
                        fraction = (it.x / size.width).coerceIn(0f, 1f)
                        change(fraction)
                    },
                    onDragEnd = { commit(fraction); dragState(false) },
                    onDragCancel = { dragState(false) }
                ) { pointer, _ ->
                    pointer.consume()
                    fraction = (pointer.position.x / size.width).coerceIn(0f, 1f)
                    change(fraction)
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(value.coerceIn(0f, 1f))
                .clip(shape)
                .background(Color(0x4DFFFFFF))
        )
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(0f to Color(0x38FFFFFF), 0.5f to Color.White.copy(alpha = 0f)))
                .border(1.2.dp, SpecularRim, shape)
        )
        Txt(label ?: "${(value * 100).roundToInt()}%", 12.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
        GlyphIcon(
            glyph,
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 14.dp)
                .size(18.dp),
            tint = LocalBackdrop.current.palette.textPrimary
        )
    }
}

// =====================================================================
//  Cajón de aplicaciones
// =====================================================================

@Composable
private fun AppDrawer(apps: List<AppEntry>, onOpen: (AppEntry) -> Unit) {
    SpatialGlassSurface(Modifier.fillMaxSize(), cornerRadius = 32.dp) {
        if (apps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Txt("Cargando apps…", 16.sp, FontWeight.Medium, Ultra.Secondary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(apps, key = { it.packageName + "/" + it.activityName }) { app ->
                    Column(
                        Modifier.bounceClick { onOpen(app) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .size(88.dp)
                                .spatialGlass(RoundedCornerShape(24)),
                            contentAlignment = Alignment.Center
                        ) {
                            val isNight = LocalBackdrop.current.palette.night
                            val displayIcon = (if (isNight) app.iconDark else app.iconLight) ?: app.icon
                            Image(displayIcon, contentDescription = app.label, modifier = Modifier.size(64.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Txt(app.label, 14.sp, FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// =====================================================================
//  Vista previa en Android Studio
// =====================================================================

private object PreviewActions : LauncherActions {
    override fun open(app: AppEntry) = Unit
    override fun open(shortcut: Shortcut, apps: List<AppEntry>) = Unit
    override fun openPackage(packageName: String) = Unit
    override fun openNotificationAccess() = Unit
    override fun setVolume(fraction: Float) = Unit
    override fun setBrightness(fraction: Float) = Unit
    override fun playPause() = Unit
    override fun next() = Unit
    override fun previous() = Unit
}

@Preview(widthDp = 1333, heightDp = 800)
@Composable
private fun DashboardPreview() {
    SpaceBox(Modifier.padding(18.dp)) {
        Dashboard(
            drive = Demo.drive(0L),
            speed = ResolvedSpeed(64f, SpeedSource.GPS),
            gear = Gear.D,
            gearIsManual = false,
            onGearSelected = {},
            onGearAuto = {},
            media = Demo.media(0L),
            weather = Demo.weather,
            apps = emptyList(),
            status = Demo.status,
            actions = PreviewActions
        )
    }
}


