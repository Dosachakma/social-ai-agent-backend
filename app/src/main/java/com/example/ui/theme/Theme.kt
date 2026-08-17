package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ProfessionalPrimary,
    onPrimary = Color(0xFF381E72),
    primaryContainer = ProfessionalPrimaryContainer,
    onPrimaryContainer = ProfessionalOnPrimaryContainer,
    secondary = ProfessionalPrimary,
    onSecondary = Color(0xFF381E72),
    secondaryContainer = ProfessionalPrimaryContainer,
    onSecondaryContainer = ProfessionalOnPrimaryContainer,
    tertiary = EmeraldTertiary,
    onTertiary = Color.White,
    background = ProfessionalCanvas,
    onBackground = ProfessionalTextPrimary,
    surface = ProfessionalSurface,
    onSurface = ProfessionalTextPrimary,
    surfaceVariant = ProfessionalSurfaceVariant,
    onSurfaceVariant = ProfessionalTextSecondary,
    outline = ProfessionalOutline,
    error = StatusError
)

private val LightColorScheme = darkColorScheme(
    primary = ProfessionalPrimary,
    onPrimary = Color(0xFF381E72),
    primaryContainer = ProfessionalPrimaryContainer,
    onPrimaryContainer = ProfessionalOnPrimaryContainer,
    secondary = ProfessionalPrimary,
    onSecondary = Color(0xFF381E72),
    secondaryContainer = ProfessionalPrimaryContainer,
    onSecondaryContainer = ProfessionalOnPrimaryContainer,
    tertiary = EmeraldTertiary,
    onTertiary = Color.White,
    background = ProfessionalCanvas,
    onBackground = ProfessionalTextPrimary,
    surface = ProfessionalSurface,
    onSurface = ProfessionalTextPrimary,
    surfaceVariant = ProfessionalSurfaceVariant,
    onSurfaceVariant = ProfessionalTextSecondary,
    outline = ProfessionalOutline,
    error = StatusError
)

@Composable
fun SocialAiAgentTheme(
    darkTheme: Boolean = true, // Default to sleek dark mode for AI SaaS vibe
    dynamicColor: Boolean = false, // Enforce custom brand colors
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
