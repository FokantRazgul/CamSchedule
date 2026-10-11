package app.camplanner.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.theme.Atlas
import kotlin.math.abs

/** The astronomical sun symbol ☉, drawn: a ring with a centre point. */
fun DrawScope.drawSunGlyph(center: Offset, radius: Float, color: Color, ground: Color) {
    drawCircle(ground, radius + 1.5f, center)
    drawCircle(color, radius, center, style = Stroke(1.2f))
    drawCircle(color, radius * 0.28f, center)
}

/**
 * The moon as it actually looks tonight: [illumination] 0..1 lit, lit limb on the right while
 * waxing (as seen from the northern hemisphere).
 */
fun DrawScope.drawMoonGlyph(center: Offset, radius: Float, illumination: Float, waxing: Boolean, lit: Color, outline: Color) {
    drawCircle(outline, radius, center, style = Stroke(1f))
    val k = illumination.coerceIn(0f, 1f)
    if (k < 0.01f) return
    val disc = Rect(center, radius)
    val half = Path().apply {
        moveTo(center.x, center.y - radius)
        arcTo(disc, -90f, if (waxing) 180f else -180f, forceMoveTo = false)
        close()
    }
    val w = radius * abs(1f - 2f * k)
    val terminator = Path().apply { addOval(Rect(center.x - w, center.y - radius, center.x + w, center.y + radius)) }
    val shape = if (k < 0.5f) Path.combine(PathOperation.Difference, half, terminator)
    else Path.combine(PathOperation.Union, half, terminator)
    drawPath(shape, lit)
}

@Composable
fun SunGlyph(modifier: Modifier = Modifier, size: Dp = 12.dp) {
    val gold = Atlas.colors.gold
    val ground = Atlas.colors.background
    Canvas(modifier.size(size)) { drawSunGlyph(center, this.size.minDimension / 2f - 1.5f, gold, ground) }
}

@Composable
fun MoonGlyph(illumination: Float, waxing: Boolean, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    val lit = Atlas.colors.text
    val outline = Atlas.colors.textSecondary
    Canvas(modifier.size(size)) {
        drawMoonGlyph(center, this.size.minDimension / 2f - 1f, illumination, waxing, lit, outline)
    }
}
