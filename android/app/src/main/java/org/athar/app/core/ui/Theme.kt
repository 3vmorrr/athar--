package org.athar.app.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.athar.app.R

val QuranFont = FontFamily(Font(R.font.amiri_quran))
@OptIn(ExperimentalTextApi::class)
private val UiFont = FontFamily(
    Font(R.font.noto_sans_arabic, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.noto_sans_arabic, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.noto_sans_arabic, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.noto_sans_arabic, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)
private val lightColors = lightColorScheme(
    primary = Color(0xFF176B55),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEFE7),
    onPrimaryContainer = Color(0xFF0E4D3C),
    secondary = Color(0xFF8A5A2B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E9DA),
    onSecondaryContainer = Color(0xFF5E3A18),
    tertiary = Color(0xFF3A666A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCEDEE),
    onTertiaryContainer = Color(0xFF214A4E),
    background = Color(0xFFF7F5EF),
    onBackground = Color(0xFF202722),
    surface = Color(0xFFFDFCF8),
    onSurface = Color(0xFF202722),
    surfaceVariant = Color(0xFFE8ECE7),
    onSurfaceVariant = Color(0xFF526058),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFAF6),
    surfaceContainer = Color(0xFFF1F2EC),
    surfaceContainerHigh = Color(0xFFE9ECE6),
    surfaceContainerHighest = Color(0xFFE2E7E1),
    surfaceDim = Color(0xFFDADFD9),
    surfaceBright = Color(0xFFFDFCF8),
    outline = Color(0xFF78837C),
    outlineVariant = Color(0xFFD7DDD7),
    inverseSurface = Color(0xFF2A322D),
    inverseOnSurface = Color(0xFFF2F5F1),
    inversePrimary = Color(0xFF8FD8B9),
    surfaceTint = Color(0xFF176B55),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    scrim = Color(0xFF0B1410),
)

private val darkColors = darkColorScheme(
    primary = Color(0xFF8FD8B9),
    onPrimary = Color(0xFF0D3629),
    primaryContainer = Color(0xFF174F3E),
    onPrimaryContainer = Color(0xFFC2F3DD),
    secondary = Color(0xFFE9BF8E),
    onSecondary = Color(0xFF442A10),
    secondaryContainer = Color(0xFF5B4025),
    onSecondaryContainer = Color(0xFFFFDFC0),
    tertiary = Color(0xFFA7CDD0),
    onTertiary = Color(0xFF12383C),
    tertiaryContainer = Color(0xFF2C5054),
    onTertiaryContainer = Color(0xFFC5EAEC),
    background = Color(0xFF0E1512),
    onBackground = Color(0xFFE8EEE9),
    surface = Color(0xFF121B17),
    onSurface = Color(0xFFE8EEE9),
    surfaceVariant = Color(0xFF2C3932),
    onSurfaceVariant = Color(0xFFC4CEC7),
    surfaceContainerLowest = Color(0xFF09100D),
    surfaceContainerLow = Color(0xFF111A16),
    surfaceContainer = Color(0xFF17231E),
    surfaceContainerHigh = Color(0xFF203028),
    surfaceContainerHighest = Color(0xFF293A32),
    surfaceDim = Color(0xFF0E1512),
    surfaceBright = Color(0xFF33463B),
    outline = Color(0xFF8C9A91),
    outlineVariant = Color(0xFF3A4A41),
    inverseSurface = Color(0xFFE1E7E2),
    inverseOnSurface = Color(0xFF25302A),
    inversePrimary = Color(0xFF176B55),
    surfaceTint = Color(0xFF8FD8B9),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
)

private val typography = Typography(
    displayLarge = Typography().displayLarge.copy(fontFamily = UiFont, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displayMedium = Typography().displayMedium.copy(fontFamily = UiFont, fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    displaySmall = Typography().displaySmall.copy(fontFamily = UiFont, fontWeight = FontWeight.Bold),
    headlineLarge = Typography().headlineLarge.copy(fontFamily = UiFont, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp, letterSpacing = 0.sp),
    headlineMedium = Typography().headlineMedium.copy(fontFamily = UiFont, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineSmall = Typography().headlineSmall.copy(fontFamily = UiFont, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp, letterSpacing = 0.sp),
    titleLarge = Typography().titleLarge.copy(fontFamily = UiFont, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp, letterSpacing = 0.sp),
    titleMedium = Typography().titleMedium.copy(fontFamily = UiFont, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp, letterSpacing = 0.sp),
    titleSmall = Typography().titleSmall.copy(fontFamily = UiFont, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp, letterSpacing = 0.sp),
    bodyLarge = Typography().bodyLarge.copy(fontFamily = UiFont, fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 26.sp, letterSpacing = 0.sp),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = UiFont, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 23.sp, letterSpacing = 0.sp),
    bodySmall = Typography().bodySmall.copy(fontFamily = UiFont, fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp, letterSpacing = 0.sp),
    labelLarge = Typography().labelLarge.copy(fontFamily = UiFont, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp, letterSpacing = 0.sp),
    labelMedium = Typography().labelMedium.copy(fontFamily = UiFont, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.sp),
    labelSmall = Typography().labelSmall.copy(fontFamily = UiFont, fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp, letterSpacing = 0.sp),
)
@Composable fun AtharTheme(theme: String, content: @Composable () -> Unit) {
    val dark = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (dark) darkColors else lightColors, typography = typography, shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)), content = content)
}
