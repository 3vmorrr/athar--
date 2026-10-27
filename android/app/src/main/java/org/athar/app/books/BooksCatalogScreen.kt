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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
            item(key = "books-hero", contentType = "hero") {
                AtharImmersiveHero(
                    presentation = AtharHeroPresentation(
                        artwork = AtharHeroArtwork.Books,
                        eyebrow = stringResource(R.string.knowledge),
                        title = stringResource(R.string.books),
                        supportingText = stringResource(R.string.books_catalog_intro),
                        primaryActionLabel = stringResource(R.string.search),
                    ),
                    onPrimaryAction = {
                        scope.launch {
                            if (reduced) listState.scrollToItem(1)
                            else listState.animateScrollToItem(1)
                        }
                    },
                )
            }

            item(key = "books-controls", contentType = "controls") {
                AtharLayeredContentSurface(
                    modifier = Modifier.fillMaxWidth(),
                    overlap = 20.dp,
                ) {
                    AtharSectionHeader(
                        title = stringResource(R.string.books_browse),
                    )
                    AtharSearchField(
                        value = input,
                        onValueChange = { input = it.take(100) },
                        onSearch = {
                            query = input.trim()
                            page = 1
                        },
                        label = stringResource(R.string.search),
                    )
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
                AtharPageControls(
                    page = page,
                    hasPrevious = page > 1,
                    hasNext = hasMore,
                    previousLabel = stringResource(R.string.back),
                    nextLabel = stringResource(R.string.more),
                    onPrevious = { page = (page - 1).coerceAtLeast(1) },
                    onNext = { page++ },
                    modifier = Modifier.padding(horizontal = AtharSpacing.Lg),
                )
            }
        }
    }
}

@Composable
private fun BookCatalogCard(
    book: BookCatalogItem,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = AtharSpacing.Lg)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .padding(horizontal = AtharSpacing.Lg, vertical = AtharSpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .width(52.dp)
                    .height(70.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Icon(
                        AtharIcons.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val metadata = listOfNotNull(book.publicationYear, book.language)
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
                if (metadata.isNotBlank()) {
                    Text(
                        metadata,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            Icon(
                AtharIcons.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
