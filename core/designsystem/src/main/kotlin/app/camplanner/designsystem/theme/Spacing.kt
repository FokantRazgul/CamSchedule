package app.camplanner.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class AtlasSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
    /** Horizontal page margin. */
    val gutter: Dp = 20.dp,
    /** Width of the time column on timelines. */
    val timeColumn: Dp = 52.dp,
    /** Width of the chart column (meridian line + stars). */
    val chartColumn: Dp = 36.dp,
    /** Minimum touch target. */
    val touch: Dp = 48.dp,
    /** Corner radius: barely softened, like a printed plate. */
    val corner: Dp = 2.dp,
)

object AtlasMotion {
    /** Motion is slow and quiet. */
    const val SLOW_MS = 600
    const val MEDIUM_MS = 320
}
