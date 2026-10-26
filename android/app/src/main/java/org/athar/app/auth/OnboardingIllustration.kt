package org.athar.app.auth

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform

/** Original Athar illustrations drawn natively; no external animation or raster assets. */
@Composable fun OnboardingIllustration(page: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier) {
        withTransform({ scale(size.width / 320f, size.height / 240f, Offset.Zero) }) {
            val green = colors.primary; val soft = colors.primaryContainer; val paper = colors.surfaceContainerLow; val gold = colors.secondary
            drawRoundRect(soft.copy(alpha = 0.7f), Offset(76f, 14f), Size(168f, 210f), CornerRadius(84f, 84f))
            drawCircle(gold.copy(alpha = 0.2f), 5f, Offset(263f, 64f)); drawCircle(green.copy(alpha = 0.15f), 8f, Offset(53f, 163f))
            when (page) {
                0 -> {
                    val cover = Path().apply { moveTo(55f, 79f); quadraticTo(108f, 55f, 160f, 83f); quadraticTo(212f, 55f, 265f, 79f); lineTo(265f, 188f); quadraticTo(212f, 169f, 160f, 199f); quadraticTo(108f, 169f, 55f, 188f); close() }
                    drawPath(cover, green)
                    val pages = Path().apply { moveTo(65f, 69f); quadraticTo(112f, 51f, 160f, 80f); quadraticTo(208f, 51f, 255f, 69f); lineTo(255f, 174f); quadraticTo(208f, 158f, 160f, 187f); quadraticTo(112f, 158f, 65f, 174f); close() }
                    drawPath(pages, paper); drawLine(green.copy(alpha = 0.3f), Offset(160f, 82f), Offset(160f, 185f), 2f)
                    for (line in 0..3) { val y = 97f + line * 18f; drawLine(green.copy(alpha = 0.25f), Offset(82f, y - 3), Offset(142f, y + 3), 3f); drawLine(green.copy(alpha = 0.25f), Offset(179f, y + 3), Offset(237f, y - 3), 3f) }
                    drawPath(Path().apply { moveTo(218f, 65f); lineTo(232f, 65f); lineTo(232f, 112f); lineTo(225f, 107f); lineTo(218f, 112f); close() }, gold)
                }
                1 -> {
                    drawRoundRect(green.copy(alpha = 0.12f), Offset(47f, 93f), Size(171f, 108f), CornerRadius(16f))
                    drawRoundRect(gold.copy(alpha = 0.22f), Offset(105f, 46f), Size(171f, 116f), CornerRadius(16f))
                    drawRoundRect(paper, Offset(71f, 65f), Size(177f, 130f), CornerRadius(18f))
                    drawRoundRect(green.copy(alpha = 0.18f), Offset(71f, 65f), Size(177f, 130f), CornerRadius(18f), style = Stroke(2f))
                    drawCircle(soft, 23f, Offset(159f, 106f)); drawCircle(green, 8f, Offset(159f, 101f)); drawArc(green, 185f, 170f, true, Offset(146f, 109f), Size(26f, 17f))
                    drawRoundRect(green.copy(alpha = 0.5f), Offset(104f, 145f), Size(111f, 5f), CornerRadius(3f))
                    drawRoundRect(green.copy(alpha = 0.18f), Offset(120f, 162f), Size(80f, 4f), CornerRadius(2f))
                    drawCircle(gold, 6f, Offset(237f, 68f))
                }
                2 -> {
                    drawCircle(gold, 33f, Offset(156f, 67f)); drawCircle(soft, 31f, Offset(169f, 57f))
                    drawPath(Path().apply { moveTo(96f, 200f); lineTo(96f, 155f); quadraticTo(100f, 123f, 160f, 106f); quadraticTo(220f, 123f, 224f, 155f); lineTo(224f, 200f); close() }, green)
                    drawPath(Path().apply { moveTo(112f, 200f); lineTo(112f, 157f); quadraticTo(117f, 137f, 160f, 122f); quadraticTo(203f, 137f, 208f, 157f); lineTo(208f, 200f); close() }, paper)
                    drawLine(green.copy(alpha = 0.3f), Offset(75f, 201f), Offset(246f, 201f), 3f)
                    for ((x,y) in listOf(87f to 68f, 225f to 93f, 207f to 42f)) { drawLine(gold, Offset(x - 4,y), Offset(x + 4,y), 2f); drawLine(gold, Offset(x,y - 4), Offset(x,y + 4), 2f) }
                }
                else -> {
                    drawRoundRect(green, Offset(87f, 43f), Size(139f, 158f), CornerRadius(18f))
                    drawRoundRect(paper, Offset(98f, 43f), Size(128f, 158f), CornerRadius(16f))
                    for (line in 0..3) drawRoundRect(green.copy(alpha = 0.15f), Offset(116f, 75f + line * 20f), Size(if (line == 3) 48f else 84f, 4f), CornerRadius(2f))
                    drawRoundRect(gold, Offset(176f, 143f), Size(65f, 55f), CornerRadius(12f))
                    drawRoundRect(gold, Offset(190f, 121f), Size(37f, 45f), CornerRadius(18f), style = Stroke(7f))
                    drawCircle(colors.onSecondary, 5f, Offset(209f, 167f)); drawLine(colors.onSecondary, Offset(209f, 169f), Offset(209f, 180f), 4f)
                }
            }
        }
    }
}
