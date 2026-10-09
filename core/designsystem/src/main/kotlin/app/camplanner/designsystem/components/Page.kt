package app.camplanner.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.camplanner.designsystem.theme.Atlas

/**
 * The standard page: star-field ground, masthead with double rule, an italic dateline, a serif
 * title and an optional Latin or plain aside, then the page's own content in a scrolling column.
 */
@Composable
fun AtlasPage(
    mastheadLeft: String,
    mastheadRight: String?,
    title: String,
    modifier: Modifier = Modifier,
    dateline: String? = null,
    aside: String? = null,
    onBack: (() -> Unit)? = null,
    graticule: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Atlas.colors
    StarFieldBackground(modifier.fillMaxSize(), graticule = graticule, starsPer10k = 0.6f) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Atlas.space.gutter),
        ) {
            Spacer(Modifier.height(Atlas.space.l))
            if (onBack != null) {
                Row(
                    Modifier
                        .padding(bottom = Atlas.space.s)
                        .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Glyphs.ChevronLeft, contentDescription = null, tint = c.accentText, modifier = Modifier.size(16.dp))
                    Text("Back", style = Atlas.type.label, color = c.accentText)
                }
            }
            Masthead(mastheadLeft, mastheadRight)
            Spacer(Modifier.height(Atlas.space.l))
            if (dateline != null) Text(dateline, style = Atlas.type.dateline, color = c.textSecondary)
            Text(title, style = Atlas.type.display, color = c.text)
            if (aside != null) {
                Spacer(Modifier.height(Atlas.space.xs))
                Text(aside, style = Atlas.type.aside, color = c.textSecondary)
            }
            Spacer(Modifier.height(Atlas.space.xl))
            content()
            Spacer(Modifier.height(Atlas.space.xxxl))
        }
    }
}

/** A labelled setting: label and optional explanation on the left, the control on the right. */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    showRule: Boolean = true,
    control: @Composable RowScope.() -> Unit,
) {
    RuledRow(modifier, showRule = showRule) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Atlas.type.body, color = Atlas.colors.text)
            if (detail != null) Text(detail, style = Atlas.type.bodySmall, color = Atlas.colors.textSecondary)
        }
        control()
    }
}

/** On / Off as a two-part choice, so the state reads in words. */
@Composable
fun OnOff(on: Boolean, onChange: (Boolean) -> Unit) {
    ChoiceRow(listOf(true to "On", false to "Off"), selected = on, onSelect = onChange)
}

/** A row the user can tap to open something: italic title, a line of detail, a chevron. */
@Composable
fun IndexRow(numeral: String?, title: String, detail: String?, onClick: () -> Unit, showRule: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Atlas.space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (numeral != null) {
            Text("$numeral.", style = Atlas.type.rubric, color = Atlas.colors.rubric, modifier = Modifier.widthIn(min = 44.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Atlas.type.sectionTitle, color = Atlas.colors.text)
            if (detail != null) Text(detail, style = Atlas.type.bodySmall, color = Atlas.colors.textSecondary)
        }
        Icon(Glyphs.ChevronRight, contentDescription = null, tint = Atlas.colors.textSecondary, modifier = Modifier.size(18.dp))
    }
    if (showRule) Hairline()
}

/** A plate-framed dialog: surface ground, hairline border, italic title, actions on the right. */
@Composable
fun AtlasDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Atlas.colors
    val shape = RoundedCornerShape(Atlas.space.corner)
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(c.surface, shape)
                .border(1.dp, c.hairline, shape)
                .padding(4.dp)
                .border(1.dp, c.engraving.copy(alpha = 0.18f), shape)
                .padding(Atlas.space.xl),
        ) {
            Column {
                Text(title, style = Atlas.type.sectionTitle, color = c.text)
                Spacer(Modifier.height(Atlas.space.s))
                Hairline()
                Spacer(Modifier.height(Atlas.space.l))
                content()
                Spacer(Modifier.height(Atlas.space.xl))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Atlas.space.s, Alignment.End), content = actions)
            }
        }
    }
}
