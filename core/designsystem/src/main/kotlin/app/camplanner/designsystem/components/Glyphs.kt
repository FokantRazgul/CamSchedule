package app.camplanner.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.theme.Atlas

/**
 * A four-pointed chart star, drawn the way engravers marked stars on old celestial maps.
 * [waist] sets how pinched the points are (0 = needle-thin, 1 = a diamond).
 */
fun DrawScope.drawChartStar(center: Offset, radius: Float, color: Color, waist: Float = 0.28f) {
    val inner = radius * waist
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x + inner * 0.35f, center.y - inner * 0.35f, center.x + radius, center.y)
        quadraticTo(center.x + inner * 0.35f, center.y + inner * 0.35f, center.x, center.y + radius)
        quadraticTo(center.x - inner * 0.35f, center.y + inner * 0.35f, center.x - radius, center.y)
        quadraticTo(center.x - inner * 0.35f, center.y - inner * 0.35f, center.x, center.y - radius)
        close()
    }
    drawPath(path, color)
}

@Composable
fun ChartStar(modifier: Modifier = Modifier, size: Dp = 10.dp, color: Color = Atlas.colors.gold) {
    Canvas(modifier.size(size)) { drawChartStar(center, this.size.minDimension / 2f, color) }
}

/**
 * Hairline glyphs drawn for this app (24-unit grid, 1.25 stroke). Kept deliberately few: most
 * actions are labelled with words.
 */
object Glyphs {
    private fun glyph(name: String, block: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.stroke(pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        path(
            stroke = SolidColor(Color.White), strokeLineWidth = 1.25f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = pathBuilder,
        )

    /** Two stations joined by a dotted course, like a route on a chart. */
    val Route: ImageVector by lazy {
        glyph("Route") {
            stroke { moveTo(5f, 19f); curveTo(5f, 12f, 19f, 13f, 19f, 5f) }
            path(fill = SolidColor(Color.White)) {
                moveTo(5f, 17f); arcToRelative(2f, 2f, 0f, true, true, 0f, 4f); arcToRelative(2f, 2f, 0f, true, true, 0f, -4f); close()
            }
            stroke { moveTo(19f, 3f); arcToRelative(2f, 2f, 0f, true, true, 0f, 4f); arcToRelative(2f, 2f, 0f, true, true, 0f, -4f); close() }
        }
    }

    val Plus: ImageVector by lazy {
        glyph("Plus") { stroke { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) } }
    }

    val Check: ImageVector by lazy {
        glyph("Check") { stroke { moveTo(5f, 12.5f); lineTo(10f, 17f); lineTo(19f, 7f) } }
    }

    val Close: ImageVector by lazy {
        glyph("Close") { stroke { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) } }
    }

    val ChevronRight: ImageVector by lazy {
        glyph("ChevronRight") { stroke { moveTo(10f, 6f); lineTo(16f, 12f); lineTo(10f, 18f) } }
    }

    val ChevronLeft: ImageVector by lazy {
        glyph("ChevronLeft") { stroke { moveTo(14f, 6f); lineTo(8f, 12f); lineTo(14f, 18f) } }
    }

    /** A bell reduced to an arc and a clapper. */
    val Bell: ImageVector by lazy {
        glyph("Bell") {
            stroke {
                moveTo(6f, 16f); lineTo(6f, 11f); curveTo(6f, 7.5f, 8.7f, 5f, 12f, 5f); curveTo(15.3f, 5f, 18f, 7.5f, 18f, 11f)
                lineTo(18f, 16f); moveTo(4.5f, 16f); lineTo(19.5f, 16f); moveTo(10.5f, 19f); lineTo(13.5f, 19f)
            }
        }
    }

    /** Armillary rings for Settings. */
    val Armillary: ImageVector by lazy {
        glyph("Armillary") {
            stroke {
                moveTo(12f, 4f); arcToRelative(8f, 8f, 0f, true, true, 0f, 16f); arcToRelative(8f, 8f, 0f, true, true, 0f, -16f)
                moveTo(4f, 12f); curveTo(4f, 14f, 7.6f, 15.5f, 12f, 15.5f); curveTo(16.4f, 15.5f, 20f, 14f, 20f, 12f)
                moveTo(12f, 4f); curveTo(10f, 4f, 8.5f, 7.6f, 8.5f, 12f); curveTo(8.5f, 16.4f, 10f, 20f, 12f, 20f)
            }
        }
    }
}
