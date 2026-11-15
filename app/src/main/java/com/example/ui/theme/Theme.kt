package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val LocalZsmeDarkTheme = staticCompositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = ZsmeCobaltDark,
    onPrimary = ZsmeOnCobaltDark,
    primaryContainer = ZsmeCobaltContainerDark,
    onPrimaryContainer = ZsmeOnCobaltContainerDark,
    secondary = ZsmeTealDark,
    onSecondary = ZsmeOnTealDark,
    secondaryContainer = ZsmeTealContainerDark,
    onSecondaryContainer = ZsmeOnTealContainerDark,
    tertiary = ZsmeAmberDark,
    onTertiary = ZsmeOnAmberDark,
    tertiaryContainer = ZsmeAmberContainerDark,
    onTertiaryContainer = ZsmeOnAmberContainerDark,
    background = ZsmeBackgroundDark,
    onBackground = ZsmeOnBackgroundDark,
    surface = ZsmeSurfaceDark,
    onSurface = ZsmeOnSurfaceDark,
    surfaceVariant = ZsmeSurfaceVariantDark,
    onSurfaceVariant = ZsmeOnSurfaceVariantDark,
    outline = ZsmeOutlineDark,
    error = ZsmeErrorDark,
    errorContainer = ZsmeErrorContainerDark,
    onErrorContainer = ZsmeOnErrorContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = ZsmeCobalt,
    onPrimary = ZsmeOnCobalt,
    primaryContainer = ZsmeCobaltContainer,
    onPrimaryContainer = ZsmeOnCobaltContainer,
    secondary = ZsmeTeal,
    onSecondary = ZsmeOnTeal,
    secondaryContainer = ZsmeTealContainer,
    onSecondaryContainer = ZsmeOnTealContainer,
    tertiary = ZsmeAmber,
    onTertiary = ZsmeOnAmber,
    tertiaryContainer = ZsmeAmberContainer,
    onTertiaryContainer = ZsmeOnAmberContainer,
    background = ZsmeBackgroundLight,
    onBackground = ZsmeOnBackgroundLight,
    surface = ZsmeSurfaceLight,
    onSurface = ZsmeOnSurfaceLight,
    surfaceVariant = ZsmeSurfaceVariantLight,
    onSurfaceVariant = ZsmeOnSurfaceVariantLight,
    outline = ZsmeOutlineLight,
    error = ZsmeErrorLight,
    errorContainer = ZsmeErrorContainerLight,
    onErrorContainer = ZsmeOnErrorContainerLight
)

val ZsmeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ZsmePlanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalZsmeDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ZsmeTypography,
            shapes = ZsmeShapes,
            content = content
        )
    }
}
