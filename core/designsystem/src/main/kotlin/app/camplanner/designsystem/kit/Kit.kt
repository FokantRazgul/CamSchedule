package app.camplanner.designsystem.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.designsystem.theme.AtlasMotion
import java.time.LocalDate
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

/*
 * The reading-first kit: grouped sections instead of loose rules, sans-serif for what you read,
 * the serif kept for titles and numbers, 48dp targets, standard Material controls in the atlas
 * palette. Ornament is limited to gold progress and the moon-phase streaks.
 */

// --- Page header ------------------------------------------------------------------------------

/**
 * Screen header: a small coloured overline ("Michaelmas · Week 2"), a large serif title and
 * icon actions on the right.
 */
@Composable
fun LargeHeader(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    overlineColor: Color = Atlas.colors.accentText,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().padding(start = Atlas.space.gutter, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(overline.orEmpty(), style = Atlas.type.caption, color = overlineColor, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        Text(title, style = Atlas.type.screenTitle, color = Atlas.colors.text, modifier = Modifier.padding(end = 12.dp))
    }
}

@Composable
fun HeaderAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description, tint = Atlas.colors.textSecondary)
    }
}

// --- Groups and rows --------------------------------------------------------------------------

/** Heading above a group: title on the left, an optional action or count on the right. */
@Composable
fun GroupHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, top = 20.dp, bottom = 8.dp).heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Atlas.type.groupTitle, color = Atlas.colors.text, modifier = Modifier.weight(1f))
        if (trailing != null) Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/** A rounded surface that holds related rows. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Atlas.colors.surface,
        border = BorderStroke(1.dp, Atlas.colors.hairline.copy(alpha = 0.6f)),
    ) {
        Column(content = content)
    }
}

/** Inset divider between rows of a [Group]. */
@Composable
fun GroupDivider(inset: Dp = 16.dp) {
    Box(Modifier.padding(start = inset).fillMaxWidth().height(1.dp).background(Atlas.colors.hairline))
}

/** A list row: optional leading element, title and supporting line, optional trailing element. */
@Composable
fun ItemRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingColor: Color = Atlas.colors.textSecondary,
    titleColor: Color = Atlas.colors.text,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    minHeight: Dp = 64.dp,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(Modifier.width(40.dp), contentAlignment = Alignment.CenterStart) { leading() }
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Atlas.type.itemTitle, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(supporting, style = Atlas.type.supporting, color = supportingColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}

/** A small coloured bar for a subject, used as a row's leading mark. */
@Composable
fun SubjectBar(color: Color, modifier: Modifier = Modifier, height: Dp = 36.dp) {
    Box(modifier.width(4.dp).height(height).clip(RoundedCornerShape(2.dp)).background(color))
}

// --- Progress ---------------------------------------------------------------------------------

/** A gold ring, thick enough to read at a glance, with content in the middle. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    stroke: Dp = 5.dp,
    color: Color = Atlas.colors.gold,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val value by animateFloatAsState(progress.coerceIn(0f, 1f), tween(AtlasMotion.SLOW_MS), label = "ring")
    val track = Atlas.colors.hairline
    Box(
        modifier.size(size).semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val d = this.size.minDimension - w
            val tl = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(w))
            if (value > 0f) drawArc(color, -90f, 360f * value, false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** A flat progress bar with rounded ends. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, color: Color = Atlas.colors.gold, height: Dp = 6.dp) {
    val value by animateFloatAsState(progress.coerceIn(0f, 1f), tween(AtlasMotion.SLOW_MS), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(Atlas.colors.hairline)) {
        Box(Modifier.fillMaxWidth(value).height(height).clip(CircleShape).background(color))
    }
}

/** A compact figure: label above, value, and a detail line. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    valueColor: Color = Atlas.colors.text,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Atlas.colors.surface,
        border = BorderStroke(1.dp, Atlas.colors.hairline.copy(alpha = 0.6f)),
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = Atlas.type.caption, color = Atlas.colors.textSecondary, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(value, style = Atlas.type.groupTitle.copy(fontSize = Atlas.type.groupTitle.fontSize * 1.15f), color = valueColor, maxLines = 1)
            if (detail != null) Text(detail, style = Atlas.type.supporting, color = Atlas.colors.textSecondary, maxLines = 1)
        }
    }
}

// --- Buttons and chips ------------------------------------------------------------------------

/** The main action: burgundy fill, ivory label. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Atlas.colors.accentFill, contentColor = Atlas.colors.text,
            disabledContainerColor = Atlas.colors.surfaceHigh, disabledContentColor = Atlas.colors.textSecondary,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) { ButtonContent(text, icon) }
}

/** Secondary action: outlined, wine label. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp),
        border = BorderStroke(1.dp, if (enabled) Atlas.colors.accent.copy(alpha = 0.7f) else Atlas.colors.hairline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Atlas.colors.accentText),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) { ButtonContent(text, icon) }
}

/** Low-emphasis action: wine label only. */
@Composable
fun TertiaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = Atlas.colors.accentText) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = color)) {
        ButtonContent(text, icon)
    }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(text, style = Atlas.type.button)
}

