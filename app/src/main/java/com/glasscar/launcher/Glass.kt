package com.glasscar.launcher

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

// =====================================================================
//  ULTRA SPATIAL AUTOMOTIVE GLASS v5 — Dual Theme (Dark & Light Glass)
// =====================================================================

object Ultra {
    val White = Color(0xFFFFFFFF)
    val Secondary = Color(0x99EBEBF5)
    val Electric = Color(0xFF0A84FF)
    val Cyan = Color(0xFF64D2FF)
    val Green = Color(0xFF30D158)
    val Yellow = Color(0xFFFFD60A)
    val Violet = Color(0xFFBF5AF2)
    val Magenta = Color(0xFFFF375F)
    val Amber = Color(0xFFFF9F0A)
    val Silver = Color(0xFFB0D0FF)
    val Abyss = Color(0xFF030407)

    // Colores de texto para Modo Claro (Tinta Espacial Profunda)
    val InkPrimary = Color(0xFF0B132B)
    val InkSecondary = Color(0x991C2541)

    val GlassFillDark = Color(0x1AFFFFFF)
    val GlassFillLight = Color(0x73FFFFFF) // 45% blanco escarchado en modo día
    val GlassSheen = Color(0x33FFFFFF)

    val ShadowSpotDark = Color(0x99000000)
    val ShadowAmbientDark = Color(0x66000000)
    val ShadowSpotLight = Color(0x2E0A2246)
    val ShadowAmbientLight = Color(0x1F0A2246)
}

/** Degradados de los squircles de color (arriba -> abajo). */
object Squircle {
    val Maps = Color(0xFF0A84FF) to Color(0xFF0051FF)
    val Music = Color(0xFFFF375F) to Color(0xFFFF2D55)
    val Phone = Color(0xFF30D158) to Color(0xFF248A3D)
    val Telemetry = Color(0xFF5E5CE6) to Color(0xFF3634A3)
    val Video = Color(0xFFFF453A) to Color(0xFFC91F16)
    val Settings = Color(0xFF8E8E93) to Color(0xFF48484A)
}

/** Paleta de tema completa (Modo Oscuro vs Modo Claro Real). */
data class SpacePalette(
    val night: Boolean,
    val abyss: Color,
    val midnight: Color,
    val aurora: Float,
    val textPrimary: Color,
    val textSecondary: Color,
    val glassFill: Color,
    val pillFill: Color,
    val trackFill: Color
)

val NightPalette = SpacePalette(
    night = true,
    abyss = Color(0xFF030407),
    midnight = Color(0xFF0A1128),
    aurora = 0.90f,
    textPrimary = Ultra.White,
    textSecondary = Ultra.Secondary,
    glassFill = Ultra.GlassFillDark,
    pillFill = Color(0x1FFFFFFF),
    trackFill = Color(0x22FFFFFF)
)

val DayPalette = SpacePalette(
    night = false,
    abyss = Color(0xFFDCE7F7),     // Blanco perlado / hielo azulado
    midnight = Color(0xFFB8D0F4),  // Cielo polar suave
    aurora = 0.65f,
    textPrimary = Ultra.InkPrimary,
    textSecondary = Ultra.InkSecondary,
    glassFill = Ultra.GlassFillLight,
    pillFill = Color(0x66FFFFFF),
    trackFill = Color(0x1F0B132B)
)

/** Borde especular en modo oscuro vs modo claro. */
val SpecularRimDark: Brush = Brush.linearGradient(
    0.00f to Color(0x99FFFFFF),
    0.35f to Color(0x14FFFFFF),
    0.75f to Color(0x08FFFFFF),
    1.00f to Color(0x38FFFFFF),
    start = Offset.Zero,
    end = Offset.Infinite
)

val SpecularRimLight: Brush = Brush.linearGradient(
    0.00f to Color(0xE6FFFFFF),
    0.40f to Color(0x59FFFFFF),
    0.75f to Color(0x260A84FF),
    1.00f to Color(0x99FFFFFF),
    start = Offset.Zero,
    end = Offset.Infinite
)

