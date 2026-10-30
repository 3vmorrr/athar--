package org.athar.app.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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

enum class AtharHeroVisualState {
    Ready,
    Loading,
    Empty,
    Offline,
    Error,
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
    val visualState: AtharHeroVisualState = AtharHeroVisualState.Ready,
    /**
     * Optional feature/actor identity supplied by the owning screen when reveal continuity must
     * reset across an ownership boundary. This value is used only as a Compose animation key.
     */
    val transitionKey: String? = null,
)

@Immutable
data class AtharHeroArtworkDescriptor(
    val id: String,
    val sourceKind: String = "native_canvas",
    val networkRequired: Boolean = false,
    val containsText: Boolean = false,
    val containsPeople: Boolean = false,
    val autoMirror: Boolean = false,
)

object AtharHeroArtworkRegistry {
    fun descriptor(
        artwork: AtharHeroArtwork,
        state: AtharHeroVisualState = AtharHeroVisualState.Ready,
    ): AtharHeroArtworkDescriptor {
        val id = when (state) {
            AtharHeroVisualState.Empty -> "state.empty"
            AtharHeroVisualState.Offline -> "state.offline"
            AtharHeroVisualState.Error -> "state.error"
            AtharHeroVisualState.Ready,
            AtharHeroVisualState.Loading -> when (artwork) {
                AtharHeroArtwork.Quran -> "hero.quran"
                AtharHeroArtwork.Knowledge -> "hero.knowledge"
                AtharHeroArtwork.Adhkar -> "hero.adhkar"
                AtharHeroArtwork.NightPrayer -> "hero.night"
                AtharHeroArtwork.Books -> "hero.books"
                AtharHeroArtwork.Audio -> "hero.audio"
                AtharHeroArtwork.Home,
                AtharHeroArtwork.Upcoming,
                AtharHeroArtwork.Generic -> "hero.knowledge"
            }
        }
        return AtharHeroArtworkDescriptor(id = id)
    }
}

@Immutable
data class AtharHeroLayoutSpec(
    val compact: Boolean,
    val showArtwork: Boolean,
    val stackActions: Boolean,
    val titleMaxLines: Int,
    val supportingMaxLines: Int,
    val minimumHeightDp: Int,
)

