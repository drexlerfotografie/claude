package de.mediclever.tagebuch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mediclever.tagebuch.core.model.ThemeMode
import de.mediclever.tagebuch.core.model.UserSettings

/** Farben aus dem MediClever-Styleguide 2.0. */
object Brand {
    val RustyRed = Color(0xFFCC3F4F)
    val Cardinal = Color(0xFFB9303F)
    val Madder = Color(0xFF962331)
    val RichBlack = Color(0xFF11202C)
    val Gunmetal = Color(0xFF2D323C)
    val Paper = Color(0xFFF4F5F7)
    val Line = Color(0xFFDDE1E5)
    val Muted = Color(0xFF66717B)
    val Success = Color(0xFF2D7553)
}

private val Light = lightColorScheme(
    primary = Brand.Cardinal, onPrimary = Color.White,
    primaryContainer = Color(0xFFFBE4E7), onPrimaryContainer = Brand.Madder,
    secondary = Brand.Gunmetal, onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E9ED), onSecondaryContainer = Brand.RichBlack,
    tertiary = Brand.Success, onTertiary = Color.White,
    background = Brand.Paper, onBackground = Brand.RichBlack,
    surface = Color.White, onSurface = Brand.RichBlack,
    surfaceVariant = Color(0xFFECEEF1), onSurfaceVariant = Color(0xFF55606A),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF7F8FA), surfaceContainerHigh = Color(0xFFEFF1F4), surfaceContainerHighest = Color(0xFFE6E9ED),
    outline = Color(0xFF8A939B), outlineVariant = Brand.Line,
    error = Brand.Madder, onError = Color.White,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFEF8A95), onPrimary = Color(0xFF3F0911),
    primaryContainer = Brand.Madder, onPrimaryContainer = Color(0xFFFFDADD),
    secondary = Color(0xFFC3CAD1), onSecondary = Brand.RichBlack,
    secondaryContainer = Color(0xFF2A3B49), onSecondaryContainer = Color(0xFFE2E7EC),
    tertiary = Color(0xFF7FCFA5), onTertiary = Color(0xFF07301E),
    background = Brand.RichBlack, onBackground = Color(0xFFE9EDF0),
    surface = Color(0xFF192A37), onSurface = Color(0xFFE9EDF0),
    surfaceVariant = Color(0xFF243746), onSurfaceVariant = Color(0xFFB4BEC7),
    surfaceContainerLowest = Color(0xFF0C1821), surfaceContainerLow = Color(0xFF192A37),
    surfaceContainer = Color(0xFF1D303E), surfaceContainerHigh = Color(0xFF243746), surfaceContainerHighest = Color(0xFF2C4152),
    outline = Color(0xFF8795A1), outlineVariant = Color(0xFF34495A),
    error = Color(0xFFFFB3B8), onError = Color(0xFF5C0A14),
)

/** Farbschemata fuer die fuenf Stimmungsstufen, Stufe 1 (beste) zuerst. Eigene Namen und Werte. */
enum class MoodScheme(val key: String, val label: String, val colors: List<Color>) {
    KLAR("klar", "Klar", listOf(0xFF2D8A5F, 0xFF78A94E, 0xFFD9A62E, 0xFFDD7F37, 0xFFB9303F).map { Color(it) }),
    RUHIG("ruhig", "Ruhig", listOf(0xFF1F7A8C, 0xFF4F9DB5, 0xFF8D99AE, 0xFF8B7BB0, 0xFF6A4C93).map { Color(it) }),
    KONTRAST("kontrast", "Kontrastreich", listOf(0xFF009E73, 0xFF56B4E9, 0xFFC9B800, 0xFFE69F00, 0xFFD55E00).map { Color(it) }),
    WARM("warm", "Warm", listOf(0xFFE0A100, 0xFFE2803C, 0xFFC9655A, 0xFF9A5073, 0xFF5E4675).map { Color(it) });

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: KLAR
    }
}

