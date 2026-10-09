package app.camplanner.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Raw palette. Nothing outside this file should spell out a hex value; read colours through
 * [AtlasColors] (via `Atlas.colors`) instead.
 */
internal object AtlasPalette {
    val Ink = Color(0xFF0B0D14)
    val Surface = Color(0xFF12151F)
    val Hairline = Color(0xFF262A36)
    val Ivory = Color(0xFFEDE6D6)
    val IvoryMuted = Color(0xFF8A8778)
    val Burgundy = Color(0xFF7A1F2B)
    val Wine = Color(0xFFB8455A)

    /** Wine lifted to 5:1 on ink, for small wine-coloured text. #B8455A is 3.7:1, fine for UI and large text only. */
    val WineText = Color(0xFFC95F72)
    val Gold = Color(0xFFC2A25A)

    /** Muted subject inks: OKLCH L 0.70, C 0.055, hue stepped round the wheel; all ~7:1 on ink. */
    val Subjects = listOf(
        Color(0xFFBE9193), // rose
        Color(0xFF7DA5BC), // slate
        Color(0xFFAE9C77), // ochre
        Color(0xFF84A990), // sage
        Color(0xFFA696BA), // heather
        Color(0xFFBB9580), // terracotta
        Color(0xFF75AAA9), // verdigris
        Color(0xFF9AA47E), // olive
        Color(0xFF919DC2), // dusk
        Color(0xFFB891A6), // mauve
    )
}

@Immutable
data class AtlasColors(
    /** Page background: ink with a blue undertone. */
    val background: Color,
    /** Raised areas (sheets, the bottom bar). Used sparingly; most structure comes from rules. */
    val surface: Color,
    /** 1px rules and outlines. */
    val hairline: Color,
    val text: Color,
    val textSecondary: Color,
    /** Burgundy for filled areas (primary button, selected day). */
    val accentFill: Color,
    /** Wine for interactive elements: outlines, icons, rings, large labels. */
    val accent: Color,
    /** Wine for small text. */
    val accentText: Color,
    /** Antique gold: progress and stars only. */
    val gold: Color,
    val subjects: List<Color>,
) {
    fun subject(index: Int): Color = subjects[Math.floorMod(index, subjects.size)]

    /** Overdue / error uses the accent rather than a new red, to stay inside the palette. */
    val warning: Color get() = accentText
}

val DarkAtlasColors = AtlasColors(
    background = AtlasPalette.Ink,
    surface = AtlasPalette.Surface,
    hairline = AtlasPalette.Hairline,
    text = AtlasPalette.Ivory,
    textSecondary = AtlasPalette.IvoryMuted,
    accentFill = AtlasPalette.Burgundy,
    accent = AtlasPalette.Wine,
    accentText = AtlasPalette.WineText,
    gold = AtlasPalette.Gold,
    subjects = AtlasPalette.Subjects,
)
