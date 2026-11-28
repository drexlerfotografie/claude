package de.mediclever.schichtclever.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mediclever.schichtclever.R
import de.mediclever.schichtclever.data.AppData
import de.mediclever.schichtclever.data.ThemeMode

// mediclever-Markenfarben (aus dem Website-Theme übernommen)
object Mc {
    val Red = Color(0xFFB9303F)
    val Madder = Color(0xFF962331)
    val Rusty = Color(0xFFCC3F4F)
    val RedOnDark = Color(0xFFD9475A)
    val Dark = Color(0xFF11202C)
    val Text = Color(0xFF2D323C)
    val Grey = Color(0xFF5A606B)
    val Line = Color(0xFFDDE0E5)
    val Bg = Color(0xFFF4F5F7)

    val NightBg = Color(0xFF0B1620)
    val NightCard = Color(0xFF11202C)
    val NightLine = Color(0xFF1E3242)
    val NightText = Color(0xFFE8ECF0)
    val NightMuted = Color(0xFF8FA0AE)

    val Green = Color(0xFF2E9E6A)
    val Amber = Color(0xFFE0A030)
}

@Immutable
data class ExtraColors(val line: Color, val muted: Color, val success: Color, val warning: Color, val accent: Color)

val LocalExtra = staticCompositionLocalOf { ExtraColors(Mc.Line, Mc.Grey, Mc.Green, Mc.Amber, Mc.Red) }

private val DarkColors = darkColorScheme(
    primary = Mc.Red,
    onPrimary = Color.White,
    primaryContainer = Mc.Madder,
    onPrimaryContainer = Color.White,
    secondary = Mc.RedOnDark,
    background = Mc.NightBg,
    onBackground = Mc.NightText,
    surface = Mc.NightBg,
    onSurface = Mc.NightText,
    surfaceVariant = Mc.NightCard,
    onSurfaceVariant = Mc.NightMuted,
    surfaceContainer = Mc.NightCard,
    surfaceContainerHigh = Color(0xFF162A39),
    surfaceContainerLow = Mc.NightCard,
    outline = Mc.NightLine,
    outlineVariant = Mc.NightLine,
    error = Mc.RedOnDark,
)

private val LightColors = lightColorScheme(
    primary = Mc.Red,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF7E3E6),
    onPrimaryContainer = Mc.Madder,
    secondary = Mc.Madder,
    background = Color(0xFFFCFCFC),
    onBackground = Mc.Text,
    surface = Color(0xFFFCFCFC),
    onSurface = Mc.Dark,
    surfaceVariant = Color.White,
    onSurfaceVariant = Mc.Grey,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Mc.Bg,
    surfaceContainerLow = Color.White,
    outline = Mc.Line,
    outlineVariant = Mc.Line,
    error = Mc.Red,
)

val Oswald = FontFamily(
    Font(R.font.oswald_medium, FontWeight.Medium),
    Font(R.font.oswald_semibold, FontWeight.SemiBold),
)

private val base = Typography()
private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.SemiBold, fontSize = 56.sp, lineHeight = 60.sp),
    displayMedium = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.SemiBold, fontSize = 42.sp, lineHeight = 46.sp),
    displaySmall = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineLarge = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold),
    labelSmall = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 1.2.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp),
)

/** Entscheidet anhand der Einstellung (und ggf. der laufenden Schicht), ob dunkel dargestellt wird. */
@Composable
fun AppData.useDark(): Boolean = when (settings.theme) {
    ThemeMode.DUNKEL -> true
    ThemeMode.HELL -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.AUTO_SCHICHT -> activeShift?.let { it.type.night } ?: isSystemInDarkTheme()
}

@Composable
fun SchichtCleverTheme(dark: Boolean, content: @Composable () -> Unit) {
    val extra = if (dark) ExtraColors(Mc.NightLine, Mc.NightMuted, Mc.Green, Mc.Amber, Mc.RedOnDark)
    else ExtraColors(Mc.Line, Mc.Grey, Mc.Green, Mc.Amber, Mc.Red)
    CompositionLocalProvider(LocalExtra provides extra) {
        val scheme = if (dark) DarkColors else LightColors
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes) {
            // Standard-Textfarbe passend zum Hintergrund, auch außerhalb von Surface/Scaffold
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}
