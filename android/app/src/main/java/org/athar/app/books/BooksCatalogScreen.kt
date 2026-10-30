package org.athar.app.books

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.gson.JsonElement
import kotlinx.coroutines.launch
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*

data class BookCatalogItem(
    val id: Int,
    val title: String,
    val author: String?,
    val description: String?,
    val publicationYear: String?,
    val language: String?,
)

object BookCatalogContract {
    fun item(element: JsonElement): BookCatalogItem {
        require(element.isJsonObject)
        val value = element.asJsonObject
        val id = value["id"]?.asInt?.takeIf { it > 0 }
            ?: error("invalid_book_id")
        val title = value.string("title")?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= 240 }
            ?: error("invalid_book_title")
        return BookCatalogItem(
            id = id,
            title = title,
            author = value.string("author")?.trim()?.takeIf { it.isNotEmpty() }?.also {
                require(it.length <= 240)
            },
            description = value.string("description")?.trim()?.takeIf { it.isNotEmpty() }?.also {
                require(it.length <= 4000)
            },
            publicationYear = value.string("publication_year")?.trim()?.takeIf { it.isNotEmpty() }?.also {
                require(it.length <= 20)
            },
            language = value.string("language")?.trim()?.takeIf { it.isNotEmpty() }?.also {
                require(it.length <= 40)
            },
        )
    }

    fun page(data: JsonElement): Pair<List<BookCatalogItem>, Boolean> {
        require(data.isJsonObject)
        val rows = data.asJsonObject["items"]
        require(rows != null && rows.isJsonArray)
        val items = rows.asJsonArray.map(::item)
        require(items.size <= 100)
        require(items.map { it.id }.toSet().size == items.size)
        val pagination = data.asJsonObject["pagination"]
        val hasMore = pagination?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.get("has_more")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
            ?.asBoolean
            ?: false
        return items to hasMore
    }

    private fun com.google.gson.JsonObject.string(name: String): String? {
        val field = get(name) ?: return null
        if (field.isJsonNull) return null
        require(field.isJsonPrimitive)
        return field.asString
    }
}

@Composable
fun BooksCatalogScreen(
    graph: AppGraph,
    openBook: (Int) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(1) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reduced = reducedMotion()

    DataScreen(
        graph = graph,
        path = "books",
        query = mapOf(
            "q" to query,
            "page" to page.toString(),
        ),
    ) { data, reload ->
        val parsed = remember(data) {
            runCatching { BookCatalogContract.page(data) }
        }
        val result = parsed.getOrNull()

        if (result == null) {
            Box(Modifier.padding(AtharSpacing.Xl)) {
                MessagePanel(stringResource(R.string.books_catalog_invalid), true) {
                    TextButton(onClick = reload) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            return@DataScreen
        }

        val (books, hasMore) = result
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = AtharSpacing.Xxl),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
        ) {
            item(key = "books-intro", contentType = "intro") {
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
                                    stringResource(R.string.books),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    stringResource(R.string.books_catalog_intro),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    if (reduced) listState.scrollToItem(1)
                                    else listState.animateScrollToItem(1)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) {
                            Icon(
                                AtharIcons.Search,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(AtharSpacing.Sm))
                            Text(stringResource(R.string.search))
                        }
                    }
                }
            }

            item(key = "books-controls", contentType = "controls") {
                AtharLayeredContentSurface(
                    modifier = Modifier.fillMaxWidth(),
                    overlap = 20.dp,
                ) {
                    AtharSectionHeader(
                        title = stringResource(R.string.books_browse),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it.take(100) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text(stringResource(R.string.search)) },
                        )
                        IconButton(
                            onClick = {
                                query = input.trim()
                                page = 1
                            },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        ) {
                            Icon(
                                AtharIcons.Search,
                                contentDescription = stringResource(R.string.search),
                            )
                        }
                    }
                }
            }

            if (books.isEmpty()) {
                item(key = "books-empty", contentType = "empty") {
                    Box(Modifier.padding(horizontal = AtharSpacing.Lg)) {
                        QuietEmptyState(
                            stringResource(
                                if (query.isBlank()) {
                                    R.string.books_catalog_empty
                                } else {
                                    R.string.books_catalog_no_match
                                },
                            ),
                        )
                    }
                }
            }

            items(
                items = books,
                key = { it.id },
                contentType = { "book-card" },
            ) { book ->
                BookCatalogCard(
                    book = book,
                    onClick = { openBook(book.id) },
                )
            }

            item(key = "books-pagination", contentType = "pagination") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AtharSpacing.Lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = { page = (page - 1).coerceAtLeast(1) },
                        enabled = page > 1,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.back))
                    }
                    Text(
                        page.toString(),
                        Modifier.padding(AtharSpacing.Lg),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    TextButton(
                        onClick = { page++ },
                        enabled = hasMore,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.more))
                    }
                }
            }
        }
    }
}

@Composable
private fun BookCatalogCard(
    book: BookCatalogItem,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .padding(horizontal = AtharSpacing.Lg)
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 112.dp)
                .padding(AtharSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
        ) {
            Surface(
                modifier = Modifier
                    .width(58.dp)
                    .height(82.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                ),
            ) {
                Box(Modifier.fillMaxSize()) {
                    Icon(
                        AtharIcons.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(AtharSpacing.Md)
                            .fillMaxSize(),
                    )
                }
            }

            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Xs),
            ) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                book.author?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                book.description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Xs),
                ) {
                    book.publicationYear?.let {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(it) },
                        )
                    }
                    book.language?.let {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(it) },
                        )
                    }
                }
            }

            Icon(
                AtharIcons.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
