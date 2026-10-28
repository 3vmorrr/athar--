package org.athar.app.core.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.google.gson.JsonElement

@OptIn(ExperimentalSharedTransitionApi::class)
internal val LocalAtharSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
internal val LocalAtharVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** Compact editorial identity for ordinary screens; the immersive Home hero remains distinct. */
@Composable
fun AtharScreenIdentity(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Lg), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(icon, null, Modifier.padding(14.dp).size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Xs)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** API26-compatible floating material. Deliberately no expensive backdrop blur. */
@Composable
fun AtharFloatingSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.98f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 3.dp,
        content = content,
    )
}

/** Native book identity, not a fabricated publisher cover: the current API has no cover field. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AtharBookCover(id: String, modifier: Modifier = Modifier) {
    val shared = LocalAtharSharedScope.current
    val visibility = LocalAtharVisibilityScope.current
    val reduced = reducedMotion()
    val transition = if (shared != null && visibility != null && !reduced) with(shared) {
        Modifier.sharedElement(
            sharedContentState = rememberSharedContentState(key = "book-cover:$id"),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ -> tween(AtharMotion.Slow, easing = AtharMotion.Standard) },
        )
    } else Modifier
    Box(
        modifier.testTag("book-cover-$id").then(transition).clip(MaterialTheme.shapes.small)
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHigh))),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(6.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)))
        Icon(AtharIcons.MenuBook, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun AtharCatalogCard(item: JsonElement, kind: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val title = item.text("title").ifBlank { item.text("name") }
    val subtitle = item.text("scholar_name").ifBlank { item.text("author") }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag("catalog-$kind-${item.text("id")}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(AtharSpacing.Lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Lg)) {
            when {
                kind == "books" -> AtharBookCover(item.text("id"), Modifier.width(56.dp).height(76.dp))
                kind == "lessons" && item.text("youtube_id").matches(Regex("[A-Za-z0-9_-]{11}")) ->
                    AsyncImage("https://i.ytimg.com/vi/${item.text("youtube_id")}/hqdefault.jpg", null,
                        Modifier.size(76.dp).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop)
                else -> Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                    Icon(if (kind == "scholars") AtharIcons.Person else AtharIcons.School, null,
                        Modifier.padding(12.dp).size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(AtharIcons.ArrowForward, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
