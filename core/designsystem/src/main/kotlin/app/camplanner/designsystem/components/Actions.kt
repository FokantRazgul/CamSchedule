package app.camplanner.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.theme.Atlas

enum class ActionStyle {
    /** Burgundy fill, ivory label. One per screen at most. */
    Filled,

    /** Wine hairline outline, wine label. */
    Outlined,

    /** Wine label only. */
    Text,
}

/** The app's button. Square-shouldered, hairline, quiet. */
@Composable
fun AtlasButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ActionStyle = ActionStyle.Outlined,
    glyph: ImageVector? = null,
    enabled: Boolean = true,
    textStyle: TextStyle = Atlas.type.label,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
) {
    val c = Atlas.colors
    val shape = RoundedCornerShape(Atlas.space.corner)
    val labelColor = when {
        !enabled -> c.textSecondary
        style == ActionStyle.Filled -> c.text
        else -> c.accentText
    }
    val base = modifier
        .defaultMinSize(minHeight = 40.dp)
        .clip(shape)
        .let {
            when (style) {
                ActionStyle.Filled -> it.background(if (enabled) c.accentFill else c.surface)
                ActionStyle.Outlined -> it.border(1.dp, if (enabled) c.accent else c.hairline, shape)
                ActionStyle.Text -> it
            }
        }
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(contentPadding)
    Row(base, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (glyph != null) Icon(glyph, contentDescription = null, tint = labelColor, modifier = Modifier.size(16.dp))
        Text(text, style = textStyle, color = labelColor)
    }
}

/** "+5", "+10", "+20": tabular, compact, same width regardless of digits. */
@Composable
fun QuickAddButton(amount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AtlasButton(
        text = "+$amount",
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 52.dp),
        style = ActionStyle.Outlined,
        textStyle = Atlas.type.time,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
    )
}

/**
 * Two or three mutually exclusive options set side by side, e.g. "Done" / "Not yet" or
 * "Walk" / "Bike". The selected one is filled burgundy.
 */
@Composable
fun <T> ChoiceRow(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Atlas.colors
    val shape = RoundedCornerShape(Atlas.space.corner)
    Row(modifier.clip(shape).border(1.dp, c.hairline, shape)) {
        options.forEachIndexed { i, (value, label) ->
            val isSelected = value == selected
            if (i > 0) Box(Modifier.size(width = 1.dp, height = 36.dp).background(c.hairline))
            Box(
                modifier = Modifier
                    .background(if (isSelected) c.accentFill else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(value) }
                    .semantics { this.selected = isSelected }
                    .defaultMinSize(minWidth = 64.dp, minHeight = 36.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Atlas.type.label, color = if (isSelected) c.text else c.textSecondary)
            }
        }
    }
}
