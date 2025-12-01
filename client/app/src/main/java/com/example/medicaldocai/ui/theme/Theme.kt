package com.example.medicaldocai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import android.os.Build
import androidx.compose.ui.platform.LocalContext

// Healthcare-inspired colors
private val HealthcarePrimary = Color(0xFF1976D2) // Professional Blue
private val HealthcareSecondary = Color(0xFF4CAF50) // Medical Green
private val HealthcareTertiary = Color(0xFFFF9800) // Alert Orange
private val HealthcareBackground = Color(0xFFFAFAFA) // Clean White
private val HealthcareSurface = Color(0xFFFFFFFF) // Pure White

private val DarkHealthcarePrimary = Color(0xFF90CAF9) // Light Blue
private val DarkHealthcareSecondary = Color(0xFF81C784) // Light Green
private val DarkHealthcareTertiary = Color(0xFFFFB74D) // Light Orange

private val LightColorScheme = lightColorScheme(
    primary = HealthcarePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F2FD),
    onPrimaryContainer = Color(0xFF0D47A1),

    secondary = HealthcareSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F5E9),
    onSecondaryContainer = Color(0xFF1B5E20),

    tertiary = HealthcareTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0B2),
    onTertiaryContainer = Color(0xFFE65100),

    error = Color(0xFFD32F2F),
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFFB71C1C),

    background = HealthcareBackground,
    onBackground = Color(0xFF212121),

    surface = HealthcareSurface,
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF757575),

    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFE0E0E0),
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkHealthcarePrimary,
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF1565C0),
    onPrimaryContainer = DarkHealthcarePrimary,

    secondary = DarkHealthcareSecondary,
    onSecondary = Color(0xFF1B5E20),
    secondaryContainer = Color(0xFF2E7D32),
    onSecondaryContainer = DarkHealthcareSecondary,

    tertiary = DarkHealthcareTertiary,
    onTertiary = Color(0xFFE65100),
    tertiaryContainer = Color(0xFFF57C00),
    onTertiaryContainer = DarkHealthcareTertiary,

    background = Color(0xFF121212),
    onBackground = Color(0xFFFFFFFF),

    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF424242),
    onSurfaceVariant = Color(0xFFBDBDBD),
)

@Composable
fun MedicalAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
        typography = MedicalTypography,
        shapes = MedicalShapes,
        content = content
    )
}