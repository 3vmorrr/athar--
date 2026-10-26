package org.athar.app.notifications

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.AtharHeroSurface
import org.athar.app.core.ui.AtharSpacing
import org.athar.app.core.ui.AtharStatusPill
import org.athar.app.core.ui.MessagePanel
import org.athar.app.core.ui.QuietEmptyState

data class NotificationInboxItem(
    val id: Int,
    val category: String,
    val sourceType: String,
    val sourceId: String?,
    val title: String,
    val body: String,
    val destinationToken: String,
    val destination: NotificationDestination,
    val read: Boolean,
    val readAt: String?,
    val expiresAt: String,
    val createdAt: String,
)

data class NotificationInboxPage(
    val items: List<NotificationInboxItem>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val unreadCount: Int,
)

object NotificationInboxContract {
    private val categories = NotificationPreferenceContract.categories

    fun item(data: JsonElement): NotificationInboxItem {
        require(data.isJsonObject)
        val row = data.asJsonObject
        val id = row.requiredInt("id")
        require(id > 0)
        val category = row.requiredString("category")
        require(category in categories)
        val sourceType = row.requiredString("source_type")
        require(sourceType.matches(Regex("[a-z0-9_]{1,40}")))
        val sourceId = row.optionalString("source_id")
        require(sourceId == null || (sourceId.length <= 120 && sourceId.none { it.code < 32 }))
        val title = row.requiredString("title")
        require(title.isNotBlank() && title.length <= 160)
        val body = row.requiredString("body")
        require(body.length <= 500)
        val destinationToken = row.requiredString("destination")
        val destination = requireNotNull(NotificationDeepLinks.decode(destinationToken))
        val read = row.requiredBoolean("read")
        val readAt = row.optionalString("read_at")
        val expiresAt = row.requiredUtc("expires_at")
        val createdAt = row.requiredUtc("created_at")
        readAt?.let { require(it.endsWith("Z") && it.length in 20..40) }

        return NotificationInboxItem(
            id = id,
            category = category,
            sourceType = sourceType,
            sourceId = sourceId,
            title = title,
            body = body,
            destinationToken = destinationToken,
            destination = destination,
            read = read,
            readAt = readAt,
            expiresAt = expiresAt,
            createdAt = createdAt,
        )
    }

    fun page(data: JsonElement): NotificationInboxPage {
        require(data.isJsonObject)
        val root = data.asJsonObject
        val rows = root.get("items")
        require(rows != null && rows.isJsonArray)
        val items = rows.asJsonArray.map(::item)
        require(items.size <= 50)
        require(items.map { it.id }.toSet().size == items.size)
        require(items.zipWithNext().all { (a, b) -> a.id > b.id })

        val paginationElement = root.get("pagination")
        require(paginationElement != null && paginationElement.isJsonObject)
        val pagination = paginationElement.asJsonObject
        val cursor = pagination.optionalString("next_cursor")
        if (cursor != null) require(cursor.length in 1..180)
        val unread = root.requiredInt("unread_count")
        require(unread >= 0)

        return NotificationInboxPage(
            items = items,
            nextCursor = cursor,
            hasMore = pagination.requiredBoolean("has_more"),
            unreadCount = unread,
        )
    }

    private fun JsonObject.requiredString(name: String): String {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }

    private fun JsonObject.requiredUtc(name: String): String {
        val value = requiredString(name)
        require(value.endsWith("Z") && value.length in 20..40)
        return value
    }

    private fun JsonObject.requiredInt(name: String): Int {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        return value.asInt
    }

    private fun JsonObject.requiredBoolean(name: String): Boolean {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isBoolean)
        return value.asBoolean
    }

    private fun JsonObject.optionalString(name: String): String? {
        val value = get(name) ?: return null
        if (value.isJsonNull) return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }
}

data class NotificationInboxUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: Boolean = false,
    val items: List<NotificationInboxItem> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val unreadCount: Int = 0,
    val selected: NotificationInboxItem? = null,
    val detailBusy: Boolean = false,
    val detailError: Boolean = false,
)

