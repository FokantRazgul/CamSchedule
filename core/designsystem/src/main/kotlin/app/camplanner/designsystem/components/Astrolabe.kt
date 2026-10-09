package app.camplanner.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.theme.Atlas
import kotlin.math.cos
import kotlin.math.sin

/** One engagement on the dial, in minutes after local midnight. */
@Immutable
data class DialArc(val startMinute: Int, val endMinute: Int, val color: Color, val state: StarState)

/**
 * The day drawn as an astrolabe plate: a 24-hour dial with midnight at the foot and noon at the
 * crown, so the sun climbs over the top like the real sky. The lit sector runs from sunrise to
 * sunset with a horizon chord between them, engagements sit on an inner band in their subject
 * inks, and a gold hand marks the present.
 *
 * All line work is engraved hairline; the only fills are the faint daylight plate and the arcs.
 */
@Composable
fun Astrolabe(
    arcs: List<DialArc>,
    modifier: Modifier = Modifier,
    nowMinute: Int? = null,
    sunriseMinute: Int? = null,
    sunsetMinute: Int? = null,
    diameter: Dp = 320.dp,
    description: String = "Dial of the day",
    center: @Composable BoxScope.() -> Unit = {},
) {
    val c = Atlas.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = Atlas.type.time.copy(fontSize = Atlas.type.overline.fontSize, color = c.textSecondary)
    Box(modifier.size(diameter).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val plate = PlateColors(
                engraving = c.engraving, faint = c.hairline, daylight = c.daylight, gold = c.gold,
                ground = c.background, labelStyle = labelStyle,
            )
            drawPlate(arcs, nowMinute, sunriseMinute, sunsetMinute, plate, measurer)
        }
        Box(Modifier.size(diameter * 0.5f), contentAlignment = Alignment.Center, content = center)
    }
}

private class PlateColors(
    val engraving: Color,
    val faint: Color,
    val daylight: Color,
    val gold: Color,
    val ground: Color,
    val labelStyle: androidx.compose.ui.text.TextStyle,
)

/** Compose angle (0° = 3 o'clock, clockwise) for a minute of the day, midnight at the bottom. */
private fun angleOf(minute: Float): Float = 90f + minute / 4f

private fun DrawScope.polar(radius: Float, angleDeg: Float): Offset {
    val a = Math.toRadians(angleDeg.toDouble())
    return Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat())
}

private fun DrawScope.drawPlate(
    arcs: List<DialArc>,
    nowMinute: Int?,
    sunrise: Int?,
    sunset: Int?,
    p: PlateColors,
    measurer: TextMeasurer,
) {
    val outer = size.minDimension / 2f - 7.dp.toPx()
    val rim = outer - 3.dp.toPx()
    val band = rim - 38.dp.toPx()
    val inner = band - 12.dp.toPx()

    // Daylight plate and horizon chord.
    if (sunrise != null && sunset != null && sunset > sunrise) {
        val start = angleOf(sunrise.toFloat())
        val sweep = (sunset - sunrise) / 4f
        // Filled without the centre, the arc closes along its chord: the sky above the horizon.
        drawArc(p.daylight, start, sweep, useCenter = false, topLeft = Offset(center.x - rim, center.y - rim), size = Size(rim * 2, rim * 2))
        drawLine(p.engraving, polar(rim, start), polar(rim, start + sweep), 1f)
    }

    // Rim: two engraved circles.
    drawCircle(p.engraving, outer, center, style = Stroke(1f))
    drawCircle(p.engraving, rim, center, style = Stroke(1f))

    // Graduations: every quarter hour, longer each hour, longest every three hours.
    for (q in 0 until 96) {
        val angle = angleOf(q * 15f)
        val (len, width) = when {
            q % 12 == 0 -> 11.dp.toPx() to 1.3f
            q % 4 == 0 -> 6.dp.toPx() to 1f
            else -> 3.dp.toPx() to 1f
        }
        drawLine(if (q % 4 == 0) p.engraving else p.faint, polar(rim, angle), polar(rim - len, angle), width)
    }

    // Hour numerals every three hours, upright.
    for (h in 0 until 24 step 3) {
        val label = measurer.measure("%02d".format(h), p.labelStyle)
        val at = polar(rim - 22.dp.toPx(), angleOf(h * 60f))
        drawText(label, topLeft = Offset(at.x - label.size.width / 2f, at.y - label.size.height / 2f))
    }

    // Engagement band.
    drawCircle(p.faint, band, center, style = Stroke(1f))
    drawCircle(p.engraving, inner, center, style = Stroke(1f))
    val bandBox = Offset(center.x - band, center.y - band)
    val bandSize = Size(band * 2, band * 2)
    val arcWidth = 5.dp.toPx()
    arcs.forEach { arc ->
        val start = angleOf(arc.startMinute.toFloat())
        val sweep = ((arc.endMinute - arc.startMinute) / 4f).coerceAtLeast(1.5f)
        val color = when (arc.state) {
            StarState.PAST -> arc.color.copy(alpha = 0.4f)
            StarState.CURRENT -> p.gold
            StarState.FUTURE -> arc.color
        }
        // A hair of space either side so adjacent engagements read as separate.
        drawArc(color, start + 0.6f, (sweep - 1.2f).coerceAtLeast(0.8f), false, bandBox, bandSize, style = Stroke(arcWidth, cap = StrokeCap.Butt))
    }

    // Sun at rising and setting, set into the outer rim.
    val sunR = 4.dp.toPx()
    if (sunrise != null) drawSunGlyph(polar((outer + rim) / 2f, angleOf(sunrise.toFloat())), sunR, p.gold, p.ground)
    if (sunset != null) drawSunGlyph(polar((outer + rim) / 2f, angleOf(sunset.toFloat())), sunR, p.gold, p.ground)

    // The present: a gold hand from the inner circle to the rim, tipped with a star.
    if (nowMinute != null) {
        val a = angleOf(nowMinute.toFloat())
        drawLine(p.gold, polar(inner, a), polar(rim - 2.dp.toPx(), a), 1.25.dp.toPx(), cap = StrokeCap.Round)
        drawChartStar(polar(outer + 0.5f, a), 5.dp.toPx(), p.gold)
    }
}
