package app.camplanner.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.theme.Atlas

/** A subject's colour as a small lozenge, the way charts key their constellations. */
@Composable
fun SubjectMark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(8.dp)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(center.x, 0f); lineTo(size.width, center.y); lineTo(center.x, size.height); lineTo(0f, center.y); close()
        }
        drawPath(path, color)
    }
}

/**
 * Screen heading: an overline (weekday, section) above a serif display line, with optional
 * actions on the right.
 */
@Composable
fun ScreenHeading(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (overline != null) {
                Text(overline.uppercase(), style = Atlas.type.overline, color = Atlas.colors.textSecondary)
                Spacer(Modifier.height(Atlas.space.xs))
            }
            Text(title, style = Atlas.type.display, color = Atlas.colors.text)
        }
        if (actions != null) Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

data class NavItem<T>(val key: T, val label: String)

/**
 * Bottom navigation in words, no icons. A small gold star marks the current page, sitting on
 * the rule above the bar.
 */
@Composable
fun <T> AtlasNavBar(
    items: List<NavItem<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Atlas.colors
    Column(modifier.fillMaxWidth().background(c.background).windowInsetsPadding(WindowInsets.navigationBars)) {
        Hairline()
        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            items.forEach { item ->
                val isSelected = item.key == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(role = Role.Tab) { onSelect(item.key) }
                        .semantics { this.selected = isSelected }
                        .padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (isSelected) ChartStar(size = 7.dp) else Spacer(Modifier.size(7.dp))
                    Spacer(Modifier.height(9.dp))
                    Text(
                        item.label,
                        style = Atlas.type.label,
                        color = if (isSelected) c.text else c.textSecondary,
                    )
                }
            }
        }
    }
}
