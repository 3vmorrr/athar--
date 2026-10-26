package org.athar.app.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.google.gson.JsonElement
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.auth.AuthState

@Composable fun PremiumHomeScreen(graph: AppGraph, auth: AuthState.SignedIn, navigate: (String) -> Unit, open: (String) -> Unit) {
    DataScreen(graph, "home") { data, _ ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyVerticalGrid(GridCells.Adaptive(300.dp), Modifier.widthIn(max = 1200.dp).fillMaxSize(), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item(key = "welcome", span = { GridItemSpan(maxLineSpan) }, contentType = "hero") {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                        Column(Modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerLow))).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.your_journey), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.hello_name, auth.user.displayName ?: auth.user.username.orEmpty()), style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.home_invitation), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { navigate("quran") }, Modifier.heightIn(min = 52.dp)) { Icon(AtharIcons.MenuBook, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.read)) }
                                OutlinedButton(onClick = { open("night-prayer") }, Modifier.heightIn(min = 52.dp)) { Icon(AtharIcons.NightsStay, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.night_prayer)) }
                                OutlinedButton(onClick = { open("upcoming") }, Modifier.heightIn(min = 52.dp)) { Icon(AtharIcons.CalendarMonth, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.upcoming_lessons)) }
                            }
                        }
                    }
                }
                item(key = "activity", span = { GridItemSpan(maxLineSpan) }, contentType = "activity") {
                    val activity = data.asJsonObject["activity"]
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeStat(activity.text("quran_sessions").ifBlank { "0" }, stringResource(R.string.reading_sessions), Modifier.weight(1f).widthIn(min = 90.dp))
                        HomeStat(activity.text("lesson_completions").ifBlank { "0" }, stringResource(R.string.completed_lessons), Modifier.weight(1f).widthIn(min = 90.dp))
                        HomeStat(activity.text("adhkar_completions").ifBlank { "0" }, stringResource(R.string.completed_adhkar), Modifier.weight(1f).widthIn(min = 90.dp))
                    }
                }
                item(key = "lessons-heading", span = { GridItemSpan(maxLineSpan) }, contentType = "heading") { HomeSectionHeading(stringResource(R.string.latest_lessons), { navigate("knowledge") }) }
                items(data.array("latest_lessons"), key = { "lesson-${it.text("id")}" }, contentType = { "lesson" }) { item -> HomeContentCard(item, lesson = true) { open("lessons/${item.text("id")}") } }
                if (data.array("latest_lessons").isEmpty()) item(key = "lessons-empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") { QuietEmptyState(stringResource(R.string.empty_state)) }
                item(key = "books-heading", span = { GridItemSpan(maxLineSpan) }, contentType = "heading") { HomeSectionHeading(stringResource(R.string.books), { open("catalog-books") }) }
                items(data.array("books"), key = { "book-${it.text("id")}" }, contentType = { "book" }) { item -> HomeContentCard(item, lesson = false) { open("books/${item.text("id")}") } }
                if (data.array("books").isEmpty()) item(key = "books-empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") { QuietEmptyState(stringResource(R.string.empty_state)) }
                item(key = "adhkar", span = { GridItemSpan(maxLineSpan) }, contentType = "invitation") {
                    Surface(onClick = { navigate("adhkar") }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(AtharIcons.Spa, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.secondary)
                            Column(Modifier.weight(1f)) { Text(stringResource(R.string.adhkar), style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.adhkar_invitation), style = MaterialTheme.typography.bodyMedium) }
                            Icon(AtharIcons.ArrowForward, null)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun HomeStat(value: String, label: String, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary); Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable private fun HomeSectionHeading(title: String, more: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton(onClick = more) { Text(stringResource(R.string.view_all)) } }
}
@Composable private fun HomeContentCard(item: JsonElement, lesson: Boolean, open: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = reducedMotion()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, if (reduced) snap() else spring(stiffness = AtharMotion.SmallSpringStiffness, dampingRatio = AtharMotion.SmallSpringDamping), label = "content-card-press")
    Surface(onClick = open, modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), interactionSource = interaction) {
        Column {
            if (lesson && item.text("youtube_id").matches(Regex("[A-Za-z0-9_-]{11}"))) AsyncImage(model = "https://i.ytimg.com/vi/${item.text("youtube_id")}/hqdefault.jpg", contentDescription = null, modifier = Modifier.fillMaxWidth().height(160.dp).background(MaterialTheme.colorScheme.primaryContainer), contentScale = ContentScale.Crop)
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!lesson) Icon(AtharIcons.MenuBook, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.secondary)
                Text(item.text("title"), style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(item.text("scholar_name").ifBlank { item.text("author") }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
@Composable fun QuietEmptyState(message: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(AtharIcons.MenuBook, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary); Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
