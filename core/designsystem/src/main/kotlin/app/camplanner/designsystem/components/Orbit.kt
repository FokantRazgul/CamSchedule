package app.camplanner.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.designsystem.theme.AtlasMotion
import kotlin.math.cos
import kotlin.math.sin

/**
 * Progress as an orbit: a hairline circle with a thin arc travelling clockwise from the top, and
 * a small body sitting at the arc's leading edge. An optional [markFraction] adds a tick on the
 * orbit (the reading minimum, for example).
 */
@Composable
fun OrbitRing(
    progress: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 120.dp,
    color: Color = Atlas.colors.gold,
    trackColor: Color = Atlas.colors.hairline,
    arcWidth: Dp = 1.5.dp,
    markFraction: Float? = null,
    showBody: Boolean = true,
    graduations: Int = 0,
    majorEvery: Int = 5,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(AtlasMotion.SLOW_MS),
        label = "orbit",
    )
    val markColor = Atlas.colors.textSecondary
    val engraving = Atlas.colors.engraving
    val faint = Atlas.colors.hairline
    Box(
        modifier = modifier
            .size(diameter)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(diameter)) {
            val stroke = arcWidth.toPx()
            val bodyRadius = stroke * 1.9f
            // Graduated dials sit inside a scale of ticks, like an instrument bezel.
            val scale = if (graduations > 0) 9.dp.toPx() else 0f
            val inset = bodyRadius + 1f + scale
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            if (graduations > 0) {
                val r = (size.minDimension - inset * 2) / 2f
                drawCircle(engraving, r + scale, center, style = Stroke(1f))
                for (i in 0 until graduations) {
                    val major = i % majorEvery == 0
                    val a = Math.toRadians((-90.0 + 360.0 * i / graduations))
                    val dx = cos(a).toFloat()
                    val dy = sin(a).toFloat()
                    val from = r + scale - (if (major) 6.dp.toPx() else 3.5.dp.toPx())
                    drawLine(
                        if (major) engraving else faint,
                        Offset(center.x + from * dx, center.y + from * dy),
                        Offset(center.x + (r + scale) * dx, center.y + (r + scale) * dy),
                        strokeWidth = if (major) 1.2f else 1f,
                    )
                }
            }
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(width = 1f))
            if (markFraction != null) drawTick(markFraction, inset, markColor, stroke * 3f)
            if (animated > 0f) {
                drawArc(
                    color, -90f, 360f * animated, false, topLeft, arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                if (showBody) drawCircle(color, bodyRadius, pointOnOrbit(animated, inset))
            }
        }
        content()
    }
}

/** A small orbit for list rows (about the height of a line of text). */
@Composable
fun OrbitDot(
    progress: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 22.dp,
    color: Color = Atlas.colors.gold,
) = OrbitRing(progress, modifier, diameter, color, arcWidth = 1.5.dp, showBody = false)

/**
 * Horizontal variant: an arc segment of a very large orbit, read left to right. Used for the
 * weekly running distance, where a full ring would be too heavy.
 */
@Composable
fun OrbitArc(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Atlas.colors.gold,
    height: Dp = 28.dp,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(AtlasMotion.SLOW_MS), label = "arc")
    val track = Atlas.colors.hairline
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f) },
    ) {
        // A shallow arc: chord = width, sagitta = height * 0.6.
        val w = size.width
        val sag = size.height * 0.6f
        val r = (w * w / 4f + sag * sag) / (2f * sag)
        val center = Offset(w / 2f, sag + (r - sag) + size.height * 0.2f)
        val sweepTotal = Math.toDegrees(2.0 * kotlin.math.asin((w / 2f / r).toDouble())).toFloat()
        val start = -90f - sweepTotal / 2f
        val box = Size(r * 2, r * 2)
        val tl = Offset(center.x - r, center.y - r)
        drawArc(track, start, sweepTotal, false, tl, box, style = Stroke(1f))
        if (animated > 0f) {
            drawArc(color, start, sweepTotal * animated, false, tl, box, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
            val a = Math.toRadians((start + sweepTotal * animated).toDouble())
            drawCircle(color, 1.5.dp.toPx() * 1.9f, Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat()))
        }
    }
}

private fun DrawScope.pointOnOrbit(fraction: Float, inset: Float): Offset {
    val r = (size.minDimension - inset * 2) / 2f
    val angle = Math.toRadians((-90f + 360f * fraction).toDouble())
    return Offset(center.x + r * cos(angle).toFloat(), center.y + r * sin(angle).toFloat())
}

private fun DrawScope.drawTick(fraction: Float, inset: Float, color: Color, length: Float) {
    val r = (size.minDimension - inset * 2) / 2f
    val angle = Math.toRadians((-90f + 360f * fraction.coerceIn(0f, 1f)).toDouble())
    val dx = cos(angle).toFloat()
    val dy = sin(angle).toFloat()
    drawLine(
        color,
        Offset(center.x + (r - length / 2) * dx, center.y + (r - length / 2) * dy),
        Offset(center.x + (r + length / 2) * dx, center.y + (r + length / 2) * dy),
        strokeWidth = 1f,
    )
}
