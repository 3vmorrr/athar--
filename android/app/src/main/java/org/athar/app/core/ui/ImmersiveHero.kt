package org.athar.app.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AtharHeroArtwork {
    Home,
    Quran,
    Knowledge,
    Adhkar,
    NightPrayer,
    Upcoming,
    Books,
    Audio,
    Generic,
}

enum class AtharHeroStatusTone {
    Neutral,
    Offline,
    Warning,
    Live,
}

@Immutable
data class AtharHeroPresentation(
    val artwork: AtharHeroArtwork,
    val title: String,
    val supportingText: String? = null,
    val eyebrow: String? = null,
    val statusText: String? = null,
    val statusTone: AtharHeroStatusTone = AtharHeroStatusTone.Neutral,
    val primaryActionLabel: String? = null,
    val secondaryActionLabel: String? = null,
)

/**
 * Presentation-only immersive hero.
 *
 * Feature screens own all data, authentication, persistence and callbacks. This component never
 * reads repositories and never invents continuation/progress. [collapseFraction] is supplied by
 * the screen's existing scroll owner so this shared primitive does not create nested scrolling.
 */
@Composable
fun AtharImmersiveHero(
    presentation: AtharHeroPresentation,
    modifier: Modifier = Modifier,
    collapseFraction: Float = 0f,
    reduceMotion: Boolean = false,
    onPrimaryAction: (() -> Unit)? = null,
    onSecondaryAction: (() -> Unit)? = null,
    trailing: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val reduced = reduceMotion || reducedMotion()
    val target = collapseFraction.coerceIn(0f, 1f)
    val animatedCollapse by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduced) snap() else tween(AtharMotion.Normal, easing = AtharMotion.Standard),
        label = "athar-hero-collapse",
    )
    val artScale by animateFloatAsState(
        targetValue = if (reduced) 1f else 1.03f - (0.03f * (1f - animatedCollapse)),
        animationSpec = if (reduced) snap() else tween(AtharMotion.Normal, easing = AtharMotion.Enter),
        label = "athar-hero-art-scale",
    )
    val artAlpha = 1f - (animatedCollapse * 0.42f)
    val expandedPadding = AtharSpacing.Xxl
    val verticalPadding = (24f - (8f * animatedCollapse)).dp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 148.dp)
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)),
        color = Color.Transparent,
        contentColor = Color.White,
    ) {
        Box(
            Modifier
                .background(heroBrush(presentation.artwork))
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.02f),
                            0.6f to Color.Black.copy(alpha = 0.10f),
                            1f to Color.Black.copy(alpha = 0.42f),
                        ),
                    )
                }
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            AtharHeroArtworkLayer(
                presentation.artwork,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = artScale
                        scaleY = artScale
                        alpha = artAlpha
                        translationY = if (reduced) 0f else -8f * animatedCollapse
                    },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = expandedPadding, vertical = verticalPadding),
                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
            ) {
                presentation.eyebrow?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                }

                presentation.statusText?.takeIf { it.isNotBlank() }?.let {
                    AtharHeroStatus(it, presentation.statusTone)
                }

                Text(
                    presentation.title,
                    modifier = Modifier.semantics { heading() },
                    style = if (animatedCollapse > 0.6f) MaterialTheme.typography.headlineSmall
                    else MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = if (animatedCollapse > 0.6f) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )

                presentation.supportingText?.takeIf { it.isNotBlank() && animatedCollapse < 0.88f }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.90f),
                        maxLines = if (animatedCollapse > 0.45f) 1 else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (animatedCollapse < 0.78f && (
                        presentation.primaryActionLabel != null ||
                            presentation.secondaryActionLabel != null ||
                            trailing != null
                        )
                ) {
                    Spacer(Modifier.height(AtharSpacing.Xs))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                    ) {
                        if (presentation.primaryActionLabel != null && onPrimaryAction != null) {
                            Button(onClick = onPrimaryAction) {
                                Text(presentation.primaryActionLabel)
                            }
                        }
                        if (presentation.secondaryActionLabel != null && onSecondaryAction != null) {
                            TextButton(onClick = onSecondaryAction) {
                                Text(
                                    presentation.secondaryActionLabel,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                    if (trailing != null) trailing()
                }
            }
        }
    }
}

/**
 * Rounded lower layer used beneath a section hero. It creates visual depth without pretending
 * the content is draggable. A grip/handle is deliberately not rendered here.
 */
@Composable
fun AtharLayeredContentSurface(
    modifier: Modifier = Modifier,
    overlap: Dp = 18.dp,
    contentPadding: PaddingValues = PaddingValues(
        start = AtharSpacing.Lg,
        end = AtharSpacing.Lg,
        top = AtharSpacing.Xxl,
        bottom = AtharSpacing.Xxl,
    ),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .offset(y = -overlap),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
            content = content,
        )
    }
}

