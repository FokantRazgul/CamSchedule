package app.camplanner.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import app.camplanner.designsystem.theme.Atlas
import kotlin.random.Random

private class Star(val x: Float, val y: Float, val radiusPx: Float, val alpha: Float)

/**
 * A barely visible, static scatter of stars. Positions come from a fixed seed so the sky is the
 * same every time the screen is drawn; nothing moves.
 *
 * @param starsPer10k stars per 10 000 dp²
 */
fun Modifier.starField(
    color: Color,
    seed: Int = 1729,
    starsPer10k: Float = 1.1f,
    graticule: Boolean = false,
): Modifier = drawWithCache {
    val dpArea = (size.width / this.density) * (size.height / this.density)
    val count = (dpArea / 10_000f * starsPer10k).toInt().coerceIn(0, 600)
    val random = Random(seed)
    val stars = List(count) {
        // Most stars are faint pinpricks; a handful are a touch brighter, like 3rd-magnitude stars.
        val bright = random.nextFloat() < 0.08f
        Star(
            x = random.nextFloat() * size.width,
            y = random.nextFloat() * size.height,
            radiusPx = (if (bright) 1.1f else 0.55f + random.nextFloat() * 0.4f) * this.density,
            alpha = if (bright) 0.22f else 0.05f + random.nextFloat() * 0.10f,
        )
    }
    // A fragment of a star chart's coordinate grid: circles of declination round a pole beyond
    // the top-right corner and hour lines radiating from it. Very faint; it should be felt more
    // than seen.
    val pole = Offset(size.width * 1.15f, -size.width * 0.35f)
    val gridColor = color.copy(alpha = 0.045f)
    val step = 64f * this.density
    val reach = size.width * 1.9f
    onDrawBehind {
        if (graticule) {
            var r = step * 3
            while (r < reach) {
                drawCircle(gridColor, r, pole, style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                r += step
            }
            for (deg in 100..250 step 12) {
                val a = Math.toRadians(deg.toDouble())
                drawLine(
                    gridColor,
                    Offset(pole.x + step * 3 * kotlin.math.cos(a).toFloat(), pole.y + step * 3 * kotlin.math.sin(a).toFloat()),
                    Offset(pole.x + reach * kotlin.math.cos(a).toFloat(), pole.y + reach * kotlin.math.sin(a).toFloat()),
                    1f,
                )
            }
        }
        stars.forEach { s -> drawCircle(color.copy(alpha = s.alpha), s.radiusPx, Offset(s.x, s.y)) }
    }
}

/** Page background with the star scatter, used behind the home (Today) screen. */
@Composable
fun StarFieldBackground(
    modifier: Modifier = Modifier,
    graticule: Boolean = true,
    starsPer10k: Float = 1.1f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .background(Atlas.colors.background)
            .starField(Atlas.colors.text, starsPer10k = starsPer10k, graticule = graticule),
        content = content,
    )
}