val SpecularRim: Brush get() = SpecularRimDark

// =====================================================================
//  Fondo: Aurora Mesh Wallpaper (Adaptativo Oscuro / Claro)
// =====================================================================

private fun DrawScope.aurora(color: Color, alpha: Float, center: Offset, radius: Float, area: Size) {
    drawRect(
        Brush.radialGradient(
            listOf(color.copy(alpha = alpha.coerceIn(0f, 1f)), color.copy(alpha = 0f)),
            center = center,
            radius = radius
        ),
        size = area
    )
}

fun DrawScope.drawSpaceBackground(p: SpacePalette, area: Size) {
    if (area.width <= 0f || area.height <= 0f) return
    val w = area.width
    val h = area.height
    drawRect(p.abyss, size = area)
    aurora(p.midnight, 1f, Offset(w * 0.10f, h * 0.05f), max(w, h) * 0.70f, area)
    if (p.night) {
        aurora(Ultra.Electric, 0.45f * p.aurora, Offset(w * 0.24f, h * 0.32f), w * 0.38f, area)
        aurora(Ultra.Violet, 0.40f * p.aurora, Offset(w * 0.70f, h * 0.18f), w * 0.35f, area)
        aurora(Ultra.Magenta, 0.40f * p.aurora, Offset(w * 0.90f, h * 0.40f), w * 0.26f, area)
        aurora(Ultra.Cyan, 0.30f * p.aurora, Offset(w * 0.52f, h * 1.02f), w * 0.40f, area)
    } else {
        // Auroras pastel luminosas para Modo Claro (Azul cielo, Lavanda solar, Rosa coral suave, Turquesa)
        aurora(Color(0xFF4CA6FF), 0.42f * p.aurora, Offset(w * 0.24f, h * 0.32f), w * 0.40f, area)
        aurora(Color(0xFFA77BFF), 0.35f * p.aurora, Offset(w * 0.70f, h * 0.18f), w * 0.36f, area)
        aurora(Color(0xFFFF6B8B), 0.32f * p.aurora, Offset(w * 0.90f, h * 0.40f), w * 0.28f, area)
        aurora(Color(0xFF38C6F4), 0.35f * p.aurora, Offset(w * 0.52f, h * 1.02f), w * 0.42f, area)
    }
}

data class Backdrop(val size: Size = Size.Zero, val palette: SpacePalette = NightPalette)

val LocalBackdrop = compositionLocalOf { Backdrop() }

// =====================================================================
//  SpatialGlassSurface
// =====================================================================

@Composable
fun SpatialGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 32.dp,
    elevation: Dp = 24.dp,
    glow: Color? = null,
    glowCenter: Offset = Offset(0.18f, 0.35f),
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val backdrop = LocalBackdrop.current
    val p = backdrop.palette
    var origin by remember { mutableStateOf(Offset.Zero) }
    val rim = if (p.night) SpecularRimDark else SpecularRimLight
    val ambShadow = if (p.night) Ultra.ShadowAmbientDark else Ultra.ShadowAmbientLight
    val spotShadow = if (p.night) Ultra.ShadowSpotDark else Ultra.ShadowSpotLight

    Box(
        modifier
            .shadow(elevation, shape, clip = false, ambientColor = ambShadow, spotColor = spotShadow)
            .clip(shape)
            .onGloballyPositioned { origin = it.positionInRoot() }
    ) {
        Canvas(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = BlurEffect(40f, 40f, TileMode.Clamp)
                    }
                    clip = true
                }
        ) {
            translate(-origin.x, -origin.y) {
                drawSpaceBackground(p, backdrop.size)
            }
            if (glow != null) {
                val glowAlpha = if (p.night) 0.32f else 0.22f
                drawRect(
                    Brush.radialGradient(
                        listOf(glow.copy(alpha = glowAlpha), glow.copy(alpha = 0f)),
                        center = Offset(size.width * glowCenter.x, size.height * glowCenter.y),
                        radius = size.maxDimension * 0.58f
                    )
                )
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .drawBehind { drawGlassLayers(cornerRadius.toPx(), p.glassFill, if (p.night) 1f else 1.6f) }
                .border(1.5.dp, rim, shape)
        )
        content()
    }
}

