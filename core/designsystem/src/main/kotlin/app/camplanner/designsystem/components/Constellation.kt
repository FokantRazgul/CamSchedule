package app.camplanner.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.theme.Atlas
import kotlin.math.abs
import kotlin.math.sqrt

enum class StarState { PAST, CURRENT, FUTURE }

/** What the chart column shows beside one row. */
@Immutable
sealed interface ChartPoint {
    /** An event. [magnitude] in 0..1 sets the star's size (longer events read as brighter stars). */
    data class Star(val state: StarState, val magnitude: Float = 0.5f, val seed: Int = 0) : ChartPoint

    /** The present moment: a short wine tick across the meridian. */
    data object Now : ChartPoint

    /** A row with nothing on the chart (free time, notes); lines pass straight by. */
    data object None : ChartPoint
}

/**
 * The day as a constellation. Each row gets a point in a narrow chart column on the left: a thin
 * meridian runs down the column, events sit beside it as stars, and consecutive stars are joined
 * like a figure on a star chart, with the lines stopping just short of each star as engravers drew
 * them. The current event is gold with a fine halo ring (a drawn ring, not a blur).
 *
 * @param anchor distance from the top of a row to its star; align it with the row's first line
 * @param leading optional left column (start times), [leadingWidth] wide
 */
@Composable
fun <T> Constellation(
    items: List<T>,
    point: (T) -> ChartPoint,
    modifier: Modifier = Modifier,
    anchor: Dp = 13.dp,
    leadingWidth: Dp = Atlas.space.timeColumn,
    chartWidth: Dp = Atlas.space.chartColumn,
    leading: @Composable (T) -> Unit = {},
    rowContent: @Composable (T) -> Unit,
) {
    val tops = remember(items.size) { mutableStateMapOf<Int, Float>() }
    val c = Atlas.colors
    val palette = ChartColors(
        meridian = c.hairline,
        line = c.text.copy(alpha = 0.32f),
        pastLine = c.textSecondary.copy(alpha = 0.28f),
        star = c.text,
        pastStar = c.textSecondary.copy(alpha = 0.7f),
        current = c.gold,
        now = c.accent,
    )
    Column(
        modifier.drawBehind {
            drawChart(items.map(point), tops, anchor.toPx(), leadingWidth.toPx() + chartWidth.toPx() / 2f, palette)
        },
    ) {
        items.forEachIndexed { index, item ->
            Row(Modifier.fillMaxWidth().onPlaced { tops[index] = it.positionInParent().y }) {
                Box(Modifier.width(leadingWidth)) { leading(item) }
                Spacer(Modifier.width(chartWidth))
                Box(Modifier.weight(1f)) { rowContent(item) }
            }
        }
    }
}

private class ChartColors(
    val meridian: Color,
    val line: Color,
    val pastLine: Color,
    val star: Color,
    val pastStar: Color,
    val current: Color,
    val now: Color,
)

private class Placed(val at: Offset, val radius: Float, val state: StarState)

private fun DrawScope.drawChart(
    points: List<ChartPoint>,
    tops: Map<Int, Float>,
    anchor: Float,
    meridianX: Float,
    colors: ChartColors,
) {
    if (tops.size < points.size || points.isEmpty()) return
    val ys = points.indices.map { tops.getValue(it) + anchor }

    // Meridian: from just above the first row to just below the last.
    val overhang = 18.dp.toPx()
    drawLine(colors.meridian, Offset(meridianX, ys.first() - overhang), Offset(meridianX, ys.last() + overhang), 1f)

    var starIndex = 0
    val stars = points.mapIndexedNotNull { i, p ->
        if (p !is ChartPoint.Star) return@mapIndexedNotNull null
        // Alternate sides of the meridian with a small, stable jitter so the figure zigzags.
        val side = if (starIndex++ % 2 == 0) -1f else 1f
        val jitter = (3 + abs(p.seed) % 5).dp.toPx()
        val radius = (2.2f + 2.4f * p.magnitude.coerceIn(0f, 1f)).dp.toPx()
        Placed(Offset(meridianX + side * jitter, ys[i]), radius, p.state)
    }

    val gap = 3.dp.toPx()
    stars.zipWithNext { a, b ->
        val dx = b.at.x - a.at.x
        val dy = b.at.y - a.at.y
        val len = sqrt(dx * dx + dy * dy)
        if (len <= a.radius + b.radius + gap * 2) return@zipWithNext
        val ux = dx / len
        val uy = dy / len
        val from = Offset(a.at.x + ux * (a.radius + gap), a.at.y + uy * (a.radius + gap))
        val to = Offset(b.at.x - ux * (b.radius + gap), b.at.y - uy * (b.radius + gap))
        val color = if (b.state == StarState.PAST) colors.pastLine else colors.line
        drawLine(color, from, to, strokeWidth = 1f)
    }

    points.forEachIndexed { i, p ->
        if (p is ChartPoint.Now) {
            val half = 7.dp.toPx()
            drawLine(colors.now, Offset(meridianX - half, ys[i]), Offset(meridianX + half, ys[i]), 1.5.dp.toPx())
        }
    }

    stars.forEach { s ->
        when (s.state) {
            StarState.PAST -> drawChartStar(s.at, s.radius, colors.pastStar)
            StarState.FUTURE -> drawChartStar(s.at, s.radius, colors.star)
            StarState.CURRENT -> {
                val r = s.radius * 1.3f
                drawCircle(colors.current.copy(alpha = 0.5f), r * 2.3f, s.at, style = Stroke(1f))
                drawChartStar(s.at, r, colors.current)
            }
        }
    }
}
