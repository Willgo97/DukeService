package nl.dejongduke.service.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.annotation.StringRes
import nl.dejongduke.service.R

/**
 * Loosely after the new DUKE service menu: near-black panels (#101010) with a
 * lighter sidebar (#232323), white text for what is active and grey for what is
 * not, and one warm gold accent on the controls. Nothing else is coloured, so a
 * warning still stands out on its own.
 */
private val Gold = Color(0xFFD9B978)
private val GoldDeep = Color(0xFF8A6524)

private val DarkScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF3A2A08),
    primaryContainer = Color(0xFF4A3A16),
    onPrimaryContainer = Color(0xFFF8E6C2),
    inversePrimary = GoldDeep,
    secondary = Color(0xFFB8BCC2),
    onSecondary = Color(0xFF1E2125),
    secondaryContainer = Color(0xFF32363B),
    onSecondaryContainer = Color(0xFFDFE2E6),
    tertiary = Color(0xFFC9CDD3),
    background = Color(0xFF0E0E0F),
    onBackground = Color(0xFFF1F1F2),
    surface = Color(0xFF0E0E0F),
    onSurface = Color(0xFFF1F1F2),
    surfaceVariant = Color(0xFF29292C),
    onSurfaceVariant = Color(0xFFA6A6AB),
    surfaceContainerLowest = Color(0xFF0A0A0B),
    surfaceContainerLow = Color(0xFF141415),
    surfaceContainer = Color(0xFF18181A),
    surfaceContainerHigh = Color(0xFF202022),
    surfaceContainerHighest = Color(0xFF2A2A2D),
    outline = Color(0xFF6E6E74),
    outlineVariant = Color(0xFF323236),
    error = Color(0xFFFF9E93),
    onError = Color(0xFF551710),
    errorContainer = Color(0xFF6E2A21),
    onErrorContainer = Color(0xFFFFDAD5),
)

private val LightScheme = lightColorScheme(
    primary = GoldDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF7E6C4),
    onPrimaryContainer = Color(0xFF2A1D00),
    inversePrimary = Gold,
    secondary = Color(0xFF4E535A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDEE1E5),
    onSecondaryContainer = Color(0xFF161A1E),
    tertiary = Color(0xFF44484E),
    background = Color(0xFFF2F2F3),
    onBackground = Color(0xFF16161A),
    surface = Color(0xFFF2F2F3),
    onSurface = Color(0xFF16161A),
    surfaceVariant = Color(0xFFE4E4E7),
    onSurfaceVariant = Color(0xFF55555C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFB),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFE9E9EB),
    surfaceContainerHighest = Color(0xFFDEDEE1),
    outline = Color(0xFF81818A),
    outlineVariant = Color(0xFFD4D4D9),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFADDDA),
    onErrorContainer = Color(0xFF410E0B),
)

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(letterSpacing = (-0.5).sp),
        headlineSmall = base.headlineSmall.copy(letterSpacing = (-0.4).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
    )
}

enum class ThemeMode(@StringRes val label: Int) {
    System(R.string.system),
    Light(R.string.light),
    Dark(R.string.dark),
}

@Composable
fun DukeTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val scheme = if (dark) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}

/** Colour for a NOTE / CAUTION / WARNING banner. */
@Composable
fun warnColor(level: String): Color = when (level.lowercase()) {
    "danger", "warning" -> MaterialTheme.colorScheme.error
    "caution" -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
