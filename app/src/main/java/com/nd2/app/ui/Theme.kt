package com.nd2.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colori del marchio, coerenti con l'icona (viola notte + giallo luna). */
object Brand {
    val Night = Color(0xFF3F2A8C)
    val Violet = Color(0xFF5B45C2)
    val Lavender = Color(0xFF8E7BEA)
    val Moon = Color(0xFFFFD54F)

    val activeGradient = Brush.linearGradient(listOf(Color(0xFF6A54D6), Night, Color(0xFF241A57)))

    /** Coppie (sfondo, testo) per gli avatar, scelte in base al nome. */
    private val avatarLight = listOf(
        Color(0xFFE6DEFF) to Color(0xFF3F2A8C),
        Color(0xFFFFE1D6) to Color(0xFF8A3414),
        Color(0xFFD5F0E3) to Color(0xFF125C3B),
        Color(0xFFFFF0C2) to Color(0xFF6B4E00),
        Color(0xFFD8ECFF) to Color(0xFF0F4C80),
        Color(0xFFFFDDEB) to Color(0xFF85224E),
    )
    private val avatarDark = listOf(
        Color(0xFF3B2F72) to Color(0xFFE6DEFF),
        Color(0xFF5C2A17) to Color(0xFFFFDBCF),
        Color(0xFF1C4A35) to Color(0xFFC8F0DC),
        Color(0xFF524012) to Color(0xFFFFE9A8),
        Color(0xFF173E63) to Color(0xFFD2E8FF),
        Color(0xFF5E2240) to Color(0xFFFFD9E7),
    )

    fun avatarColors(name: String, dark: Boolean): Pair<Color, Color> {
        val list = if (dark) avatarDark else avatarLight
        return list[Math.floorMod(name.lowercase().hashCode(), list.size)]
    }
}

private val Light = lightColorScheme(
    primary = Brand.Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF21005E),
    secondary = Color(0xFF625B71),
    secondaryContainer = Color(0xFFE8E0F8),
    onSecondaryContainer = Color(0xFF1E192B),
    tertiary = Color(0xFF7A5900),
    tertiaryContainer = Color(0xFFFFEDC2),
    onTertiaryContainer = Color(0xFF261A00),
    background = Color(0xFFF8F6FD),
    onBackground = Color(0xFF1C1B22),
    surface = Color(0xFFF8F6FD),
    onSurface = Color(0xFF1C1B22),
    onSurfaceVariant = Color(0xFF5F5B6E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF1EEF9),
    surfaceContainerHigh = Color(0xFFEAE6F5),
    surfaceContainerHighest = Color(0xFFE3DFF0),
    outlineVariant = Color(0xFFE0DCEB),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFCABEFF),
    onPrimary = Color(0xFF2E128F),
    primaryContainer = Color(0xFF442BA7),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFFCBC3DC),
    secondaryContainer = Color(0xFF3A3448),
    onSecondaryContainer = Color(0xFFE8E0F8),
    tertiary = Color(0xFFFFD54F),
    tertiaryContainer = Color(0xFF4A3A0F),
    onTertiaryContainer = Color(0xFFFFEDC2),
    background = Color(0xFF121018),
    onBackground = Color(0xFFE6E1EE),
    surface = Color(0xFF121018),
    onSurface = Color(0xFFE6E1EE),
    onSurfaceVariant = Color(0xFFADA8BC),
    surfaceContainerLowest = Color(0xFF0E0C14),
    surfaceContainerLow = Color(0xFF1B1823),
    surfaceContainer = Color(0xFF201D29),
    surfaceContainerHigh = Color(0xFF2A2634),
    surfaceContainerHighest = Color(0xFF35313F),
    outlineVariant = Color(0xFF39354A),
)

private val Nd2Shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

private val Nd2Typography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun Nd2Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = Nd2Shapes,
        typography = Nd2Typography,
        content = content,
    )
}
