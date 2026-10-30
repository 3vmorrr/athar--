package org.athar.app.books

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*

@Composable
fun BookDetailScreen(
    graph: AppGraph,
    bookId: Int,
    openReader: (Int) -> Unit,
) {
    val owner = remember(bookId) { requireNotNull(graph.session.userId) }
    val uriHandler = LocalUriHandler.current
    val snackbar = remember { SnackbarHostState() }
    var favoriteInitial by remember(bookId) { mutableStateOf(false) }

    LaunchedEffect(owner, bookId) {
        try {
            favoriteInitial = graph.content
                .get("me/favorites")
                .data
                .asJsonArray
                .any {
                    it.text("target_type") == "book" &&
                        it.text("target_id") == bookId.toString()
                }
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            // The idempotent favorite action remains available.
        }
    }

    Box(Modifier.fillMaxSize()) {
        DataScreen(graph, "books/$bookId") { item, _ ->
            val sourceUrl = item.text("source_url")
            val safeSource = remember(sourceUrl) { safeExternalHttps(sourceUrl) }
            val canDownload = item.text("can_download") == "true"

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = AtharSpacing.Xxl),
                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
            ) {
                item(key = "book-intro", contentType = "intro") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = AtharSpacing.Lg,
                                end = AtharSpacing.Lg,
                                top = AtharSpacing.Md,
                            ),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Column(
                            Modifier.padding(AtharSpacing.Xl),
                            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                            ) {
                                Surface(
                                    modifier = Modifier.size(52.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Icon(
                                        AtharIcons.MenuBook,
                                        contentDescription = null,
                                        modifier = Modifier.padding(AtharSpacing.Md),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Xs),
                                ) {
                                    Text(
                                        item.text("title"),
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    item.text("author").takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            AtharStatusPill(
                                text = stringResource(
                                    if (canDownload) {
                                        R.string.book_download_allowed
                                    } else {
                                        R.string.book_download_unavailable
                                    },
                                ),
                                emphasized = canDownload,
                            )
                        }
                    }
                }

                item(key = "book-metadata", contentType = "metadata") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AtharSpacing.Lg),
                        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                    ) {
                        AtharSectionHeader(stringResource(R.string.book_metadata))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                        ) {
                            item.text("author").takeIf { it.isNotBlank() }?.let {
                                AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    label = { Text(it) },
                                    leadingIcon = {
                                        Icon(AtharIcons.Person, contentDescription = null)
                                    },
                                )
                            }
                            item.text("publication_year").takeIf { it.isNotBlank() }?.let {
                                AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    label = {
                                        Text(
                                            stringResource(
                                                R.string.book_publication_year,
                                                it,
                                            ),
                                        )
                                    },
                                )
                            }
                            item.text("language").takeIf { it.isNotBlank() }?.let {
                                AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    label = {
                                        Text(
                                            stringResource(
                                                R.string.book_language,
                                                it,
                                            ),
                                        )
                                    },
                                )
                            }
                        }

                        item.text("description").takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }

                item(key = "book-rights", contentType = "rights") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AtharSpacing.Lg),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Column(
                            Modifier.padding(AtharSpacing.Xl),
                            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                        ) {
                            AtharSectionHeader(stringResource(R.string.book_rights))
                            item.text("rights_holder").takeIf { it.isNotBlank() }?.let {
                                BookMetadataLine(
                                    stringResource(R.string.book_rights_holder),
                                    it,
                                )
                            }
                            item.text("rights_basis").takeIf { it.isNotBlank() }?.let {
                                BookMetadataLine(
                                    stringResource(R.string.book_rights_basis),
                                    it,
                                )
                            }
                            BookMetadataLine(
                                stringResource(R.string.book_download_permission),
                                stringResource(
                                    if (canDownload) {
                                        R.string.book_download_allowed
                                    } else {
                                        R.string.book_download_unavailable
                                    },
                                ),
                            )

                            if (safeSource != null) {
                                TextButton(
                                    onClick = { uriHandler.openUri(safeSource) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text(stringResource(R.string.book_open_source))
                                }
                            }
                        }
                    }
                }

                item(key = "book-actions", contentType = "actions") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AtharSpacing.Lg),
                        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                    ) {
                        BookDownloadActions(
                            graph = graph,
                            owner = owner,
                            bookId = bookId,
                            canDownload = canDownload,
                            onOpenReader = { openReader(bookId) },
                        )

                        MarkToggle(
                            identity = "books/$bookId",
                            initial = favoriteInitial,
                            favorite = true,
                            feedback = snackbar,
                        ) { selected ->
                            graph.session.request(
                                if (selected) "PUT" else "DELETE",
                                "me/favorites",
                                mapOf(
                                    "target_type" to "book",
                                    "target_id" to bookId,
                                ),
                                expectedUser = owner,
                            )
                            graph.content.invalidate("me/favorites", owner)
                        }
                    }
                }
            }
        }

        SnackbarHost(
            snackbar,
            Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
        )
    }
}

@Composable
private fun BookMetadataLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AtharSpacing.Xs)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun safeExternalHttps(value: String): String? =
    value.takeIf(BookDownloadPolicy::safeHttps)
