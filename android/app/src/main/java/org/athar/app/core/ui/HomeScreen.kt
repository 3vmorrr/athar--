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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import com.google.gson.JsonElement
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.auth.AuthState
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.data.protectedReads
import org.athar.app.learning.UpcomingHomeSection

@Composable
fun PremiumHomeScreen(
    graph: AppGraph,
    auth: AuthState.SignedIn,
    navigate: (String) -> Unit,
    open: (String) -> Unit,
) {
    DataScreen(graph, "home") { data, _ ->
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
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = AtharSpacing.Lg,
                                end = AtharSpacing.Lg,
                                top = AtharSpacing.Md,
                            ),
                    ) {
                        AtharImmersiveHero(
                            presentation = AtharHeroPresentation(
                                artwork = AtharHeroArtwork.Home,
                                eyebrow = stringResource(R.string.your_journey),
                                title = stringResource(
                                    R.string.hello_name,
                                    auth.user.displayName ?: auth.user.username.orEmpty(),
                                ),
                                supportingText = stringResource(R.string.home_invitation),
                                primaryActionLabel = stringResource(R.string.read),
                                secondaryActionLabel = stringResource(R.string.night_prayer),
                                transitionKey = "home:${auth.user.id}",
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            onPrimaryAction = { navigate("quran") },
                            onSecondaryAction = { open("night-prayer") },
                        )
                    }
                }

                item(key = "home-layer", contentType = "layered-content") {
                    AtharLayeredContentSurface(
                        modifier = Modifier.fillMaxWidth(),
                        overlap = 24.dp,
                    ) {
                        HomeReadingContinuation(graph = graph, open = open)

                        HomeQuickActions(navigate = navigate, open = open)

                        UpcomingHomeSection(graph, open)

                        val activity = data.asJsonObject["activity"]
                        AtharSectionHeader(stringResource(R.string.your_activity))
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                        ) {
                            HomeStat(
                                activity.text("quran_sessions").ifBlank { "0" },
                                stringResource(R.string.reading_sessions),
                                Modifier.weight(1f).widthIn(min = 96.dp),
                            )
                            HomeStat(
                                activity.text("lesson_completions").ifBlank { "0" },
                                stringResource(R.string.completed_lessons),
                                Modifier.weight(1f).widthIn(min = 96.dp),
                            )
                            HomeStat(
                                activity.text("adhkar_completions").ifBlank { "0" },
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
                            items = data.array("latest_lessons"),
                            lesson = true,
                            emptyMessage = stringResource(R.string.empty_state),
                        ) { item ->
                            open("lessons/${item.text("id")}")
                        }

                        AtharSectionHeader(
                            title = stringResource(R.string.books),
                            actionLabel = stringResource(R.string.view_all),
                            onAction = { open("catalog-books") },
                        )
                        HomeContentCollection(
                            items = data.array("books"),
                            lesson = false,
                            emptyMessage = stringResource(R.string.empty_state),
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
private fun HomeReadingContinuation(
    graph: AppGraph,
    open: (String) -> Unit,
) {
    val owner = graph.session.userId ?: return
    val readingFlow = remember(owner) {
        graph.reading.observe(owner).protectedReads()
    }
    val read by readingFlow.collectAsStateWithLifecycle(
        initialValue = ProtectedRead.Loading,
    )

    LaunchedEffect(owner) {
        try {
            graph.reading.refresh(owner)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            // The encrypted local marker remains the source of truth while offline.
        }
    }

    when (val current = read) {
        ProtectedRead.Loading -> Unit
        ProtectedRead.Unreadable -> {
            MessagePanel(
                message = stringResource(R.string.local_data_unreadable),
                error = true,
            )
        }
        is ProtectedRead.Ready -> {
            val position = current.value ?: return
            var surahName by remember(position.surah) {
                mutableStateOf<String?>(null)
            }
            var ayahPreview by remember(position.surah, position.ayah) {
                mutableStateOf<String?>(null)
            }

            LaunchedEffect(position.surah, position.ayah) {
                try {
                    val data = graph.quran.data()
                    surahName = data.index.surahs
                        .getOrNull(position.surah - 1)
                        ?.name
                    ayahPreview = data.surah(position.surah)
                        .getOrNull(position.ayah - 1)
                        ?.text
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    // The continuation control still works from the canonical numeric reference.
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.secondary)
                            .align(Alignment.TopCenter),
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(AtharSpacing.Xl)
                            .padding(top = AtharSpacing.Xs),
                        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Icon(
                                    AtharIcons.MenuBook,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(AtharSpacing.Sm)
                                        .size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.resume_reading),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                Text(
                                    stringResource(
                                        R.string.reading_position_label,
                                        surahName ?: position.surah.toString(),
                                        position.ayah,
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            when {
                                position.conflict -> AtharStatusPill(
                                    text = stringResource(R.string.reading_conflict),
                                    emphasized = true,
                                )
                                position.pending -> AtharStatusPill(
                                    text = stringResource(R.string.reading_pending),
                                )
                            }
                        }

                        ayahPreview?.takeIf { it.isNotBlank() }?.let { ayah ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Text(
                                    text = ayah,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(AtharSpacing.Lg),
                                    fontFamily = QuranFont,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Button(
                            onClick = {
                                open("quran/${position.surah}/${position.ayah}")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) {
                            Icon(
                                AtharIcons.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(AtharSpacing.Sm))
                            Text(stringResource(R.string.resume_reading))
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
        val twoColumns = maxWidth >= 360.dp
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
                        icon = AtharIcons.NightsStay,
                        label = stringResource(R.string.night_prayer),
                        onClick = { open("night-prayer") },
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
                        icon = AtharIcons.School,
                        label = stringResource(R.string.knowledge),
                        onClick = { navigate("knowledge") },
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
    items: List<JsonElement>,
    lesson: Boolean,
    emptyMessage: String,
    open: (JsonElement) -> Unit,
) {
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
                    Icon(
                        AtharIcons.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
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