class NotificationInboxViewModel(
    private val graph: AppGraph,
    private val owner: Long,
) : ViewModel() {
    private val mutable = MutableStateFlow(NotificationInboxUiState())
    val state = mutable.asStateFlow()

    init { load(reset = true) }

    fun load(reset: Boolean) {
        val snapshot = mutable.value
        if (snapshot.loadingMore || (snapshot.loading && !reset)) return
        if (!reset && !snapshot.hasMore) return

        viewModelScope.launch {
            val current = mutable.value
            mutable.value = if (reset) {
                current.copy(loading = true, loadingMore = false, error = false)
            } else {
                current.copy(loadingMore = true, error = false)
            }
            try {
                graph.session.requireVerifiedOwner(owner)
                val cursor = if (reset) null else current.nextCursor
                val query = buildMap<String, String> {
                    put("per_page", "20")
                    cursor?.let { put("cursor", it) }
                }
                val page = NotificationInboxContract.page(
                    graph.session.request(
                        "GET",
                        "me/notification-inbox",
                        query = query,
                        expectedUser = owner,
                    ),
                )
                graph.session.requireVerifiedOwner(owner)
                val merged = if (reset) {
                    page.items
                } else {
                    (current.items + page.items)
                        .associateBy { it.id }
                        .values
                        .sortedByDescending { it.id }
                }
                mutable.value = mutable.value.copy(
                    loading = false,
                    loadingMore = false,
                    error = false,
                    items = merged,
                    nextCursor = page.nextCursor,
                    hasMore = page.hasMore,
                    unreadCount = page.unreadCount,
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(
                    loading = false,
                    loadingMore = false,
                    error = true,
                )
            }
        }
    }

    fun open(item: NotificationInboxItem) {
        if (mutable.value.detailBusy) return
        mutable.value = mutable.value.copy(
            selected = item,
            detailBusy = !item.read,
            detailError = false,
        )
        if (item.read) return
        viewModelScope.launch {
            try {
                graph.session.requireVerifiedOwner(owner)
                val updated = NotificationInboxContract.item(
                    graph.session.request(
                        "PUT",
                        "me/notification-inbox/" + item.id + "/read",
                        expectedUser = owner,
                    ),
                )
                graph.session.requireVerifiedOwner(owner)
                applyDetail(updated, becameRead = !item.read && updated.read)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(detailBusy = false, detailError = true)
            }
        }
    }

    fun retryDetail() {
        val selected = mutable.value.selected ?: return
        if (mutable.value.detailBusy) return
        viewModelScope.launch {
            mutable.value = mutable.value.copy(detailBusy = true, detailError = false)
            try {
                graph.session.requireVerifiedOwner(owner)
                var updated = NotificationInboxContract.item(
                    graph.session.request(
                        "GET",
                        "me/notification-inbox/" + selected.id,
                        expectedUser = owner,
                    ),
                )
                if (!updated.read) {
                    updated = NotificationInboxContract.item(
                        graph.session.request(
                            "PUT",
                            "me/notification-inbox/" + selected.id + "/read",
                            expectedUser = owner,
                        ),
                    )
                }
                graph.session.requireVerifiedOwner(owner)
                applyDetail(updated, becameRead = !selected.read && updated.read)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(detailBusy = false, detailError = true)
            }
        }
    }

    fun deleteSelected() {
        val selected = mutable.value.selected ?: return
        if (mutable.value.detailBusy) return
        viewModelScope.launch {
            mutable.value = mutable.value.copy(detailBusy = true, detailError = false)
            try {
                graph.session.requireVerifiedOwner(owner)
                graph.session.request(
                    "DELETE",
                    "me/notification-inbox/" + selected.id,
                    expectedUser = owner,
                )
                graph.session.requireVerifiedOwner(owner)
                mutable.value = mutable.value.copy(
                    selected = null,
                    detailBusy = false,
                    detailError = false,
                    items = mutable.value.items.filterNot { it.id == selected.id },
                    unreadCount = (
                        mutable.value.unreadCount - if (selected.read) 0 else 1
                    ).coerceAtLeast(0),
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(detailBusy = false, detailError = true)
            }
        }
    }

    fun closeDetail() {
        mutable.value = mutable.value.copy(
            selected = null,
            detailBusy = false,
            detailError = false,
        )
    }

    private fun applyDetail(updated: NotificationInboxItem, becameRead: Boolean) {
        mutable.value = mutable.value.copy(
            selected = updated,
            detailBusy = false,
            detailError = false,
            items = mutable.value.items.map { if (it.id == updated.id) updated else it },
            unreadCount = (
                mutable.value.unreadCount - if (becameRead) 1 else 0
            ).coerceAtLeast(0),
        )
    }
}

@Composable
fun NotificationInboxScreen(
    graph: AppGraph,
    open: (String) -> Unit,
) {
    val owner = requireNotNull(graph.session.userId)
    val vm: NotificationInboxViewModel = viewModel(
        key = "notification-inbox:" + owner,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NotificationInboxViewModel(graph, owner) as T
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.selected != null) { vm.closeDetail() }

    val selected = state.selected
    if (selected != null) {
        NotificationInboxDetail(
            item = selected,
            busy = state.detailBusy,
            error = state.detailError,
            onBack = vm::closeDetail,
            onRetry = vm::retryDetail,
            onDelete = vm::deleteSelected,
            onOpenDestination = { openInboxDestination(selected.destination, open) },
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AtharSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
    ) {
        item(key = "inbox-hero", contentType = "hero") {
            AtharHeroSurface(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.notification_inbox),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(R.string.notification_inbox_unread, state.unreadCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when {
            state.loading && state.items.isEmpty() -> item(key = "loading") {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            state.error && state.items.isEmpty() -> item(key = "error") {
                MessagePanel(stringResource(R.string.inbox_load_failed), true) {
                    TextButton(onClick = { vm.load(reset = true) }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            state.items.isEmpty() -> item(key = "empty") {
                QuietEmptyState(stringResource(R.string.inbox_empty))
            }
        }

        items(
            items = state.items,
            key = { it.id },
            contentType = { "inbox-item" },
        ) { item ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.open(item) },
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(AtharSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (!item.read) {
                            AtharStatusPill(
                                stringResource(R.string.inbox_unread),
                                emphasized = true,
                            )
                        }
                    }
                    Text(
                        item.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    Text(
                        item.createdAt,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.error && state.items.isNotEmpty()) item(key = "page-error") {
            MessagePanel(stringResource(R.string.inbox_more_failed), true)
        }

        if (state.hasMore) item(key = "more") {
            Button(
                onClick = { vm.load(reset = false) },
                enabled = !state.loadingMore,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.loadingMore) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.load_more))
                }
            }
        }
    }
}

@Composable
private fun NotificationInboxDetail(
    item: NotificationInboxItem,
    busy: Boolean,
    error: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onOpenDestination: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AtharSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
    ) {
        item(key = "back") {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        item(key = "detail") {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(AtharSpacing.Xl),
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                ) {
                    Text(item.title, style = MaterialTheme.typography.headlineSmall)
                    Text(item.body, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        item.createdAt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (error) {
                        MessagePanel(stringResource(R.string.inbox_detail_failed), true) {
                            TextButton(onClick = onRetry) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                    if (item.destination !is NotificationDestination.Inbox) {
                        Button(
                            onClick = onOpenDestination,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.inbox_open_destination))
                        }
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        }
    }
}

private fun openInboxDestination(
    destination: NotificationDestination,
    open: (String) -> Unit,
) {
    when (destination) {
        is NotificationDestination.Lesson -> open("lessons/" + destination.id)
        is NotificationDestination.Series -> open("series/" + destination.id)
        NotificationDestination.Home -> open("home")
        NotificationDestination.Quran -> open("quran")
        NotificationDestination.Adhkar -> open("adhkar")
        NotificationDestination.NightPrayer -> open("night-prayer")
        NotificationDestination.Inbox -> Unit
    }
}
