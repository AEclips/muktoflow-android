package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val ZenDarkColorScheme = darkColorScheme(
    primary = ForestPrimaryDark,
    onPrimary = ForestOnPrimaryDark,
    primaryContainer = ForestPrimaryContainerDark,
    onPrimaryContainer = ForestOnPrimaryContainerDark,
    secondary = BambooSecondaryDark,
    onSecondary = BambooOnSecondaryDark,
    secondaryContainer = BambooSecondaryContainerDark,
    onSecondaryContainer = BambooOnSecondaryContainerDark,
    tertiary = SageTertiaryDark,
    onTertiary = SageOnTertiaryDark,
    tertiaryContainer = SageTertiaryContainerDark,
    onTertiaryContainer = SageOnTertiaryContainerDark,
    background = ZenBackgroundDark,
    onBackground = ZenOnBackgroundDark,
    surface = ZenSurfaceDark,
    onSurface = ZenOnSurfaceDark,
    surfaceVariant = ZenSurfaceVariantDark,
    onSurfaceVariant = ZenOnSurfaceVariantDark,
    outline = ZenOutlineDark
)

private val ZenLightColorScheme = lightColorScheme(
    primary = ForestPrimaryLight,
    onPrimary = ForestOnPrimaryLight,
    primaryContainer = ForestPrimaryContainerLight,
    onPrimaryContainer = ForestOnPrimaryContainerLight,
    secondary = BambooSecondaryLight,
    onSecondary = BambooOnSecondaryLight,
    secondaryContainer = BambooSecondaryContainerLight,
    onSecondaryContainer = BambooOnSecondaryContainerLight,
    tertiary = SageTertiaryLight,
    onTertiary = SageOnTertiaryLight,
    tertiaryContainer = SageTertiaryContainerLight,
    onTertiaryContainer = SageOnTertiaryContainerLight,
    background = ZenBackgroundLight,
    onBackground = ZenOnBackgroundLight,
    surface = ZenSurfaceLight,
    onSurface = ZenOnSurfaceLight,
    surfaceVariant = ZenSurfaceVariantLight,
    onSurfaceVariant = ZenOnSurfaceVariantLight,
    outline = ZenOutlineLight
)

@Composable
fun ZenFocusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Prefer our tailored Zen aesthetic
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ZenDarkColorScheme
        else -> ZenLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backwards compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    ZenFocusTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}

@Composable
fun isZenDarkTheme(): Boolean {
    return MaterialTheme.colorScheme.background.red < 0.2f
}

@Composable
fun focusAccentColor(): androidx.compose.ui.graphics.Color {
    return if (isZenDarkTheme()) FocusColorDark else FocusColorLight
}

