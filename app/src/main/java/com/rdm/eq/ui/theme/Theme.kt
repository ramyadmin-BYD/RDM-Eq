package com.rdm.eq.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rdm.eq.model.ThemeMode

object RdmColors {
    val success = Color(0xFF3DDC97)
    val warning = Color(0xFFFFB547)
    val danger = Color(0xFFFF6B6B)
    val neutral = Color(0xFF8A97A8)
}

private val Dark = darkColorScheme(
    primary = Color(0xFF2EE6D6), onPrimary = Color(0xFF002420),
    primaryContainer = Color(0xFF0E3B38), onPrimaryContainer = Color(0xFFB8FFF7),
    secondary = Color(0xFF1FB5FF), onSecondary = Color(0xFF001E2F),
    secondaryContainer = Color(0xFF16324A), onSecondaryContainer = Color(0xFFCDEBFF),
    background = Color(0xFF0B0F14), onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF121821), onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1A2230), onSurfaceVariant = Color(0xFF9AA8B8),
    surfaceContainer = Color(0xFF141B25), surfaceContainerHigh = Color(0xFF1A2230),
    surfaceContainerLow = Color(0xFF10151D), surfaceContainerHighest = Color(0xFF222C3B),
    outline = Color(0xFF34425A), outlineVariant = Color(0xFF243044),
    error = Color(0xFFFF6B6B),
)

private val Light = lightColorScheme(
    primary = Color(0xFF00897F), onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F2EC), onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF0277BD), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3EAFB), onSecondaryContainer = Color(0xFF001E2F),
    background = Color(0xFFF3F6F9), onBackground = Color(0xFF111820),
    surface = Color.White, onSurface = Color(0xFF111820),
    surfaceVariant = Color(0xFFE6ECF2), onSurfaceVariant = Color(0xFF4A5868),
    surfaceContainer = Color(0xFFF7F9FB), surfaceContainerHigh = Color(0xFFEDF1F5),
    surfaceContainerLow = Color(0xFFFAFBFC), surfaceContainerHighest = Color(0xFFE2E8EE),
    outline = Color(0xFFB4C0CC), outlineVariant = Color(0xFFD5DDE5),
    error = Color(0xFFC62828),
)

private val base = Typography()
// Slightly larger than phone defaults: read at arm's length in a car.
private val CarTypography = Typography(
    headlineSmall = base.headlineSmall.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 18.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 16.sp),
    bodySmall = base.bodySmall.copy(fontSize = 14.sp),
    labelLarge = base.labelLarge.copy(fontSize = 16.sp),
    labelMedium = base.labelMedium.copy(fontSize = 14.sp),
)

@Composable
fun RdmEqTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = CarTypography, content = content)
}
