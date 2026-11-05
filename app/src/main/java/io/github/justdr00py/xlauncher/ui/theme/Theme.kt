package io.github.justdr00py.xlauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.tv.material3.LocalContentColor
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

@Immutable
data class Accent(val name: String, val color: Color)

@Immutable
data class Backdrop(val name: String, val top: Color, val bottom: Color, val usesHeroArt: Boolean = false)

val Accents = listOf(
    Accent("Azure", Color(0xFF4C8DFF)),
    Accent("Violet", Color(0xFF9D7BFF)),
    Accent("Teal", Color(0xFF2EC4B6)),
    Accent("Amber", Color(0xFFFFB547)),
)

val Backgrounds = listOf(
    Backdrop("Midnight", Color(0xFF0A0E1A), Color(0xFF151C30)),
    Backdrop("Graphite", Color(0xFF0D0D0F), Color(0xFF1D1D22)),
    Backdrop("Deep Sea", Color(0xFF03121A), Color(0xFF0B2A38)),
    Backdrop("Featured art", Color(0xFF0A0E1A), Color(0xFF0A0E1A), usesHeroArt = true),
)

fun accentAt(index: Int) = Accents[index.mod(Accents.size)]
fun backdropAt(index: Int) = Backgrounds[index.mod(Backgrounds.size)]

private val XTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun XTheme(accentIndex: Int, content: @Composable () -> Unit) {
    val accent = accentAt(accentIndex).color
    val scheme = remember(accent) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF05070D),
            secondary = accent,
            border = accent,
            background = Color(0xFF0A0E1A),
            onBackground = Color(0xFFE9ECF3),
            surface = Color(0xFF161B28),
            onSurface = Color(0xFFE9ECF3),
            surfaceVariant = Color(0xFF232A3B),
            onSurfaceVariant = Color(0xFFAEB5C6),
            inverseSurface = Color(0xFFF2F4F8),
            inverseOnSurface = Color(0xFF0A0E1A),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = XTypography) {
        // Text drawn straight on the backdrop (outside any Surface) defaults to black otherwise.
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}
