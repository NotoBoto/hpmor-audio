package io.github.notoboto.unison.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.notoboto.unison.data.Settings

private val Dark = darkColorScheme(
    primary = Color(0xFFE8417A), onPrimary = Color.White,
    primaryContainer = Color(0xFF4A2235), onPrimaryContainer = Color(0xFFFFB1C8),
    secondary = Color(0xFFD9BAC6), onSecondary = Color(0xFF3B2530),
    secondaryContainer = Color(0xFF4A2235), onSecondaryContainer = Color(0xFFFFD9E4),
    tertiary = Color(0xFF7CC0FF),
    background = Color(0xFF1A1520), onBackground = Color(0xFFEFE6F1),
    surface = Color(0xFF1A1520), onSurface = Color(0xFFEFE6F1),
    surfaceVariant = Color(0xFF2F2636), onSurfaceVariant = Color(0xFFA79BAD),
    surfaceContainerLowest = Color(0xFF141018), surfaceContainerLow = Color(0xFF201A26),
    surfaceContainer = Color(0xFF241D2A), surfaceContainerHigh = Color(0xFF2F2636), surfaceContainerHighest = Color(0xFF3A3042),
    outline = Color(0xFF6F6577), outlineVariant = Color(0xFF3A3042),
)
private val Light = lightColorScheme(
    primary = Color(0xFFB8164F), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E4), onPrimaryContainer = Color(0xFF6B0B2E),
    secondaryContainer = Color(0xFFFFD9E4), onSecondaryContainer = Color(0xFF6B0B2E),
    tertiary = Color(0xFF1F6FB2),
    background = Color(0xFFFDF7FA), onBackground = Color(0xFF22191F),
    surface = Color(0xFFFDF7FA), onSurface = Color(0xFF22191F),
    surfaceVariant = Color(0xFFF4E9EF), onSurfaceVariant = Color(0xFF6F6069),
    surfaceContainerLow = Color(0xFFF8EEF3), surfaceContainer = Color(0xFFF4E9EF),
    surfaceContainerHigh = Color(0xFFECDDE5), surfaceContainerHighest = Color(0xFFE2D2DB),
    outline = Color(0xFFA8989F), outlineVariant = Color(0xFFE2D2DB),
)

/** Role colours for the reader, by theme. Index 0 = narrator (plain text). */
val roleColorsDark = listOf(0xFF7CC0FF, 0xFFFF9F73, 0xFF7FD99A, 0xFFEC92E0, 0xFFE9C94F, 0xFF5FD6DD, 0xFFFF8AA8, 0xFFA9ACFF, 0xFFC4D96A).map { Color(it) }
val roleColorsLight = listOf(0xFF1F6FB2, 0xFFB0471F, 0xFF2F8A4A, 0xFF9A3A8E, 0xFF8A6D00, 0xFF00828A, 0xFFC2335B, 0xFF5B5FC7, 0xFF6D7F1F).map { Color(it) }
val LocalIsDark = staticCompositionLocalOf { true }

@Composable
fun isDarkTheme(s: Settings) = when (s.theme) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }

@Composable
fun UnisonTheme(s: Settings, content: @Composable () -> Unit) {
    val dark = isDarkTheme(s)
    val ctx = LocalContext.current
    var scheme: ColorScheme = when {
        s.dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    if (dark && s.amoled) scheme = scheme.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0C0A0E), surfaceContainer = Color(0xFF121014))
    androidx.compose.runtime.CompositionLocalProvider(LocalIsDark provides dark) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
