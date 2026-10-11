package app.camplanner.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class AtlasSpacing(
    /** Horizontal margin of headers and sheets. */
    val gutter: Dp = 20.dp,
)

object AtlasMotion {
    /** Progress fills slowly and quietly. */
    const val SLOW_MS = 600
}