@Composable
fun AtharCompactHeroHeader(
    title: String,
    modifier: Modifier = Modifier,
    statusText: String? = null,
    onAction: (() -> Unit)? = null,
    actionLabel: String? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AtharSpacing.Lg, vertical = AtharSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                statusText?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun AtharHeroStatus(text: String, tone: AtharHeroStatusTone) {
    val background = when (tone) {
        AtharHeroStatusTone.Neutral -> Color.White.copy(alpha = 0.14f)
        AtharHeroStatusTone.Offline -> Color(0xFF273E36).copy(alpha = 0.82f)
        AtharHeroStatusTone.Warning -> Color(0xFF714A27).copy(alpha = 0.86f)
        AtharHeroStatusTone.Live -> Color(0xFF7A2E2B).copy(alpha = 0.88f)
    }
    Surface(
        color = background,
        contentColor = Color.White,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun AtharHeroArtworkLayer(
    artwork: AtharHeroArtwork,
    modifier: Modifier = Modifier,
) {
    val accent = heroAccent(artwork)
    Canvas(modifier) {
        val w = size.width
        val h = size.height

        when (artwork) {
            AtharHeroArtwork.NightPrayer -> {
                drawCircle(Color.White.copy(alpha = 0.12f), radius = h * 0.18f, center = Offset(w * 0.78f, h * 0.23f))
                drawCircle(Color.White.copy(alpha = 0.04f), radius = h * 0.25f, center = Offset(w * 0.78f, h * 0.23f))
                val silhouette = Path().apply {
                    moveTo(0f, h * 0.86f)
                    lineTo(w * 0.18f, h * 0.68f)
                    lineTo(w * 0.30f, h * 0.78f)
                    lineTo(w * 0.43f, h * 0.56f)
                    lineTo(w * 0.57f, h * 0.76f)
                    lineTo(w * 0.72f, h * 0.64f)
                    lineTo(w, h * 0.82f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(silhouette, Color.Black.copy(alpha = 0.18f))
            }
            AtharHeroArtwork.Books -> {
                repeat(4) { index ->
                    val width = w * (0.16f + index * 0.015f)
                    val left = w * (0.58f + index * 0.07f)
                    val top = h * (0.22f + index * 0.07f)
                    drawRoundRect(
                        accent.copy(alpha = 0.10f + index * 0.03f),
                        topLeft = Offset(left, top),
                        size = Size(width, h * 0.5f),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                    )
                }
            }
            AtharHeroArtwork.Audio -> {
                repeat(5) { index ->
                    val x = w * (0.58f + index * 0.075f)
                    val bar = h * (0.16f + (index % 3) * 0.07f)
                    drawLine(
                        accent.copy(alpha = 0.22f),
                        start = Offset(x, h * 0.54f - bar / 2),
                        end = Offset(x, h * 0.54f + bar / 2),
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            else -> {
                val archWidth = w * 0.46f
                val archHeight = h * 0.72f
                val left = w * 0.58f
                val top = h * 0.12f
                val arch = Path().apply {
                    moveTo(left, top + archHeight)
                    lineTo(left, top + archHeight * 0.45f)
                    cubicTo(
                        left,
                        top + archHeight * 0.08f,
                        left + archWidth,
                        top + archHeight * 0.08f,
                        left + archWidth,
                        top + archHeight * 0.45f,
                    )
                    lineTo(left + archWidth, top + archHeight)
                    close()
                }
                drawPath(arch, accent.copy(alpha = 0.13f))
                drawCircle(
                    accent.copy(alpha = 0.08f),
                    radius = h * 0.22f,
                    center = Offset(w * 0.82f, h * 0.22f),
                )
                drawRoundRect(
                    Color.White.copy(alpha = 0.05f),
                    topLeft = Offset(w * 0.48f, h * 0.62f),
                    size = Size(w * 0.48f, h * 0.18f),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }
    }
}

private fun heroBrush(artwork: AtharHeroArtwork): Brush = when (artwork) {
    AtharHeroArtwork.NightPrayer -> Brush.linearGradient(
        listOf(Color(0xFF102E2B), Color(0xFF17483D), Color(0xFF10241F)),
    )
    AtharHeroArtwork.Adhkar -> Brush.linearGradient(
        listOf(Color(0xFF295F4D), Color(0xFF7B6642), Color(0xFF274B40)),
    )
    AtharHeroArtwork.Upcoming -> Brush.linearGradient(
        listOf(Color(0xFF1D6651), Color(0xFF315F62), Color(0xFF173F36)),
    )
    AtharHeroArtwork.Audio -> Brush.linearGradient(
        listOf(Color(0xFF1B5847), Color(0xFF284F4A), Color(0xFF153B32)),
    )
    AtharHeroArtwork.Books -> Brush.linearGradient(
        listOf(Color(0xFF365B47), Color(0xFF6A563B), Color(0xFF223D31)),
    )
    else -> Brush.linearGradient(
        listOf(Color(0xFF24694F), Color(0xFF315B49), Color(0xFF1A4A3A)),
    )
}

private fun heroAccent(artwork: AtharHeroArtwork): Color = when (artwork) {
    AtharHeroArtwork.NightPrayer -> Color(0xFFA8D7C5)
    AtharHeroArtwork.Adhkar -> Color(0xFFF0C990)
    AtharHeroArtwork.Upcoming -> Color(0xFFB5DAD6)
    AtharHeroArtwork.Books -> Color(0xFFE3BC7F)
    AtharHeroArtwork.Audio -> Color(0xFFA9D5C5)
    else -> Color(0xFFD8B178)
}