/** Tonal square button for counts: "+5", "+10", "−". */
@Composable
fun CountButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, description: String = text) {
    Surface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 52.dp, minHeight = 48.dp).semantics { contentDescription = description },
        shape = RoundedCornerShape(12.dp),
        color = Atlas.colors.surfaceHigh,
        contentColor = Atlas.colors.text,
        border = BorderStroke(1.dp, Atlas.colors.hairline),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
            Text(text, style = Atlas.type.button.copy(fontFeatureSettings = "tnum"), textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun AppChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = Atlas.type.button.copy(fontSize = Atlas.type.supporting.fontSize)) },
        modifier = modifier.heightIn(min = 40.dp),
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = Atlas.colors.textSecondary,
            selectedContainerColor = Atlas.colors.accentFill,
            selectedLabelColor = Atlas.colors.text,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected,
            borderColor = Atlas.colors.hairline, selectedBorderColor = Atlas.colors.accentFill,
        ),
    )
}

/** Burgundy "add" button floating above the tab bar. */
@Composable
fun AddButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = Atlas.colors.accentFill,
        contentColor = Atlas.colors.text,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(text, style = Atlas.type.button)
        }
    }
}

// --- Fields -----------------------------------------------------------------------------------

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    suffix: String? = null,
) {
    val c = Atlas.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = c.textSecondary.copy(alpha = 0.7f)) } },
        suffix = suffix?.let { { Text(it, color = c.textSecondary) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        textStyle = Atlas.type.reading,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.hairline,
            focusedContainerColor = c.surfaceHigh,
            unfocusedContainerColor = c.surfaceHigh,
            cursorColor = c.accent,
            focusedLabelColor = c.accentText,
            unfocusedLabelColor = c.textSecondary,
            focusedTextColor = c.text,
            unfocusedTextColor = c.text,
        ),
    )
}

/** A tappable field that opens a picker: label above, value below, framed like a text field. */
@Composable
fun PickerBox(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Atlas.colors
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.surfaceHigh)
            .border(1.dp, c.hairline, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = Atlas.type.caption, color = c.textSecondary)
        Text(value, style = Atlas.type.reading.copy(fontFeatureSettings = "tnum"), color = c.text)
    }
}

// --- Calendar strip ---------------------------------------------------------------------------

@Immutable
data class DayCell(val date: LocalDate, val events: Int, val isToday: Boolean)

/** One week as seven tappable days; dots show how busy each day is. */
@Composable
fun DayStrip(days: List<DayCell>, selected: LocalDate, onSelect: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val c = Atlas.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { d ->
            val isSelected = d.date == selected
            Column(
                Modifier
                    .width(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) c.accentFill else Color.Transparent)
                    .border(1.dp, if (d.isToday && !isSelected) c.gold.copy(alpha = 0.7f) else Color.Transparent, RoundedCornerShape(16.dp))
                    .clickable(role = Role.Tab) { onSelect(d.date) }
                    .semantics { this.selected = isSelected }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    d.date.dayOfWeek.getDisplayName(JTextStyle.NARROW, Locale.UK),
                    style = Atlas.type.caption, color = if (isSelected) c.text else c.textSecondary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${d.date.dayOfMonth}",
                    style = Atlas.type.groupTitle.copy(fontFeatureSettings = "tnum"),
                    color = if (isSelected) c.text else if (d.isToday) c.gold else c.text,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(5.dp)) {
                    repeat(minOf(d.events, 3)) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(if (isSelected) c.text else c.accentText))
                    }
                }
            }
        }
    }
}