/** Zwoelf Farben fuer Gruppen, Skalen und Ziele. */
val GroupPalette: List<Color> = listOf(
    0xFFB9303F, 0xFFDD7F37, 0xFFD9A62E, 0xFF78A94E, 0xFF2D8A5F, 0xFF1F7A8C,
    0xFF4F7FC0, 0xFF6A4C93, 0xFFB05690, 0xFF8A6E52, 0xFF5C6B78, 0xFF2D323C,
).map { Color(it) }

fun rawGroupColor(index: Int?): Color = GroupPalette[((index ?: 10) % GroupPalette.size + GroupPalette.size) % GroupPalette.size]

/** Palettenfarbe, im dunklen Modus aufgehellt, damit Symbole und Linien lesbar bleiben. */
@Composable
fun groupColor(index: Int?): Color {
    val c = rawGroupColor(index)
    return if (LocalDiaryColors.current.dark) androidx.compose.ui.graphics.lerp(c, Color.White, 0.38f) else c
}

@Immutable
data class DiaryColors(
    val mood: List<Color>,
    val dark: Boolean,
) {
    /** Farbe einer Stufe 1 bis 5; ausserhalb davon neutral. */
    fun level(level: Int?): Color = mood.getOrNull((level ?: 0) - 1) ?: Color(0xFF8A939B)
}

/** Lesbare Schriftfarbe auf einer farbigen Flaeche. */
fun onColor(bg: Color): Color = if (bg.luminance() > 0.5f) Brand.RichBlack else Color.White

val LocalDiaryColors = staticCompositionLocalOf { DiaryColors(MoodScheme.KLAR.colors, false) }

fun parseHex(hex: String): Color? = runCatching {
    val v = hex.removePrefix("#")
    Color((if (v.length == 6) "FF$v" else v).toLong(16))
}.getOrNull()

fun moodColors(settings: UserSettings): List<Color> {
    if (settings.moodScheme == "custom") {
        val c = settings.customMoodColors.mapNotNull { parseHex(it) }
        if (c.size == 5) return c
    }
    return MoodScheme.of(settings.moodScheme).colors
}

private fun typography(family: FontFamily): Typography {
    val b = Typography()
    fun TextStyle.f(weight: FontWeight? = null) = copy(fontFamily = family, fontWeight = weight ?: fontWeight)
    return Typography(
        displaySmall = b.displaySmall.f(FontWeight.Black),
        headlineLarge = b.headlineLarge.f(FontWeight.ExtraBold),
        headlineMedium = b.headlineMedium.f(FontWeight.ExtraBold),
        headlineSmall = b.headlineSmall.f(FontWeight.Bold),
        titleLarge = b.titleLarge.f(FontWeight.Bold),
        titleMedium = b.titleMedium.f(FontWeight.Bold),
        titleSmall = b.titleSmall.f(FontWeight.Bold),
        bodyLarge = b.bodyLarge.f().copy(lineHeight = 25.sp),
        bodyMedium = b.bodyMedium.f().copy(lineHeight = 21.sp),
        bodySmall = b.bodySmall.f(),
        labelLarge = b.labelLarge.f(FontWeight.SemiBold),
        labelMedium = b.labelMedium.f(FontWeight.SemiBold),
        labelSmall = b.labelSmall.f(FontWeight.SemiBold),
    )
}

val FontChoices = listOf("roboto" to "Roboto (Standard)", "serif" to "Serifenschrift", "mono" to "Schreibmaschine")

@Composable
fun TagebuchTheme(settings: UserSettings = UserSettings(), forceDark: Boolean? = null, content: @Composable () -> Unit) {
    val dark = forceDark ?: when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val family = when (settings.fontChoice) {
        "serif" -> FontFamily.Serif
        "mono" -> FontFamily.Monospace
        else -> FontFamily.Default // Roboto, die Markenschrift, ist die Systemschrift von Android
    }
    CompositionLocalProvider(LocalDiaryColors provides DiaryColors(moodColors(settings), dark)) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = typography(family),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}
