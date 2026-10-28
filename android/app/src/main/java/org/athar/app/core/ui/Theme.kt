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
private val lightColors = lightColorScheme(primary = Color(0xFF24694F), onPrimary = Color.White, primaryContainer = Color(0xFFE3F1E8), onPrimaryContainer = Color(0xFF19563F), secondary = Color(0xFFA85F25), secondaryContainer = Color(0xFFF7EBDD), background = Color(0xFFF4F3ED), onBackground = Color(0xFF202923), surface = Color(0xFFFBFCF8), surfaceContainer = Color(0xFFE9F0E9), surfaceContainerLow = Color.White, onSurface = Color(0xFF202923), onSurfaceVariant = Color(0xFF3F4E44), outline = Color(0xFF68766E), outlineVariant = Color(0xFFD9E2D9), error = Color(0xFFAC403B), errorContainer = Color(0xFFF8E8E5))
private val darkColors = darkColorScheme(primary = Color(0xFF8ADBAF), onPrimary = Color(0xFF14261C), primaryContainer = Color(0xFF244635), onPrimaryContainer = Color(0xFFA5E7C1), secondary = Color(0xFFF0BD78), secondaryContainer = Color(0xFF463722), background = Color(0xFF101815), onBackground = Color(0xFFF2F6F1), surface = Color(0xFF17231E), surfaceContainer = Color(0xFF283A31), surfaceContainerLow = Color(0xFF1E2D26), onSurface = Color(0xFFF2F6F1), onSurfaceVariant = Color(0xFFCDD7D0), outline = Color(0xFFA8B7AC), outlineVariant = Color(0xFF40564A), error = Color(0xFFF19A8E), errorContainer = Color(0xFF482D2B))
// Define every Material surface role so default purple surfaces never enter Athar.
private val completeLightColors = lightColors.copy(
    onSecondary = Color.White, onSecondaryContainer = Color(0xFF673B18),
    tertiary = Color(0xFF356A72), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDEFF0), onTertiaryContainer = Color(0xFF194A51),
    surfaceDim = Color(0xFFD9E0D8), surfaceBright = Color(0xFFFBFCF8),
    surfaceContainerLowest = Color.White, surfaceContainerHigh = Color(0xFFE1E9E0),
    surfaceContainerHighest = Color(0xFFD9E2D9), surfaceVariant = Color(0xFFE1E9E0),
    inverseSurface = Color(0xFF283A31), inverseOnSurface = Color(0xFFF2F6F1),
    inversePrimary = Color(0xFF8ADBAF), surfaceTint = Color(0xFF24694F),
    onError = Color.White, onErrorContainer = Color(0xFF6D2421), scrim = Color(0xFF101815),
)
private val completeDarkColors = darkColors.copy(
    onSecondary = Color(0xFF36230F), onSecondaryContainer = Color(0xFFF4D9B5),
    tertiary = Color(0xFFA2D2D8), onTertiary = Color(0xFF143A40),
    tertiaryContainer = Color(0xFF284E54), onTertiaryContainer = Color(0xFFCEEDF0),
    surfaceDim = Color(0xFF101815), surfaceBright = Color(0xFF35483D),
    surfaceContainerLowest = Color(0xFF0C130F), surfaceContainerHigh = Color(0xFF304339),
    surfaceContainerHighest = Color(0xFF394D42), surfaceVariant = Color(0xFF304339),
    inverseSurface = Color(0xFFE1E9E0), inverseOnSurface = Color(0xFF202923),
    inversePrimary = Color(0xFF24694F), surfaceTint = Color(0xFF8ADBAF),
    onError = Color(0xFF421A16), onErrorContainer = Color(0xFFFFDAD5), scrim = Color(0xFF000000),
)
private val typography = Typography(
    displayLarge = Typography().displayLarge.copy(fontFamily = UiFont, fontSize = 48.sp, lineHeight = 68.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    displayMedium = Typography().displayMedium.copy(fontFamily = UiFont, fontSize = 40.sp, lineHeight = 56.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    displaySmall = Typography().displaySmall.copy(fontFamily = UiFont, fontSize = 34.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    headlineLarge = Typography().headlineLarge.copy(fontFamily = UiFont, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp),
    headlineMedium = Typography().headlineMedium.copy(fontFamily = UiFont, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp),
    headlineSmall = Typography().headlineSmall.copy(fontFamily = UiFont, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp),
    titleLarge = Typography().titleLarge.copy(fontFamily = UiFont, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
    titleMedium = Typography().titleMedium.copy(fontFamily = UiFont, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleSmall = Typography().titleSmall.copy(fontFamily = UiFont, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    bodyLarge = Typography().bodyLarge.copy(fontFamily = UiFont, fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 28.sp, letterSpacing = 0.sp),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = UiFont, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 25.sp, letterSpacing = 0.sp),
    bodySmall = Typography().bodySmall.copy(fontFamily = UiFont, fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp, letterSpacing = 0.sp),
    labelLarge = Typography().labelLarge.copy(fontFamily = UiFont, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp, letterSpacing = 0.sp),
    labelMedium = Typography().labelMedium.copy(fontFamily = UiFont, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp, letterSpacing = 0.sp),
    labelSmall = Typography().labelSmall.copy(fontFamily = UiFont, fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.sp),
)
@Composable fun AtharTheme(theme: String, content: @Composable () -> Unit) {
    val dark = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (dark) completeDarkColors else completeLightColors, typography = typography, shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)), content = content)
}