// --- Navigation -------------------------------------------------------------------------------

@Immutable
data class NavDestination<T>(val key: T, val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)

@Composable
fun <T> AppNavBar(items: List<NavDestination<T>>, selected: T, onSelect: (T) -> Unit) {
    val c = Atlas.colors
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
        NavigationBar(containerColor = c.background, tonalElevation = 0.dp) {
            items.forEach { item ->
                val isSelected = item.key == selected
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelect(item.key) },
                    icon = { Icon(if (isSelected) item.selectedIcon else item.icon, contentDescription = null) },
                    label = { Text(item.label, style = Atlas.type.caption) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = c.gold,
                        selectedTextColor = c.text,
                        indicatorColor = c.surfaceHigh,
                        unselectedIconColor = c.textSecondary,
                        unselectedTextColor = c.textSecondary,
                    ),
                )
            }
        }
    }
}

// --- Sheets -----------------------------------------------------------------------------------

/** The body of a bottom sheet: grab handle, title, then content. */
@Composable
fun SheetBody(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = Atlas.space.gutter).padding(bottom = 24.dp)) {
        Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(Atlas.colors.hairline))
        }
        Text(title, style = Atlas.type.heroTitle.copy(fontSize = Atlas.type.heroTitle.fontSize * 0.9f), color = Atlas.colors.text)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

// --- Settings-style controls ------------------------------------------------------------------

/** A themed switch: gold thumb when on, quiet when off. */
@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Atlas.colors
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = androidx.compose.material3.SwitchDefaults.colors(
            checkedThumbColor = c.text,
            checkedTrackColor = c.accentFill,
            checkedBorderColor = c.accentFill,
            uncheckedThumbColor = c.textSecondary,
            uncheckedTrackColor = c.surfaceHigh,
            uncheckedBorderColor = c.hairline,
        ),
    )
}

/** − value + for small numbers with a unit; long ranges move in [step]s. */
@Composable
fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1,
    min: Int = 0,
    max: Int = 999,
    suffix: String = "",
    description: String = "",
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        CountButton("−", { onChange((value - step).coerceAtLeast(min)) }, description = "Decrease $description")
        Text(
            "$value$suffix",
            style = Atlas.type.itemTitle.copy(fontFeatureSettings = "tnum"),
            color = Atlas.colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp),
        )
        CountButton("+", { onChange((value + step).coerceAtMost(max)) }, description = "Increase $description")
    }
}

/** A row in a settings group: title and explanation on the left, a control on the right. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    control: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Atlas.type.reading, color = Atlas.colors.text)
            if (supporting != null) Text(supporting, style = Atlas.type.supporting, color = Atlas.colors.textSecondary)
        }
        if (control != null) {
            Spacer(Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, content = control)
        }
    }
}

/** A notice at the top of a page, with an action: missing permissions, nothing imported yet. */
@Composable
fun Banner(text: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = Atlas.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = c.accentFill.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, c.accent.copy(alpha = 0.45f)),
        onClick = onAction,
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = c.accentText, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = Atlas.type.supporting.copy(fontSize = Atlas.type.reading.fontSize * 0.94f), color = c.text, modifier = Modifier.weight(1f))
            TertiaryButton(action, onAction)
        }
    }
}

/** A quiet message for an empty group, with an optional action. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(text, style = Atlas.type.reading, color = Atlas.colors.textSecondary)
        if (action != null) TertiaryButton(action, onAction, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Top bar for pages opened from somewhere else: back arrow, title, optional actions. */
@Composable
fun SubPageBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Atlas.colors.text)
        }
        Text(
            title, style = Atlas.type.heroTitle.copy(fontSize = Atlas.type.heroTitle.fontSize * 0.85f), color = Atlas.colors.text,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}
