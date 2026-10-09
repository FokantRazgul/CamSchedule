package app.camplanner.designsystem.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.components.ActionStyle
import app.camplanner.designsystem.components.AtlasButton
import app.camplanner.designsystem.components.ChartStar
import app.camplanner.designsystem.components.ChoiceRow
import app.camplanner.designsystem.components.Glyphs
import app.camplanner.designsystem.components.Moon
import app.camplanner.designsystem.components.MoonPhase
import app.camplanner.designsystem.components.OrbitArc
import app.camplanner.designsystem.components.OrbitRing
import app.camplanner.designsystem.components.QuickAddButton
import app.camplanner.designsystem.components.SectionHeader
import app.camplanner.designsystem.components.SubjectMark
import app.camplanner.designsystem.components.starField
import app.camplanner.designsystem.theme.Atlas
import app.camplanner.designsystem.theme.CamPlannerTheme

/** One sheet with every token and component, for design review. */
@Preview(name = "Design system", widthDp = 393, heightDp = 900, showBackground = true, backgroundColor = 0xFF0B0D14)
@Composable
fun DesignSystemSheet() = CamPlannerTheme {
    val c = Atlas.colors
    val t = Atlas.type
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .starField(c.text)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader("Colour")
        listOf(
            "ink" to c.background, "surface" to c.surface, "rule" to c.hairline, "ivory" to c.text,
            "muted" to c.textSecondary, "burgundy" to c.accentFill, "wine" to c.accent, "gold" to c.gold,
        ).chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { row.forEach { (name, color) -> Swatch(name, color) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("SUBJECTS", style = t.overline, color = c.textSecondary)
            c.subjects.forEach { SubjectMark(it, Modifier.size(10.dp)) }
        }

        SectionHeader("Type")
        Sample("64", t.numeralLarge)
        Sample("20 October", t.display)
        Sample("Analysis I", t.title)
        Sample("1 h 30 min free", t.aside, c.textSecondary)
        Sample("Mill Lane Lecture Rooms, a short walk from Trinity Street.", t.body)
        Sample("09:00  10:45  13:28  17:30", t.time)
        Sample("RUNNING HEAD", t.overline, c.textSecondary)

        SectionHeader("Progress")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OrbitRing(0.64f, diameter = 96.dp) { Text("64", style = t.numeral) }
            OrbitRing(0.3f, diameter = 56.dp, markFraction = 0.4f, color = c.accent, showBody = false)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MoonPhase.entries.forEach { Moon(it, size = 14.dp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChartStar(size = 8.dp); ChartStar(size = 11.dp); ChartStar(size = 14.dp)
                }
            }
        }
        OrbitArc(0.75f)

        SectionHeader("Actions")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AtlasButton("Close the day", {}, style = ActionStyle.Filled)
            AtlasButton("Route", {}, glyph = Glyphs.Route)
            AtlasButton("Edit", {}, style = ActionStyle.Text)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAddButton(5, {}); QuickAddButton(10, {}); QuickAddButton(20, {})
        }
        ChoiceRow(listOf(0 to "Walk", 1 to "Bike"), selected = 0, onSelect = {})
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(34.dp).background(color).border(1.dp, Atlas.colors.hairline))
        Text(name, style = Atlas.type.overline.copy(letterSpacing = Atlas.type.overline.letterSpacing * 0.3f), color = Atlas.colors.textSecondary)
    }
}

@Composable
private fun Sample(text: String, style: TextStyle, color: Color = Atlas.colors.text) {
    Text(text, style = style, color = color)
}
