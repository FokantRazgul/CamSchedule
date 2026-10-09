package app.camplanner.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.theme.Atlas
import kotlin.math.abs

/** Thick-and-thin rule, as under the masthead of a printed almanac. */
@Composable
fun DoubleRule(modifier: Modifier = Modifier, color: Color = Atlas.colors.engraving) {
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        val thick = 1.5.dp.toPx()
        drawLine(color, Offset(0f, thick / 2), Offset(size.width, thick / 2), thick)
        val y = size.height - 0.5f
        drawLine(color, Offset(0f, y), Offset(size.width, y), 1f)
    }
}

/**
 * Masthead: a running head in spaced capitals (left and right), then a double rule.
 *
 *     CAM PLANNER                           52°12′ N · 0°07′ E
 *     ══════════════════════════════════════════════════════
 */
@Composable
fun Masthead(left: String, right: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(left.uppercase(), style = Atlas.type.overline, color = Atlas.colors.textSecondary, modifier = Modifier.weight(1f))
            if (right != null) Text(right.uppercase(), style = Atlas.type.overline, color = Atlas.colors.textSecondary)
        }
        Spacer(Modifier.height(Atlas.space.s))
        DoubleRule()
    }
}

/**
 * Numbered section title with a rubricated Roman numeral and an italic serif name, closed by a
 * hairline.
 *
 *     II.  Homework                                 next 3 days
 *     ──────────────────────────────────────────────────────
 */
@Composable
fun SectionTitle(
    numeral: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(top = Atlas.space.s, bottom = Atlas.space.xs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("$numeral.", style = Atlas.type.rubric, color = Atlas.colors.rubric, modifier = Modifier.padding(bottom = 3.dp))
            Spacer(Modifier.width(Atlas.space.m))
            Text(title, style = Atlas.type.sectionTitle, color = Atlas.colors.text, modifier = Modifier.weight(1f))
            if (trailing != null) Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
        Spacer(Modifier.height(Atlas.space.s))
        Hairline()
    }
}

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

/**
 * One line of almanac: sunrise, sunset and the moon, each with its symbol.
 *
 *     ☉ Rises 07:29 · sets 17:53        ◐ Waxing gibbous
 */
@Composable
fun AlmanacLine(
    sunrise: String?,
    sunset: String?,
    moonName: String,
    moonIllumination: Float,
    moonWaxing: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Atlas.colors
    Row(
        modifier.fillMaxWidth().semantics { contentDescription = "Sunrise $sunrise, sunset $sunset, $moonName" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (sunrise != null && sunset != null) {
            SunGlyph()
            Text("Rises $sunrise  ·  sets $sunset", style = Atlas.type.time, color = c.textSecondary)
        }
        Spacer(Modifier.weight(1f))
        MoonGlyph(moonIllumination, moonWaxing)
        Text(moonName, style = Atlas.type.aside.copy(fontSize = Atlas.type.body.fontSize), color = c.textSecondary)
    }
}
