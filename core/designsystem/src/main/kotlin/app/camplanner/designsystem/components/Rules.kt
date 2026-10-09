package app.camplanner.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.theme.Atlas

/** A 1px rule. The basic structural element: lists are rules and spacing, not cards. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Atlas.colors.hairline) {
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1f)
    }
}

/**
 * Running head: spaced capitals, then a rule to the right edge, then optional trailing content.
 *
 *     READING ─────────────────────── 3 subjects
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Atlas.space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), style = Atlas.type.overline, color = Atlas.colors.textSecondary)
        Spacer(Modifier.width(Atlas.space.m))
        Hairline(Modifier.weight(1f))
        if (trailing != null) {
            Spacer(Modifier.width(Atlas.space.m))
            trailing()
        }
    }
}

/** A list row followed by a rule. */
@Composable
fun RuledRow(
    modifier: Modifier = Modifier,
    showRule: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Atlas.space.m),
        horizontalArrangement = Arrangement.spacedBy(Atlas.space.m),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
    if (showRule) Hairline()
}
