package com.glasscar.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/** Set de íconos propio del sistema Ultra (cuadrícula de 24 unidades). */
enum class Glyph {
    Grid, Play, Pause, Prev, Next, Wifi, Bluetooth, Navigation, Speaker, Sun, Moon,
    Map, Note, Phone, Gauge, Gear, Camera, Plus, Mountain, Screen
}

@Composable
fun GlyphIcon(glyph: Glyph, modifier: Modifier = Modifier, tint: Color = Ultra.White) {
    Canvas(modifier) { drawGlyph(glyph, SolidColor(tint)) }
}

fun DrawScope.drawGlyph(glyph: Glyph, brush: Brush) {
    val s = size.minDimension / 24f
    val ox = (size.width - 24f * s) / 2f
    val oy = (size.height - 24f * s) / 2f
    fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    fun poly(vararg pts: Offset, closed: Boolean = true) = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        if (closed) close()
    }
    val stroke = Stroke(width = 2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (glyph) {
        Glyph.Grid -> for (r in 0..2) for (c in 0..2) {
            drawCircle(brush, radius = 2f * s, center = p(5f + c * 7f, 5f + r * 7f))
        }

        Glyph.Play -> drawPath(poly(p(7f, 4f), p(20f, 12f), p(7f, 20f)), brush)

        Glyph.Pause -> {
            drawRoundRect(brush, topLeft = p(6f, 4f), size = Size(4f * s, 16f * s), cornerRadius = CornerRadius(1.5f * s))
            drawRoundRect(brush, topLeft = p(14f, 4f), size = Size(4f * s, 16f * s), cornerRadius = CornerRadius(1.5f * s))
        }

        Glyph.Prev -> {
            drawPath(poly(p(19f, 5f), p(19f, 19f), p(8f, 12f)), brush)
            drawRoundRect(brush, topLeft = p(5f, 5f), size = Size(2.5f * s, 14f * s), cornerRadius = CornerRadius(1f * s))
        }

        Glyph.Next -> {
            drawPath(poly(p(5f, 5f), p(5f, 19f), p(16f, 12f)), brush)
            drawRoundRect(brush, topLeft = p(16.5f, 5f), size = Size(2.5f * s, 14f * s), cornerRadius = CornerRadius(1f * s))
        }

        Glyph.Wifi -> {
            for (r in listOf(6f, 10f, 14f)) {
                drawArc(
                    brush, startAngle = 225f, sweepAngle = 90f, useCenter = false,
                    topLeft = p(12f - r, 20f - r), size = Size(2f * r * s, 2f * r * s), style = stroke
                )
            }
            drawCircle(brush, radius = 1.8f * s, center = p(12f, 20f))
        }

        Glyph.Bluetooth -> drawPath(
            poly(p(7f, 7f), p(17f, 17f), p(12f, 22f), p(12f, 2f), p(17f, 7f), p(7f, 17f), closed = false),
            brush, style = stroke
        )

        Glyph.Navigation -> drawPath(poly(p(12f, 2f), p(20f, 21f), p(12f, 17f), p(4f, 21f)), brush)

        Glyph.Speaker -> {
            drawPath(poly(p(3f, 9f), p(7f, 9f), p(12f, 5f), p(12f, 19f), p(7f, 15f), p(3f, 15f)), brush)
            drawArc(brush, startAngle = -45f, sweepAngle = 90f, useCenter = false,
                topLeft = p(10f, 8f), size = Size(8f * s, 8f * s), style = stroke)
            drawArc(brush, startAngle = -45f, sweepAngle = 90f, useCenter = false,
                topLeft = p(7f, 5f), size = Size(14f * s, 14f * s), style = stroke)
        }

        Glyph.Sun -> {
            drawCircle(brush, radius = 4.5f * s, center = p(12f, 12f))
            for (k in 0 until 8) {
                val a = Math.toRadians(k * 45.0)
                val c = cos(a).toFloat()
                val sn = sin(a).toFloat()
                drawLine(brush, p(12f + c * 7.5f, 12f + sn * 7.5f), p(12f + c * 10f, 12f + sn * 10f),
                    strokeWidth = 2f * s, cap = StrokeCap.Round)
            }
        }

        Glyph.Moon -> {
            val disc = Path().apply { addOval(Rect(p(12f, 12f), 8.5f * s)) }
            val bite = Path().apply { addOval(Rect(p(17f, 8f), 7.5f * s)) }
            val moon = Path().apply { op(disc, bite, PathOperation.Difference) }
            drawPath(moon, brush)
        }

        Glyph.Map -> {
            drawPath(
                poly(p(3f, 6f), p(9f, 3f), p(15f, 6f), p(21f, 3f), p(21f, 18f), p(15f, 21f), p(9f, 18f), p(3f, 21f)),
                brush, style = stroke
            )
            drawLine(brush, p(9f, 3f), p(9f, 18f), strokeWidth = 2f * s, cap = StrokeCap.Round)
            drawLine(brush, p(15f, 6f), p(15f, 21f), strokeWidth = 2f * s, cap = StrokeCap.Round)
        }

        Glyph.Note -> {
            drawCircle(brush, radius = 3f * s, center = p(7f, 18f))
            drawCircle(brush, radius = 3f * s, center = p(17f, 16f))
            drawPath(poly(p(10f, 18f), p(10f, 5f), p(20f, 3f), p(20f, 16f), closed = false), brush, style = stroke)
        }

        Glyph.Phone -> {
            drawRoundRect(brush, topLeft = p(6.5f, 2f), size = Size(11f * s, 20f * s),
                cornerRadius = CornerRadius(3f * s), style = stroke)
            drawLine(brush, p(10.5f, 18.5f), p(13.5f, 18.5f), strokeWidth = 2f * s, cap = StrokeCap.Round)
        }

        Glyph.Gauge -> {
            drawArc(brush, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = p(3f, 6f), size = Size(18f * s, 18f * s), style = stroke)
            drawLine(brush, p(12f, 15f), p(17f, 9.5f), strokeWidth = 2.2f * s, cap = StrokeCap.Round)
            drawCircle(brush, radius = 2f * s, center = p(12f, 15f))
        }

        Glyph.Gear -> {
            drawCircle(brush, radius = 6.5f * s, center = p(12f, 12f), style = stroke)
            drawCircle(brush, radius = 2.6f * s, center = p(12f, 12f), style = stroke)
            for (k in 0 until 8) {
                val a = Math.toRadians(k * 45.0 + 22.5)
                val c = cos(a).toFloat()
                val sn = sin(a).toFloat()
                drawLine(brush, p(12f + c * 7.5f, 12f + sn * 7.5f), p(12f + c * 10f, 12f + sn * 10f),
                    strokeWidth = 2.6f * s, cap = StrokeCap.Round)
            }
        }

        Glyph.Camera -> {
            drawRoundRect(brush, topLeft = p(2.5f, 7f), size = Size(19f * s, 13f * s),
                cornerRadius = CornerRadius(3f * s), style = stroke)
            drawPath(poly(p(8f, 7f), p(9.5f, 4f), p(14.5f, 4f), p(16f, 7f), closed = false), brush, style = stroke)
            drawCircle(brush, radius = 3.5f * s, center = p(12f, 13.5f), style = stroke)
        }

        Glyph.Plus -> {
            drawLine(brush, p(12f, 5f), p(12f, 19f), strokeWidth = 2f * s, cap = StrokeCap.Round)
            drawLine(brush, p(5f, 12f), p(19f, 12f), strokeWidth = 2f * s, cap = StrokeCap.Round)
        }

        Glyph.Screen -> {
            drawRoundRect(brush, topLeft = p(2.5f, 4f), size = Size(19f * s, 13f * s),
                cornerRadius = CornerRadius(2.5f * s), style = stroke)
            drawLine(brush, p(12f, 17f), p(12f, 21f), strokeWidth = 2f * s, cap = StrokeCap.Round)
            drawLine(brush, p(8f, 21f), p(16f, 21f), strokeWidth = 2f * s, cap = StrokeCap.Round)
        }

        Glyph.Mountain -> drawPath(
            poly(p(2f, 20f), p(9f, 7f), p(13f, 13f), p(16f, 9f), p(22f, 20f)),
            brush, style = stroke
        )
    }
}

