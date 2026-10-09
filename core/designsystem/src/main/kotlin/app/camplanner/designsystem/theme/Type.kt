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

@Immutable
data class AtlasType(
    /** Big serif numerals inside rings and on the stats page. */
    val numeralLarge: TextStyle,
    val numeral: TextStyle,
    /** Screen titles: "Tuesday, 20 October". */
    val display: TextStyle,
    val headline: TextStyle,
    /** Event and section titles. */
    val title: TextStyle,
    /** Italic serif for asides: free time, quiet empty states. */
    val aside: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    /** Spaced small capitals for section headings, like an almanac's running heads. */
    val overline: TextStyle,
    /** Italic serif section titles: "Reading", "The Order of the Day". */
    val sectionTitle: TextStyle,
    /** Roman section numerals, set in the rubric colour. */
    val rubric: TextStyle,
    /** Italic serif weekday above the date. */
    val dateline: TextStyle,
    /** Times of day and durations. */
    val time: TextStyle,
)

val DefaultAtlasType = AtlasType(
    numeralLarge = TextStyle(
        fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 52.sp, lineHeight = 52.sp,
        fontFeatureSettings = TABULAR, lineHeightStyle = trim,
    ),
    numeral = TextStyle(
        fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 32.sp,
        fontFeatureSettings = TABULAR, lineHeightStyle = trim,
    ),
    display = TextStyle(
        fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 48.sp, lineHeight = 50.sp,
        letterSpacing = (-0.005).em, fontFeatureSettings = TABULAR,
    ),
    headline = TextStyle(
        fontFamily = Cormorant, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 32.sp,
        fontFeatureSettings = TABULAR,
    ),
    title = TextStyle(
        fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 25.sp,
        fontFeatureSettings = TABULAR,
    ),
    aside = TextStyle(
        fontFamily = Cormorant, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 22.sp,
        fontFeatureSettings = TABULAR,
    ),
    body = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    label = TextStyle(
        fontFamily = Hanken, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp,
        letterSpacing = 0.01.em,
    ),
    overline = TextStyle(
        fontFamily = Hanken, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, lineHeight = 14.sp,
        letterSpacing = 0.22.em,
    ),
    sectionTitle = TextStyle(
        fontFamily = Cormorant, fontStyle = FontStyle.Italic, fontSize = 25.sp, lineHeight = 28.sp,
        fontFeatureSettings = TABULAR,
    ),
    rubric = TextStyle(
        fontFamily = Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 20.sp,
        letterSpacing = 0.06.em,
    ),
    dateline = TextStyle(
        fontFamily = Cormorant, fontStyle = FontStyle.Italic, fontSize = 24.sp, lineHeight = 28.sp,
        fontFeatureSettings = TABULAR,
    ),
    time = TextStyle(
        fontFamily = Hanken, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp,
        letterSpacing = 0.02.em, fontFeatureSettings = "tnum",
    ),
)