object AtharHeroLayoutPolicy {
    fun resolve(
        fontScale: Float,
        widthDp: Float,
        collapseFraction: Float,
    ): AtharHeroLayoutSpec {
        require(fontScale > 0f)
        require(widthDp > 0f)
        val collapse = collapseFraction.coerceIn(0f, 1f)
        val largeText = fontScale >= 1.3f
        val veryLargeText = fontScale >= 1.6f
        val narrow = widthDp < 360f
        val compact = collapse >= 0.72f || veryLargeText || widthDp < 300f
        return AtharHeroLayoutSpec(
            compact = compact,
            showArtwork = !veryLargeText && collapse < 0.94f,
            stackActions = largeText || narrow,
            titleMaxLines = if (compact) {
                if (veryLargeText) 3 else 2
            } else {
                if (largeText) 3 else 2
            },
            supportingMaxLines = if (largeText) 4 else if (collapse > 0.45f) 2 else 3,
            minimumHeightDp = if (compact) 148 else if (largeText) 172 else 188,
        )
    }
}

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
    shape: Shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp),
    minimumHeight: Dp? = null,
    collapseFraction: Float = 0f,
    reduceMotion: Boolean = false,
    onPrimaryAction: (() -> Unit)? = null,
    onSecondaryAction: (() -> Unit)? = null,
    trailing: (@Composable ColumnScope.() -> Unit)? = null,
) {
    require(presentation.title.isNotBlank())
    require(presentation.transitionKey == null || presentation.transitionKey.length <= 120)
    val reduced = reduceMotion || reducedMotion()
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val target = collapseFraction.coerceIn(0f, 1f)
    val animatedCollapse by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduced) snap() else tween(
            AtharMotion.Normal,
            easing = AtharMotion.Standard,
        ),
        label = "athar-hero-collapse",
    )
    val revealKey = presentation.transitionKey ?: AtharHeroArtworkRegistry
        .descriptor(presentation.artwork, presentation.visualState).id
    val revealScale = remember(revealKey) {
        Animatable(if (reduced) 1f else 1.03f)
    }
    LaunchedEffect(revealKey, reduced) {
        if (reduced) {
            revealScale.snapTo(1f)
        } else {
            revealScale.snapTo(1.03f)
            revealScale.animateTo(
                1f,
                animationSpec = tween(AtharMotion.Normal, easing = AtharMotion.Enter),
            )
        }
    }
    val parallaxPx = with(density) { 8.dp.toPx() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape),
        color = Color.Transparent,
        contentColor = Color.White,
    ) {
        BoxWithConstraints(
            Modifier
                .background(heroBrush(presentation.artwork, presentation.visualState))
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.04f),
                            0.55f to Color.Black.copy(alpha = 0.16f),
                            1f to Color.Black.copy(alpha = 0.54f),
                        ),
                    )
                }
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val layout = AtharHeroLayoutPolicy.resolve(
                fontScale = fontScale,
                widthDp = maxWidth.value.coerceAtLeast(1f),
                collapseFraction = animatedCollapse,
            )
            val verticalPadding = if (layout.compact) 16.dp else 24.dp
            val horizontalPadding = if (maxWidth < 360.dp) AtharSpacing.Lg else AtharSpacing.Xxl
            val artAlpha = (1f - animatedCollapse * 0.42f).coerceIn(0f, 1f)

            if (layout.showArtwork) {
                AtharHeroArtworkLayer(
                    artwork = presentation.artwork,
                    state = presentation.visualState,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            scaleX = revealScale.value
                            scaleY = revealScale.value
                            alpha = artAlpha
                            translationY = if (reduced) 0f else -parallaxPx * animatedCollapse
                        },
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minimumHeight ?: layout.minimumHeightDp.dp)
                    .padding(
                        horizontal = horizontalPadding,
                        vertical = verticalPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
            ) {
                presentation.eyebrow?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.82f),
                    )
                }

                presentation.statusText?.takeIf { it.isNotBlank() }?.let {
                    AtharHeroStatus(it, presentation.statusTone)
                }

                Text(
                    presentation.title,
                    modifier = Modifier.semantics { heading() },
                    style = if (layout.compact) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.headlineMedium
                    },
                    color = Color.White,
                    maxLines = layout.titleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )

                presentation.supportingText
                    ?.takeIf { it.isNotBlank() && animatedCollapse < 0.88f }
                    ?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.92f),
                            maxLines = layout.supportingMaxLines,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                if (
                    animatedCollapse < 0.78f &&
                    (
                        presentation.primaryActionLabel != null ||
                            presentation.secondaryActionLabel != null ||
                            trailing != null
                    )
                ) {
                    Spacer(Modifier.height(AtharSpacing.Xs))
                    if (layout.stackActions) {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                        ) {
                            if (
                                presentation.primaryActionLabel != null &&
                                onPrimaryAction != null
                            ) {
                                Button(
                                    onClick = onPrimaryAction,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                ) {
                                    Text(presentation.primaryActionLabel)
                                }
                            }
                            if (
                                presentation.secondaryActionLabel != null &&
                                onSecondaryAction != null
                            ) {
                                TextButton(
                                    onClick = onSecondaryAction,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                ) {
                                    Text(
                                        presentation.secondaryActionLabel,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                        ) {
                            if (
                                presentation.primaryActionLabel != null &&
                                onPrimaryAction != null
                            ) {
                                Button(
                                    onClick = onPrimaryAction,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text(presentation.primaryActionLabel)
                                }
                            }
                            if (
                                presentation.secondaryActionLabel != null &&
                                onSecondaryAction != null
                            ) {
                                TextButton(
                                    onClick = onSecondaryAction,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text(
                                        presentation.secondaryActionLabel,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                    trailing?.invoke(this)
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
    val adaptiveOverlap = if (LocalDensity.current.fontScale >= 1.6f) 0.dp else overlap
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .offset(y = -adaptiveOverlap),
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
    val largeText = LocalDensity.current.fontScale >= 1.3f
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        if (largeText) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AtharSpacing.Lg, vertical = AtharSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
            ) {
                Text(
                    title,
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                statusText?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (actionLabel != null && onAction != null) {
                    TextButton(
                        onClick = onAction,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(actionLabel)
                    }
                }
            }
        } else {
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    statusText?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (actionLabel != null && onAction != null) {
                    TextButton(
                        onClick = onAction,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun AtharHeroStatus(text: String, tone: AtharHeroStatusTone) {
    val background = when (tone) {
        AtharHeroStatusTone.Neutral -> Color.White.copy(alpha = 0.16f)
        AtharHeroStatusTone.Offline -> Color(0xFF273E36).copy(alpha = 0.92f)
        AtharHeroStatusTone.Warning -> Color(0xFF714A27).copy(alpha = 0.94f)
        AtharHeroStatusTone.Live -> Color(0xFF7A2E2B).copy(alpha = 0.94f)
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
    state: AtharHeroVisualState,
    modifier: Modifier = Modifier,
) {
    val accent = heroAccent(artwork)
    Canvas(modifier) {
        val w = size.width
        val h = size.height

        when (state) {
            AtharHeroVisualState.Empty -> {
                drawCircle(
                    accent.copy(alpha = 0.10f),
                    radius = h * 0.24f,
                    center = Offset(w * 0.78f, h * 0.26f),
                )
                val path = Path().apply {
                    moveTo(w * 0.48f, h * 0.78f)
                    cubicTo(
                        w * 0.60f, h * 0.64f,
                        w * 0.74f, h * 0.88f,
                        w, h * 0.66f,
                    )
                }
                drawPath(
                    path,
                    accent.copy(alpha = 0.18f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            AtharHeroVisualState.Offline -> {
                repeat(3) { index ->
                    drawCircle(
                        accent.copy(alpha = 0.08f + index * 0.035f),
                        radius = h * (0.12f + index * 0.07f),
                        center = Offset(w * 0.80f, h * 0.40f),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                drawLine(
                    Color.White.copy(alpha = 0.22f),
                    start = Offset(w * 0.63f, h * 0.68f),
                    end = Offset(w * 0.94f, h * 0.22f),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            AtharHeroVisualState.Error -> {
                repeat(3) { index ->
                    drawRoundRect(
                        accent.copy(alpha = 0.08f + index * 0.025f),
                        topLeft = Offset(
                            w * (0.56f + index * 0.05f),
                            h * (0.24f + index * 0.12f),
                        ),
                        size = Size(w * 0.30f, h * 0.18f),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
            AtharHeroVisualState.Ready,
            AtharHeroVisualState.Loading -> drawSectionArtwork(artwork, accent, w, h)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSectionArtwork(
    artwork: AtharHeroArtwork,
    accent: Color,
    w: Float,
    h: Float,
) {
    when (artwork) {
        AtharHeroArtwork.NightPrayer -> {
            drawCircle(
                Color.White.copy(alpha = 0.12f),
                radius = h * 0.18f,
                center = Offset(w * 0.78f, h * 0.23f),
            )
            drawCircle(
                Color.White.copy(alpha = 0.04f),
                radius = h * 0.25f,
                center = Offset(w * 0.78f, h * 0.23f),
            )
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
            drawPath(silhouette, Color.Black.copy(alpha = 0.20f))
        }
        AtharHeroArtwork.Books,
        AtharHeroArtwork.Knowledge -> {
            repeat(4) { index ->
                val width = w * (0.15f + index * 0.012f)
                val left = w * (0.56f + index * 0.075f)
                val top = h * (0.22f + index * 0.07f)
                drawRoundRect(
                    accent.copy(alpha = 0.10f + index * 0.03f),
                    topLeft = Offset(left, top),
                    size = Size(width, h * 0.48f),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                )
            }
        }
        AtharHeroArtwork.Audio -> {
            repeat(5) { index ->
                val x = w * (0.58f + index * 0.075f)
                val bar = h * (0.16f + (index % 3) * 0.07f)
                drawLine(
                    accent.copy(alpha = 0.24f),
                    start = Offset(x, h * 0.54f - bar / 2),
                    end = Offset(x, h * 0.54f + bar / 2),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        AtharHeroArtwork.Adhkar -> {
            drawCircle(
                accent.copy(alpha = 0.12f),
                radius = h * 0.30f,
                center = Offset(w * 0.83f, h * 0.24f),
            )
            drawCircle(
                Color.White.copy(alpha = 0.07f),
                radius = h * 0.18f,
                center = Offset(w * 0.76f, h * 0.54f),
            )
            drawSectionArch(accent, w, h)
        }
        AtharHeroArtwork.Upcoming -> {
            drawSectionArch(accent, w, h)
            repeat(3) { index ->
                drawCircle(
                    accent.copy(alpha = 0.10f + index * 0.025f),
                    radius = (5 + index * 4).dp.toPx(),
                    center = Offset(w * (0.67f + index * 0.09f), h * 0.35f),
                )
            }
        }
        AtharHeroArtwork.Home,
        AtharHeroArtwork.Quran,
        AtharHeroArtwork.Generic -> drawSectionArch(accent, w, h)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSectionArch(
    accent: Color,
    w: Float,
    h: Float,
) {
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
    drawPath(arch, accent.copy(alpha = 0.14f))
    drawCircle(
        accent.copy(alpha = 0.09f),
        radius = h * 0.22f,
        center = Offset(w * 0.82f, h * 0.22f),
    )
    drawRoundRect(
        Color.White.copy(alpha = 0.06f),
        topLeft = Offset(w * 0.48f, h * 0.62f),
        size = Size(w * 0.48f, h * 0.18f),
        cornerRadius = CornerRadius(18.dp.toPx()),
        style = Stroke(width = 1.dp.toPx()),
    )
}

private fun heroBrush(
    artwork: AtharHeroArtwork,
    state: AtharHeroVisualState,
): Brush {
    if (state == AtharHeroVisualState.Offline) {
        return Brush.linearGradient(
            listOf(Color(0xFF32423B), Color(0xFF3E4B45), Color(0xFF293630)),
        )
    }
    if (state == AtharHeroVisualState.Error) {
        return Brush.linearGradient(
            listOf(Color(0xFF4A493F), Color(0xFF405347), Color(0xFF333E38)),
        )
    }
    return when (artwork) {
        AtharHeroArtwork.NightPrayer -> Brush.linearGradient(
            listOf(Color(0xFF102E2B), Color(0xFF17483D), Color(0xFF10241F)),
        )
        AtharHeroArtwork.Adhkar -> Brush.linearGradient(
            listOf(Color(0xFF295F4D), Color(0xFF715E3E), Color(0xFF274B40)),
        )
        AtharHeroArtwork.Upcoming -> Brush.linearGradient(
            listOf(Color(0xFF1D6651), Color(0xFF315F62), Color(0xFF173F36)),
        )
        AtharHeroArtwork.Audio -> Brush.linearGradient(
            listOf(Color(0xFF1B5847), Color(0xFF284F4A), Color(0xFF153B32)),
        )
        AtharHeroArtwork.Books,
        AtharHeroArtwork.Knowledge -> Brush.linearGradient(
            listOf(Color(0xFF365B47), Color(0xFF66533A), Color(0xFF223D31)),
        )
        else -> Brush.linearGradient(
            listOf(Color(0xFF24694F), Color(0xFF315B49), Color(0xFF1A4A3A)),
        )
    }
}

private fun heroAccent(artwork: AtharHeroArtwork): Color = when (artwork) {
    AtharHeroArtwork.NightPrayer -> Color(0xFFA8D7C5)
    AtharHeroArtwork.Adhkar -> Color(0xFFF0C990)
    AtharHeroArtwork.Upcoming -> Color(0xFFB5DAD6)
    AtharHeroArtwork.Books,
    AtharHeroArtwork.Knowledge -> Color(0xFFE3BC7F)
    AtharHeroArtwork.Audio -> Color(0xFFA9D5C5)
    else -> Color(0xFFD8B178)
}