@Composable
fun WeatherGlyph(code: Int?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        val sunBrush = Brush.linearGradient(
            listOf(Ultra.Yellow, Color(0xFFFF9F0A)),
            start = Offset.Zero, end = Offset(size.width, size.height)
        )
        val cloudBrush = Brush.verticalGradient(listOf(Color(0xFFEAF7FF), Ultra.Cyan))
        val c = code ?: 0
        val cloudy = c != 0
        val sunny = c <= 3
        val rain = c >= 51

        if (sunny) {
            val center = if (cloudy) Offset(9f * s, 8.5f * s) else Offset(12f * s, 12f * s)
            val r = if (cloudy) 4.2f * s else 5.5f * s
            drawCircle(sunBrush, radius = r, center = center)
            for (k in 0 until 8) {
                val a = Math.toRadians(k * 45.0)
                val cx = cos(a).toFloat()
                val sy = sin(a).toFloat()
                drawLine(sunBrush, center + Offset(cx, sy) * (r + 1.8f * s), center + Offset(cx, sy) * (r + 3.6f * s),
                    strokeWidth = 1.8f * s, cap = StrokeCap.Round)
            }
        }
        if (cloudy) {
            drawCircle(cloudBrush, radius = 4.5f * s, center = Offset(10f * s, 14f * s))
            drawCircle(cloudBrush, radius = 5.5f * s, center = Offset(15f * s, 12.5f * s))
            drawRoundRect(cloudBrush, topLeft = Offset(5.5f * s, 13f * s), size = Size(15f * s, 5.5f * s),
                cornerRadius = CornerRadius(2.75f * s))
        }
        if (rain) {
            for (i in 0..2) {
                drawLine(Ultra.Cyan, Offset((9f + i * 4f) * s, 20f * s), Offset((8f + i * 4f) * s, 23f * s),
                    strokeWidth = 1.6f * s, cap = StrokeCap.Round)
            }
        }
    }
}
