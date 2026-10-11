package app.camplanner.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.camplanner.designsystem.R

val Cormorant = FontFamily(
    Font(R.font.cormorant_garamond_regular, FontWeight.Normal),
    Font(R.font.cormorant_garamond_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.cormorant_garamond_medium, FontWeight.Medium),
    Font(R.font.cormorant_garamond_semibold, FontWeight.SemiBold),
)

val Hanken = FontFamily(
    Font(R.font.hanken_grotesk_regular, FontWeight.Normal),
    Font(R.font.hanken_grotesk_medium, FontWeight.Medium),
    Font(R.font.hanken_grotesk_semibold, FontWeight.SemiBold),
)

/** Lining, tabular figures. Cormorant defaults to proportional old-style; Hanken is tabular already. */
private const val TABULAR = "lnum, tnum"

private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

/**
 * Serif (Cormorant) only for titles and big numbers; sans (Hanken Grotesk) for everything you
 * read or tap.
 */
@Immutable
data class AtlasType(
    /** Large screen title: "Sunday 11 October", "Fitness". */
    val screenTitle: TextStyle = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 40.sp, fontFeatureSettings = TABULAR),
    /** The title inside the hero card and on sheets. */
    val heroTitle: TextStyle = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 34.sp, fontFeatureSettings = TABULAR),
    /** Big counts: the alarm clock face. */
    val heroNumber: TextStyle = TextStyle(fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, lineHeight = 44.sp, fontFeatureSettings = TABULAR, lineHeightStyle = trim),
    /** Group headings: "Schedule", "Today's sets". */
    val groupTitle: TextStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    /** List item titles. */
    val itemTitle: TextStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    /** Paragraphs and values. */
    val reading: TextStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    /** Supporting lines under titles. */
    val supporting: TextStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum"),
    /** Buttons and chips. */
    val button: TextStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    /** Small captions and the overline above titles. */
    val caption: TextStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.02.em, fontFeatureSettings = "tnum"),
)

val DefaultAtlasType = AtlasType()
