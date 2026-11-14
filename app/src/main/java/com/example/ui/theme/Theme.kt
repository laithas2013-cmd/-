package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TawasulLightColorScheme =
  lightColorScheme(
    primary = TawasulGold,
    onPrimary = TawasulOnGold,
    primaryContainer = TawasulGoldSoft,
    onPrimaryContainer = TawasulOnGold,
    secondary = TawasulGoldDeep,
    onSecondary = Color.White,
    secondaryContainer = TawasulGoldChip,
    onSecondaryContainer = TawasulOnGold,
    tertiary = TawasulGoldDeep,
    onTertiary = Color.White,
    background = TawasulWhite,
    onBackground = TawasulInkLight,
    surface = TawasulIvory,
    onSurface = TawasulInkLight,
    surfaceVariant = TawasulSurfaceVariantLight,
    onSurfaceVariant = TawasulMutedInkLight,
    surfaceContainerLowest = TawasulWhite,
    surfaceContainerLow = TawasulIvory,
    surfaceContainer = TawasulCream,
    surfaceContainerHigh = TawasulSurfaceVariantLight,
    outline = TawasulOutlineLight,
    outlineVariant = TawasulOutlineLight.copy(alpha = 0.6f),
    error = TawasulErrorRed,
    onError = Color.White,
  )

private val TawasulDarkColorScheme =
  darkColorScheme(
    primary = TawasulGoldDark,
    onPrimary = TawasulOnGold,
    primaryContainer = TawasulGoldContainerDark,
    onPrimaryContainer = TawasulOnGoldContainerDark,
    secondary = TawasulGold,
    onSecondary = TawasulOnGold,
    secondaryContainer = TawasulGoldContainerDark,
    onSecondaryContainer = TawasulOnGoldContainerDark,
    tertiary = TawasulGoldDark,
    onTertiary = TawasulOnGold,
    background = TawasulObsidian,
    onBackground = TawasulInkDark,
    surface = TawasulCharcoal,
    onSurface = TawasulInkDark,
    surfaceVariant = TawasulCharcoalElevated,
    onSurfaceVariant = TawasulMutedInkDark,
    surfaceContainerLowest = TawasulObsidian,
    surfaceContainerLow = TawasulCharcoal,
    surfaceContainer = TawasulCharcoalElevated,
    surfaceContainerHigh = Color(0xFF2B303D),
    outline = TawasulOutlineDark,
    outlineVariant = TawasulOutlineDark.copy(alpha = 0.6f),
    error = TawasulErrorDark,
    onError = Color(0xFF3B0808),
  )

@Composable
fun TawasulPlusTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  isArabic: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) TawasulDarkColorScheme else TawasulLightColorScheme
  MaterialTheme(
    colorScheme = colorScheme,
    typography = buildTawasulTypography(isArabic = isArabic),
    content = content,
  )
}
