package com.glasscar.launcher

import android.os.SystemClock
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
    actions: LauncherActions
) {
    val context = LocalContext.current
    val realDrive by driveFlow.collectAsState()
    val realMedia by mediaFlow.collectAsState()
    val can by canFlow.collectAsState()
    val realWeather by weatherSource.state.collectAsState()

    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val demoStart = remember { SystemClock.elapsedRealtime() }
    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(if (DEMO_MODE) 250L else 500L)
        }
    }
    val drive = if (DEMO_MODE) Demo.drive(now) else realDrive
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
    val latKey = drive.lat?.let { (it * 10).roundToInt() }
    val lonKey = drive.lon?.let { (it * 10).roundToInt() }
    LaunchedEffect(latKey, lonKey) {
        val lat = drive.lat
        val lon = drive.lon
        if (DEMO_MODE || lat == null || lon == null) return@LaunchedEffect
        while (true) {
            weatherSource.refresh(lat, lon)
            delay(60_000L)
        }
    }

    // ---------- Estado del dispositivo ----------
    var status by remember { mutableStateOf(if (DEMO_MODE) Demo.status else DeviceStatus()) }
    if (!DEMO_MODE) {
        LaunchedEffect(Unit) {
            while (true) {
                status = withContext(Dispatchers.Default) { readDeviceStatus(context) }
                delay(3000L)
            }
        }
    }

    var night by rememberSaveable { mutableStateOf(true) }
    // Si el módulo CANbus RP5-FD-002 reporta cambio en las luces (ILL) de la Escape, ajusta automático
    LaunchedEffect(can.lightsOn) {
        can.lightsOn?.let { night = it }
    }

    val palette = animatedPalette(night)
    var rootSize by remember { mutableStateOf(Size.Zero) }
    var activeDock by remember { mutableIntStateOf(-1) }

    BackHandler(enabled = showDrawer) { onCloseDrawer() }

    CompositionLocalProvider(LocalBackdrop provides Backdrop(rootSize, palette)) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { rootSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .drawBehind { drawSpaceBackground(palette, size) }
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SpatialDock(
                    status = status,
                    hasFix = drive.hasFix,
                    active = activeDock,
                    drawerOpen = showDrawer,
                    onDock = { i ->
                        activeDock = i
                        onCloseDrawer()
                        actions.open(DockShortcuts[i], apps)
                    },
                    onGrid = onToggleDrawer,
                    modifier = Modifier
                        .width(78.dp)
                        .fillMaxHeight()
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AnimatedContent(
                        targetState = showDrawer,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        transitionSpec = {
                            (fadeIn(tween(280)) + scaleIn(initialScale = 0.94f, animationSpec = tween(280)))
                                .togetherWith(fadeOut(tween(180)))
                        },
                        label = "center"
                    ) { drawer ->
                        if (drawer) {
                            AppDrawer(apps) { app ->
                                actions.open(app)
                                onCloseDrawer()
                            }
                        } else {
                            Dashboard(
                                drive = drive,
                                speed = speed,
                                gear = gear,
                                gearIsManual = manualGear != null,
                                onGearSelected = { manualGear = it },
                                onGearAuto = { manualGear = null },
                                media = media,
                                weather = weather,
                                apps = apps,
                                status = status,
                                actions = actions
                            )
                        }
                    }
                    QuickControlBar(
                        status = status,
                        night = night,
                        onNight = { night = !night },
                        actions = actions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    )
                }
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
private fun Txt(
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

@Composable
private fun SpatialDock(
    status: DeviceStatus,
    hasFix: Boolean,
    active: Int,
    drawerOpen: Boolean,
    onDock: (Int) -> Unit,
    onGrid: () -> Unit,
    modifier: Modifier
) {
    SpatialGlassSurface(modifier, cornerRadius = 39.dp) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ClockText()
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                StatusIcon(Glyph.Wifi, status.wifi)
                StatusIcon(Glyph.Bluetooth, status.bluetooth)
                StatusIcon(Glyph.Navigation, hasFix)
            }

            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                DockShortcuts.forEachIndexed { i, shortcut ->
                    DockButton(shortcut, active = i == active && !drawerOpen) { onDock(i) }
                }
            }
            Spacer(Modifier.weight(1f))

            Box(
                Modifier
                    .size(48.dp)
                    .bounceClick(onClick = onGrid)
                    .spatialGlass(CircleShape, fill = if (drawerOpen) Color(0x40FFFFFF) else Color(0x24FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(Glyph.Grid, Modifier.size(20.dp))
            }
        }
    }
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
    GlyphIcon(glyph, Modifier.size(12.dp), tint = if (on) Ultra.Secondary else Color(0x33EBEBF5))
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
private fun Dashboard(
    drive: DriveState,
    speed: ResolvedSpeed,
    gear: Gear,
    gearIsManual: Boolean,
    onGearSelected: (Gear) -> Unit,
    onGearAuto: () -> Unit,
    media: MediaInfo,
    weather: WeatherInfo,
    apps: List<AppEntry>,
    status: DeviceStatus,
    actions: LauncherActions
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ClusterCard(
            drive = drive,
            speed = speed,
            gear = gear,
            gearIsManual = gearIsManual,
            onGearSelected = onGearSelected,
            onGearAuto = onGearAuto,
            apps = apps,
            actions = actions,
            modifier = Modifier
                .weight(0.46f)
                .fillMaxHeight()
        )
        Column(
            Modifier
                .weight(0.54f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            NowPlayingCard(
                media, apps, actions,
                Modifier
                    .weight(0.56f)
                    .fillMaxWidth()
            )
            Row(
                Modifier
                    .weight(0.44f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                WeatherWidget(
                    weather, drive,
                    Modifier
                        .weight(0.5f)
                        .fillMaxHeight()
                )
                QuickAppsWidget(
                    apps, status, actions,
                    Modifier
                        .weight(0.5f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

// ---------- Columna 1: Ultra Cluster ----------

@Composable
private fun ClusterCard(
    drive: DriveState,
    speed: ResolvedSpeed,
    gear: Gear,
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
                        animatedSpeed.roundToInt().toString(), 96.sp, FontWeight.Bold,
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
                MapPill("Google Maps", accentEnd) { actions.open(GoogleMapsShortcut, apps) }
                MapPill("Waze", accentEnd) { actions.open(WazeShortcut, apps) }
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
private fun GearSelector(gear: Gear, manual: Boolean, onSelect: (Gear) -> Unit, onAuto: () -> Unit) {
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
            if (manual) "MANUAL  ·  mantén presionado para auto" else "AUTO",
            10.sp, FontWeight.Bold, Color(0x80EBEBF5), spacing = 1.5.sp
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

@Composable
private fun NowPlayingCard(media: MediaInfo, apps: List<AppEntry>, actions: LauncherActions, modifier: Modifier) {
    var clock by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(media.isPlaying, media.updateTime) {
        while (media.isPlaying) {
            clock = SystemClock.elapsedRealtime()
            delay(500L)
        }
    }
    val position = media.livePosition(clock)
    val fraction = if (media.durationMs > 0) position.toFloat() / media.durationMs else 0f

    val title = when {
        !media.hasAccess -> "Música"
        !media.hasSession -> "Nada en reproducción"
        else -> media.title.ifBlank { "Sin título" }
    }
    val subtitle = when {
        !media.hasAccess -> "Toca para permitir el acceso a la música"
        !media.hasSession -> "Toca para abrir tu app de música"
        else -> media.artist
    }
    val onCardTap = {
        when {
            !media.hasAccess -> actions.openNotificationAccess()
            !media.hasSession -> actions.open(DockShortcuts[1], apps)
            else -> media.appPackage?.let { actions.openPackage(it) }
        }
        Unit
    }

    SpatialGlassSurface(
        modifier,
        cornerRadius = 32.dp,
        glow = media.dominant ?: Ultra.Magenta,
        glowCenter = Offset(0.30f, 0.30f)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(22.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .bounceClick(onClick = onCardTap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val artShape = RoundedCornerShape(22.dp)
                Box(
                    Modifier
                        .size(104.dp)
                        .shadow(18.dp, artShape, ambientColor = Ultra.ShadowAmbientDark, spotColor = Ultra.ShadowSpotDark)
                        .clip(artShape)
                        .background(PlaceholderArt),
                    contentAlignment = Alignment.Center
                ) {
                    val art = media.art
                    if (art != null) {
                        Image(art, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(
                                    Brush.radialGradient(
                                        listOf(Color(0x59FFFFFF), Color.White.copy(alpha = 0f)),
                                        center = Offset(90f, 70f),
                                        radius = 180f
                                    )
                                )
                        )
                        GlyphIcon(Glyph.Note, Modifier.size(44.dp))
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Txt(title, 22.sp, FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Txt(subtitle, 16.sp, FontWeight.Medium, Ultra.Secondary)
                }
            }

            Spacer(Modifier.weight(1f))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color(0x26FFFFFF))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(Ultra.White)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Txt(formatTime(position), 12.sp, FontWeight.Medium, Ultra.Secondary, style = TextStyle(fontFeatureSettings = "tnum"))
                Spacer(Modifier.weight(1f))
                if (media.durationMs > 0) {
                    Txt(
                        "-" + formatTime(media.durationMs - position), 12.sp, FontWeight.Medium, Ultra.Secondary,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundGlassButton(Glyph.Prev, 46.dp, Color(0x14FFFFFF)) { actions.previous() }
                RoundGlassButton(if (media.isPlaying) Glyph.Pause else Glyph.Play, 58.dp, Color(0x40FFFFFF)) { actions.playPause() }
                RoundGlassButton(Glyph.Next, 46.dp, Color(0x14FFFFFF)) { actions.next() }
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
        GlyphIcon(glyph, Modifier.size(size * 0.42f))
    }
}

// ---------- Columna 2, fila inferior: widgets gemelos ----------

@Composable
private fun WeatherWidget(weather: WeatherInfo, drive: DriveState, modifier: Modifier) {
    SpatialGlassSurface(modifier, cornerRadius = 28.dp) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(18.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WeatherGlyph(weather.code, Modifier.size(44.dp))
                Spacer(Modifier.weight(1f))
                CompassBadge(drive.bearing)
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Txt(
                    weather.tempC?.let { "$it°" } ?: "--°",
                    38.sp,
                    FontWeight.Light,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
                if (weather.windKmh != null || weather.humidityPct != null) {
                    val meta = listOfNotNull(
                        weather.windKmh?.let { "${it} km/h" },
                        weather.humidityPct?.let { "${it}%" }
                    ).joinToString(" · ")
                    Txt(meta, 12.sp, FontWeight.SemiBold, Ultra.Cyan, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            Txt(weather.description, 14.sp, FontWeight.Medium, Ultra.Secondary)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Mountain, Modifier.size(14.dp), tint = Ultra.Secondary)
                Spacer(Modifier.width(6.dp))
                Txt(drive.altitude?.let { "${it.roundToInt()} m de altitud" } ?: "Altitud --", 13.sp, FontWeight.Medium, Ultra.Secondary)
            }
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

@Composable
private fun QuickAppsWidget(apps: List<AppEntry>, status: DeviceStatus, actions: LauncherActions, modifier: Modifier) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pages = remember { QuickShortcuts.chunked(4) }
    val currentShortcuts = pages.getOrElse(page % pages.size) { pages.first() }

    SpatialGlassSurface(modifier, cornerRadius = 28.dp) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(18.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        if (kotlin.math.abs(dragAmount) > 24f) {
                            change.consume()
                            page = if (dragAmount < 0) (page + 1) % pages.size else (page - 1 + pages.size) % pages.size
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (row in currentShortcuts.chunked(2)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (shortcut in row) {
                                AppSquircleIcon(
                                    glyph = shortcut.glyph,
                                    colors = shortcut.colors,
                                    size = 54.dp,
                                    iconResName = shortcut.iconResName,
                                    onClick = { actions.open(shortcut, apps) }
                                )
                            }
                        }
                    }
                }
                if (pages.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.bounceClick { page = (page + 1) % pages.size }
                    ) {
                        for (i in pages.indices) {
                            Box(
                                Modifier
                                    .size(if (i == page) 14.dp else 6.dp, 6.dp)
                                    .clip(CircleShape)
                                    .background(if (i == page) Ultra.White else Color(0x40FFFFFF))
                            )
                        }
                    }
                }
            }
            BatteryRing(status)
        }
    }
}

@Composable
private fun BatteryRing(status: DeviceStatus) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 4.dp.toPx()
                val tl = Offset(sw / 2f, sw / 2f)
                val sz = Size(size.width - sw, size.height - sw)
                drawArc(Color(0x28FFFFFF), 0f, 360f, false, topLeft = tl, size = sz, style = Stroke(sw))
                status.batteryPct?.let { pct ->
                    drawArc(Ultra.Green, -90f, 360f * pct / 100f, false, topLeft = tl, size = sz, style = Stroke(sw, cap = StrokeCap.Round))
                }
            }
            Txt(status.batteryPct?.let { "$it%" } ?: "12V", 13.sp, FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        val label = when {
            status.batteryPct == null -> "Corriente"
            status.charging -> "Cargando"
            else -> "Batería"
        }
        Txt(label, 11.sp, FontWeight.Medium, Ultra.Secondary)
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
    modifier: Modifier
) {
    var volume by remember { mutableFloatStateOf(status.volume) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(status.volume) { if (!dragging) volume = status.volume }
    var brightness by rememberSaveable { mutableFloatStateOf(0.7f) }

    SpatialGlassSurface(modifier, cornerRadius = 28.dp) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassTube(
                value = volume,
                glyph = Glyph.Speaker,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                onDragState = { dragging = it }
            ) {
                volume = it
                actions.setVolume(it)
            }
            GlassTube(
                value = brightness,
                glyph = Glyph.Sun,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
            ) {
                brightness = it
                actions.setBrightness(it)
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
private fun GlassTube(
    value: Float,
    glyph: Glyph,
    modifier: Modifier,
    onDragState: (Boolean) -> Unit = {},
    onChange: (Float) -> Unit
) {
    val change by rememberUpdatedState(onChange)
    val dragState by rememberUpdatedState(onDragState)
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(Color(0x14FFFFFF))
            .pointerInput(Unit) {
                detectTapGestures { change((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragState(true)
                        change((it.x / size.width).coerceIn(0f, 1f))
                    },
                    onDragEnd = { dragState(false) },
                    onDragCancel = { dragState(false) }
                ) { pointer, _ ->
                    pointer.consume()
                    change((pointer.position.x / size.width).coerceIn(0f, 1f))
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
        GlyphIcon(
            glyph,
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 14.dp)
                .size(18.dp)
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
                columns = GridCells.Fixed(5),
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
                            Image(displayIcon, contentDescription = app.label, modifier = Modifier.size(56.dp))
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
