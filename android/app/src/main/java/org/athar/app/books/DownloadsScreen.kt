package org.athar.app.books

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.AtharIcons
import org.athar.app.core.ui.AtharSectionHeader
import org.athar.app.core.ui.AtharSpacing
import org.athar.app.core.ui.QuietEmptyState

@Composable
fun DownloadsScreen(
    graph: AppGraph,
    openReader: (Int) -> Unit,
) {
    val context = LocalContext.current
    val owner = graph.session.userId ?: return
    var revision by remember(owner) { mutableIntStateOf(0) }
    val downloads = remember(owner, revision) {
        BookStorage.downloaded(context, owner)
    }
    val totalBytes = remember(downloads) {
        downloads.sumOf { it.sizeBytes }
    }
    var pendingRemoval by remember(owner) { mutableStateOf<LocalBookDownload?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AtharSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
    ) {
        item(key = "offline-summary", contentType = "summary") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(
                    Modifier.padding(AtharSpacing.Xl),
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
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
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.offline_library),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                stringResource(R.string.offline_library_intro),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        stringResource(R.string.downloaded_books_count, downloads.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(
                            R.string.local_storage_used,
                            Formatter.formatShortFileSize(context, totalBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.local_copy_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "downloads-heading", contentType = "heading") {
            AtharSectionHeader(stringResource(R.string.downloads))
        }

        if (downloads.isEmpty()) {
            item(key = "downloads-empty", contentType = "empty") {
                QuietEmptyState(stringResource(R.string.no_downloaded_books))
            }
        } else {
            items(
                items = downloads,
                key = { it.bookId },
                contentType = { "downloaded-book" },
            ) { book ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Column(
                        Modifier.padding(AtharSpacing.Lg),
                        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Icon(
                                    AtharIcons.MenuBook,
                                    contentDescription = null,
                                    modifier = Modifier.padding(AtharSpacing.Sm),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    book.title ?: stringResource(
                                        R.string.downloaded_book_id,
                                        book.bookId,
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    Formatter.formatShortFileSize(context, book.sizeBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                        ) {
                            Button(
                                onClick = { openReader(book.bookId) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(R.string.book_open))
                            }
                            OutlinedButton(
                                onClick = { pendingRemoval = book },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(R.string.remove_local_copy))
                            }
                        }
                    }
                }
            }
        }
    }

    pendingRemoval?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.remove_local_copy)) },
            text = { Text(stringResource(R.string.local_copy_only)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        BookStorage.remove(context, owner, book.bookId)
                        revision++
                        pendingRemoval = null
                    },
                ) {
                    Text(stringResource(R.string.delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
