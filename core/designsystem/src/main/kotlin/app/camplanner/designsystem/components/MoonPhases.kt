package app.camplanner.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.theme.Atlas

/** How a day reads in a streak row. */
enum class MoonPhase {
    /** Target met: full moon, gold. */
    FULL,

    /** Target missed: new moon, a faint outline. */
    NEW,

    /** Target didn't apply (rest day): half moon in the secondary ink. */
    HALF,

    /** Today, still open: a waxing crescent. */
    CRESCENT,
}

@Composable
fun Moon(phase: MoonPhase, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    val gold = Atlas.colors.gold
    val muted = Atlas.colors.textSecondary
    val faint = Atlas.colors.hairline
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f - 1f
        when (phase) {
            MoonPhase.FULL -> drawCircle(gold, r)
            MoonPhase.NEW -> drawCircle(muted.copy(alpha = 0.55f), r, style = Stroke(1f))
            MoonPhase.HALF -> {
                drawCircle(faint, r, style = Stroke(1f))
                drawArc(muted, -90f, 180f, useCenter = true, topLeft = Offset(center.x - r, center.y - r),
                    size = androidx.compose.ui.geometry.Size(r * 2, r * 2))
            }
            MoonPhase.CRESCENT -> {
                drawCircle(muted.copy(alpha = 0.55f), r, style = Stroke(1f))
                drawCrescent(gold, r)
            }
        }
    }
}

private fun DrawScope.drawCrescent(color: Color, r: Float) {
    val disc = Path().apply { addOval(Rect(center, r)) }
    val shadow = Path().apply { addOval(Rect(Offset(center.x - r * 0.55f, center.y), r)) }
    drawPath(Path.combine(PathOperation.Difference, disc, shadow), color)
}

/**
 * The last N days as moon phases, oldest first. Reads like the phase strip along the bottom of
 * an almanac page.
 */
@Composable
fun MoonPhaseRow(
    phases: List<MoonPhase>,
    modifier: Modifier = Modifier,
    moonSize: Dp = 12.dp,
    description: String? = null,
) {
    Row(
        modifier = modifier.semantics {
            contentDescription = description ?: "${phases.count { it == MoonPhase.FULL }} of ${phases.size} days met"
        },
        horizontalArrangement = Arrangement.spacedBy(moonSize * 0.55f),
    ) {
        phases.forEach { Moon(it, size = moonSize) }
    }
}
