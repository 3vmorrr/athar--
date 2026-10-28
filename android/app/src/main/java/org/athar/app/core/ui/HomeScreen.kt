package org.athar.app.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.athar.app.learning.UpcomingHomeSection

@Composable
fun PremiumHomeScreen(
    graph: AppGraph,
    auth: AuthState.SignedIn,
    navigate: (String) -> Unit,
    open: (String) -> Unit,
) {
    DataScreen(graph, "home") { data, reload ->
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 1200.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = AtharSpacing.Xxl),
            ) {
                item(key = "home-hero", contentType = "hero") {
                    AtharImmersiveHero(
                        presentation = AtharHeroPresentation(
                            artwork = AtharHeroArtwork.Home,
                            transitionKey = "home:${graph.partition}:${auth.user.id}",
                            eyebrow = stringResource(R.string.your_journey),
                            title = stringResource(
                                R.string.hello_name,
                                auth.user.displayName ?: auth.user.username.orEmpty(),
                            ),
                            supportingText = stringResource(R.string.home_invitation),
                            primaryActionLabel = stringResource(R.string.read),
                            secondaryActionLabel = stringResource(R.string.night_prayer),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        onPrimaryAction = { navigate("quran") },
                        onSecondaryAction = { open("night-prayer") },
                    )
                }

                item(key = "home-layer", contentType = "layered-content") {
                    AtharLayeredContentSurface(
                        modifier = Modifier.fillMaxWidth(),
                        overlap = 24.dp,
                    ) {
                        HomeQuickActions(navigate = navigate, open = open)

                        UpcomingHomeSection(graph, open)

                        AtharSectionHeader(stringResource(R.string.your_activity))
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                        ) {
                            HomeStat(
                                HomeProjection.metric(data, "quran_sessions")?.toString() ?: stringResource(R.string.home_metric_unavailable),
                                stringResource(R.string.reading_sessions),
                                Modifier.weight(1f).widthIn(min = 96.dp),
                            )
                            HomeStat(
                                HomeProjection.metric(data, "lesson_completions")?.toString() ?: stringResource(R.string.home_metric_unavailable),
                                stringResource(R.string.completed_lessons),
                                Modifier.weight(1f).widthIn(min = 96.dp),
                            )
                            HomeStat(
                                HomeProjection.metric(data, "adhkar_completions")?.toString() ?: stringResource(R.string.home_metric_unavailable),
                                stringResource(R.string.completed_adhkar),
                                Modifier.weight(1f).widthIn(min = 96.dp),
                            )
                        }

                        AtharSectionHeader(
                            title = stringResource(R.string.latest_lessons),
                            actionLabel = stringResource(R.string.view_all),
                            onAction = { navigate("knowledge") },
                        )
                        HomeContentCollection(
                            items = HomeProjection.items(data, "latest_lessons"),
                            lesson = true,
                            emptyMessage = stringResource(R.string.empty_state),
                            retry = reload,
                        ) { item ->
                            open("lessons/${item.text("id")}")
                        }

                        AtharSectionHeader(
                            title = stringResource(R.string.books),
                            actionLabel = stringResource(R.string.view_all),
                            onAction = { open("catalog-books") },
                        )
                        HomeContentCollection(
                            items = HomeProjection.items(data, "books"),
                            lesson = false,
                            emptyMessage = stringResource(R.string.empty_state),
                            retry = reload,
                        ) { item ->
                            open("books/${item.text("id")}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeQuickActions(
    navigate: (String) -> Unit,
    open: (String) -> Unit,
) {
    AtharSectionHeader(stringResource(R.string.quick_actions))
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val twoColumns = maxWidth >= 300.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.3f
        if (twoColumns) {
            Column(verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                ) {
                    AtharQuickAction(
                        icon = AtharIcons.MenuBook,
                        label = stringResource(R.string.quran),
                        onClick = { navigate("quran") },
                        modifier = Modifier.weight(1f),
                        emphasized = true,
                    )
                    AtharQuickAction(
                        icon = AtharIcons.School,
                        label = stringResource(R.string.knowledge),
                        onClick = { navigate("knowledge") },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                ) {
                    AtharQuickAction(
                        icon = AtharIcons.Spa,
                        label = stringResource(R.string.adhkar),
                        onClick = { navigate("adhkar") },
                        modifier = Modifier.weight(1f),
                    )
                    AtharQuickAction(
                        icon = AtharIcons.NightsStay,
                        label = stringResource(R.string.night_prayer),
                        onClick = { open("night-prayer") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
                AtharQuickAction(
                    icon = AtharIcons.MenuBook,
                    label = stringResource(R.string.quran),
                    onClick = { navigate("quran") },
                    modifier = Modifier.fillMaxWidth(),
                    emphasized = true,
                )
                AtharQuickAction(
                    icon = AtharIcons.School,
                    label = stringResource(R.string.knowledge),
                    onClick = { navigate("knowledge") },
                    modifier = Modifier.fillMaxWidth(),
                )
                AtharQuickAction(
                    icon = AtharIcons.Spa,
                    label = stringResource(R.string.adhkar),
                    onClick = { navigate("adhkar") },
                    modifier = Modifier.fillMaxWidth(),
                )
                AtharQuickAction(
                    icon = AtharIcons.NightsStay,
                    label = stringResource(R.string.night_prayer),
                    onClick = { open("night-prayer") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HomeContentCollection(
    items: List<JsonElement>?,
    lesson: Boolean,
    emptyMessage: String,
    retry: () -> Unit,
    open: (JsonElement) -> Unit,
) {
    if (items == null) {
        FailurePanel(stringResource(R.string.home_content_unavailable), retry)
        return
    }
    if (items.isEmpty()) {
        QuietEmptyState(emptyMessage)
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= 900.dp -> 3
            maxWidth >= 620.dp -> 2
            else -> 1
        }
        if (columns == 1) {
            Column(verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
                items.forEach { item ->
                    HomeContentCard(
                        item = item,
                        lesson = lesson,
                        open = { open(item) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md)) {
                items.chunked(columns).forEach { rowItems ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                        verticalAlignment = Alignment.Top,
                    ) {
                        rowItems.forEach { item ->
                            HomeContentCard(
                                item = item,
                                lesson = lesson,
                                open = { open(item) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columns - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeStat(
    value: String,
    label: String,
    modifier: Modifier,
) {
    AtharMetric(value = value, label = label, modifier = modifier)
}

@Composable
private fun HomeContentCard(
    item: JsonElement,
    lesson: Boolean,
    open: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = reducedMotion()
    val scale by animateFloatAsState(
        if (pressed && !reduced) 0.985f else 1f,
        if (reduced) snap() else spring(
            stiffness = AtharMotion.SmallSpringStiffness,
            dampingRatio = AtharMotion.SmallSpringDamping,
        ),
        label = "content-card-press",
    )
    Surface(
        onClick = open,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        interactionSource = interaction,
    ) {
        Column {
            if (lesson && item.text("youtube_id").matches(Regex("[A-Za-z0-9_-]{11}"))) {
                AsyncImage(
                    model = "https://i.ytimg.com/vi/${item.text("youtube_id")}/hqdefault.jpg",
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                Modifier.padding(AtharSpacing.Xl),
                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
            ) {
                if (!lesson) {
                    AtharBookCover(item.text("id"), Modifier.width(56.dp).height(76.dp))
                }
                Text(
                    item.text("title"),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = item.text("scholar_name").ifBlank { item.text("author") }
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun QuietEmptyState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            Modifier.padding(AtharSpacing.Xl),
            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                AtharIcons.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
