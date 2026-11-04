package com.chatcallguard.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Suraksha ke rang: hara = theek, narangi = dhyan do, laal = khatra */
object StatusColors {
    val Safe = Color(0xFF0E9F6E)
    val Warn = Color(0xFFD97706)
    val Danger = Color(0xFFDC2626)

    fun of(level: Level) = when (level) {
        Level.SAFE -> Safe
        Level.WARN -> Warn
        Level.DANGER -> Danger
    }

    fun ofIssue(level: Int) = if (level >= 2) Danger else Warn
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5BEA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE5FF),
    onPrimaryContainer = Color(0xFF001A5C),
    secondary = Color(0xFF0E9F6E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2F5E6),
    onSecondaryContainer = Color(0xFF00382A),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE8C7),
    onTertiaryContainer = Color(0xFF3D2300),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFFE2E0),
    onErrorContainer = Color(0xFF5C0A0A),
    background = Color(0xFFF4F6FB),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFF4F6FB),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE6EAF2),
    onSurfaceVariant = Color(0xFF5B6475),
    outline = Color(0xFFC3CAD6),
    outlineVariant = Color(0xFFE4E8EF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFBFD),
    surfaceContainer = Color(0xFFF1F4F9),
    surfaceContainerHigh = Color(0xFFEBEEF5),
    surfaceContainerHighest = Color(0xFFE4E8F0)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB4FF),
    onPrimary = Color(0xFF002A8A),
    primaryContainer = Color(0xFF1E3FA8),
    onPrimaryContainer = Color(0xFFDDE5FF),
    secondary = Color(0xFF5EDBA8),
    onSecondary = Color(0xFF003826),
    secondaryContainer = Color(0xFF0B5E44),
    onSecondaryContainer = Color(0xFFD2F5E6),
    tertiary = Color(0xFFFFB95C),
    onTertiary = Color(0xFF472A00),
    tertiaryContainer = Color(0xFF6A3F00),
    onTertiaryContainer = Color(0xFFFFE8C7),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5C0A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFFE2E0),
    background = Color(0xFF0D1220),
    onBackground = Color(0xFFE5E9F2),
    surface = Color(0xFF0D1220),
    onSurface = Color(0xFFE5E9F2),
    surfaceVariant = Color(0xFF2A3142),
    onSurfaceVariant = Color(0xFFA9B2C3),
    outline = Color(0xFF4A5366),
    outlineVariant = Color(0xFF242B3B),
    surfaceContainerLowest = Color(0xFF151B2B),
    surfaceContainerLow = Color(0xFF131929),
    surfaceContainer = Color(0xFF181F30),
    surfaceContainerHigh = Color(0xFF1F2738),
    surfaceContainerHighest = Color(0xFF283043)
)

private val base = Typography()

private val GuardType = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold)
)

private val GuardShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun GuardTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = GuardType,
        shapes = GuardShapes,
        content = content
    )
}
