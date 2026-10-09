package app.camplanner.designsystem.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalAtlasColors = staticCompositionLocalOf { DarkAtlasColors }
val LocalAtlasType = staticCompositionLocalOf { DefaultAtlasType }
val LocalAtlasSpacing = staticCompositionLocalOf { AtlasSpacing() }

/** Accessors: `Atlas.colors.gold`, `Atlas.type.title`, `Atlas.space.gutter`. */
object Atlas {
    val colors: AtlasColors
        @Composable @ReadOnlyComposable get() = LocalAtlasColors.current
    val type: AtlasType
        @Composable @ReadOnlyComposable get() = LocalAtlasType.current
    val space: AtlasSpacing
        @Composable @ReadOnlyComposable get() = LocalAtlasSpacing.current
}

/**
 * Every Material role is pinned to an atlas token so no stock purple can leak in through a
 * component default. There is no light scheme and no dynamic colour on purpose.
 */
internal fun atlasColorScheme(c: AtlasColors): ColorScheme = darkColorScheme(
    primary = c.accent,
    onPrimary = c.text,
    primaryContainer = c.accentFill,
    onPrimaryContainer = c.text,
    inversePrimary = c.accentFill,
    secondary = c.gold,
    onSecondary = c.background,
    secondaryContainer = c.surface,
    onSecondaryContainer = c.text,
    tertiary = c.gold,
    onTertiary = c.background,
    tertiaryContainer = c.surface,
    onTertiaryContainer = c.text,
    background = c.background,
    onBackground = c.text,
    surface = c.background,
    onSurface = c.text,
    surfaceVariant = c.surface,
    onSurfaceVariant = c.textSecondary,
    surfaceTint = c.background,
    inverseSurface = c.text,
    inverseOnSurface = c.background,
    error = c.accentText,
    onError = c.text,
    errorContainer = c.accentFill,
    onErrorContainer = c.text,
    outline = c.hairline,
    outlineVariant = c.hairline,
    scrim = c.background,
    surfaceBright = c.surface,
    surfaceDim = c.background,
    surfaceContainerLowest = c.background,
    surfaceContainerLow = c.background,
    surfaceContainer = c.surface,
    surfaceContainerHigh = c.surface,
    surfaceContainerHighest = c.surface,
)

internal fun atlasTypography(t: AtlasType): Typography = Typography(
    displayLarge = t.numeralLarge,
    displayMedium = t.display,
    displaySmall = t.headline,
    headlineLarge = t.display,
    headlineMedium = t.headline,
    headlineSmall = t.title,
    titleLarge = t.title,
    titleMedium = t.body.copy(fontWeight = t.label.fontWeight),
    titleSmall = t.label,
    bodyLarge = t.body,
    bodyMedium = t.body,
    bodySmall = t.bodySmall,
    labelLarge = t.label,
    labelMedium = t.label,
    labelSmall = t.overline,
)

@Composable
fun CamPlannerTheme(content: @Composable () -> Unit) {
    val colors = DarkAtlasColors
    val type = DefaultAtlasType
    val space = AtlasSpacing()
    val corner = RoundedCornerShape(space.corner)
    CompositionLocalProvider(
        LocalAtlasColors provides colors,
        LocalAtlasType provides type,
        LocalAtlasSpacing provides space,
    ) {
        MaterialTheme(
            colorScheme = atlasColorScheme(colors),
            typography = atlasTypography(type),
            shapes = Shapes(
                extraSmall = corner, small = corner, medium = corner, large = corner, extraLarge = corner,
            ),
        ) {
            CompositionLocalProvider(
                LocalContentColor provides colors.text,
                LocalTextSelectionColors provides TextSelectionColors(
                    handleColor = colors.accent,
                    backgroundColor = colors.accentFill,
                ),
                content = content,
            )
        }
    }
}