private fun DrawScope.drawGlassLayers(radiusPx: Float, fill: Color, sheenStrength: Float) {
    drawRect(fill)
    drawRect(
        Brush.linearGradient(
            listOf(Ultra.GlassSheen.copy(alpha = (0.20f * sheenStrength).coerceAtMost(0.65f)), Color.White.copy(alpha = 0f)),
            start = Offset.Zero,
            end = Offset(size.width * 0.6f, size.height * 0.6f)
        )
    )
    val inset = radiusPx * 0.75f
    if (size.width > inset * 2f) {
        val y = 1.6.dp.toPx()
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(Color.White.copy(alpha = 0f), Color(0x66FFFFFF), Color.White.copy(alpha = 0f)),
                startX = inset,
                endX = size.width - inset
            ),
            start = Offset(inset, y),
            end = Offset(size.width - inset, y),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

// =====================================================================
//  Modificadores
// =====================================================================

fun Modifier.spatialGlass(
    shape: Shape,
    fill: Color? = null,
    borderWidth: Dp = 1.2.dp
): Modifier = composed {
    val p = LocalBackdrop.current.palette
    val actualFill = fill ?: p.pillFill
    val rim = if (p.night) SpecularRimDark else SpecularRimLight
    this
        .clip(shape)
        .drawBehind { drawGlassLayers(minOf(size.width, size.height) / 2f, actualFill, if (p.night) 1.2f else 1.8f) }
        .border(borderWidth, rim, shape)
}

fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bounce"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

// =====================================================================
//  AppSquircleIcon
// =====================================================================

@SuppressLint("DiscouragedApi")
@Composable
fun AppSquircleIcon(
    glyph: Glyph,
    colors: Pair<Color, Color>,
    size: Dp,
    modifier: Modifier = Modifier,
    radius: Dp = 14.dp,
    iconResName: String? = null,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isNight = LocalBackdrop.current.palette.night
    val resId = remember(iconResName, isNight) {
        if (iconResName != null) {
            val themedName = if (isNight) "${iconResName}_dark" else "${iconResName}_light"
            val themedId = context.resources.getIdentifier(themedName, "drawable", context.packageName)
            if (themedId != 0) themedId else context.resources.getIdentifier(iconResName, "drawable", context.packageName)
        } else 0
    }

    if (resId != 0) {
        Box(
            modifier
                .size(size * 1.14f)
                .then(if (onClick != null) Modifier.bounceClick(onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = resId),
                contentDescription = iconResName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        return
    }

    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .size(size)
            .then(if (onClick != null) Modifier.bounceClick(onClick = onClick) else Modifier)
            .shadow(8.dp, shape, ambientColor = Ultra.ShadowAmbientDark, spotColor = Ultra.ShadowSpotDark)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(colors.first, colors.second)))
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0x4DFFFFFF),
                        0.45f to Color.White.copy(alpha = 0f)
                    )
                )
            }
            .border(
                1.dp,
                Brush.linearGradient(
                    0f to Color(0xD9FFFFFF),
                    0.4f to Color(0x26FFFFFF),
                    1f to Color(0x40FFFFFF),
                    start = Offset.Zero,
                    end = Offset.Infinite
                ),
                shape
            ),
        contentAlignment = Alignment.Center
    ) {
        val glyphSize = size * 0.48f
        GlyphIcon(glyph, Modifier.size(glyphSize).offset(y = 1.5.dp), tint = Color(0x59000000))
        GlyphIcon(glyph, Modifier.size(glyphSize), tint = Ultra.White)
    }
}

@Composable
fun SpaceBox(modifier: Modifier = Modifier, palette: SpacePalette = NightPalette, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().drawBehind { drawSpaceBackground(palette, size) }, content = content)
}
