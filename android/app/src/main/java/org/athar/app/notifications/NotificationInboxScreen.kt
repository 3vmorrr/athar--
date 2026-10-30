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
import org.athar.app.core.data.ProtectedRead
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
            id, category, sourceType, sourceId, title, body, destinationToken,
            destination, read, readAt, expiresAt, createdAt,
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
            items, cursor, pagination.requiredBoolean("has_more"), unread,
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
    val cacheLoading: Boolean = true,
    val cacheUnreadable: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val networkError: Boolean = false,
    val items: List<NotificationInboxItem> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val unreadCount: Int = 0,
    val pendingCount: Int = 0,
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

    init {
        viewModelScope.launch {
            graph.notificationInbox.observe(owner).collect { read ->
                when (read) {
                    ProtectedRead.Loading -> {
                        if (mutable.value.items.isEmpty()) {
                            mutable.value = mutable.value.copy(cacheLoading = true)
                        }
                    }
                    ProtectedRead.Unreadable -> {
                        mutable.value = mutable.value.copy(
                            cacheLoading = false,
                            cacheUnreadable = true,
                        )
                    }
                    is ProtectedRead.Ready -> {
                        val local = read.value
                        val selectedId = mutable.value.selected?.id
                        mutable.value = mutable.value.copy(
                            cacheLoading = false,
                            cacheUnreadable = false,
                            items = local.items,
                            nextCursor = local.nextCursor,
                            hasMore = local.hasMore,
                            unreadCount = local.unreadCount,
                            pendingCount = local.pendingCount,
                            selected = selectedId?.let { id -> local.items.firstOrNull { it.id == id } },
                        )
                    }
                }
            }
        }
        load(reset = true)
    }

    fun load(reset: Boolean) {
        val snapshot = mutable.value
        if (snapshot.refreshing || snapshot.loadingMore) return
        if (!reset && !snapshot.hasMore) return
        viewModelScope.launch {
            mutable.value = if (reset) {
                mutable.value.copy(refreshing = true, networkError = false)
            } else {
                mutable.value.copy(loadingMore = true, networkError = false)
            }
            try {
                graph.notificationInbox.refresh(owner, reset)
                mutable.value = mutable.value.copy(
                    refreshing = false,
                    loadingMore = false,
                    networkError = false,
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(
                    refreshing = false,
                    loadingMore = false,
                    networkError = true,
                )
            }
        }
    }

    fun open(item: NotificationInboxItem) {
        if (mutable.value.detailBusy || mutable.value.cacheUnreadable) return
        mutable.value = mutable.value.copy(
            selected = item,
            detailBusy = !item.read,
            detailError = false,
        )
        if (item.read) return
        viewModelScope.launch {
            try {
                val local = graph.notificationInbox.markRead(owner, item.id)
                mutable.value = mutable.value.copy(
                    selected = local.items.firstOrNull { it.id == item.id },
                    detailBusy = false,
                    detailError = false,
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(detailBusy = false, detailError = true)
            }
        }
    }

    fun retryDetail() {
        val selected = mutable.value.selected ?: return
        if (mutable.value.detailBusy || mutable.value.cacheUnreadable) return
        if (selected.read) {
            mutable.value = mutable.value.copy(detailError = false)
            return
        }
        viewModelScope.launch {
            mutable.value = mutable.value.copy(detailBusy = true, detailError = false)
            try {
                val local = graph.notificationInbox.markRead(owner, selected.id)
                mutable.value = mutable.value.copy(
                    selected = local.items.firstOrNull { it.id == selected.id },
                    detailBusy = false,
                    detailError = false,
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(detailBusy = false, detailError = true)
            }
        }
    }

    fun deleteSelected() {
        val selected = mutable.value.selected ?: return
        if (mutable.value.detailBusy || mutable.value.cacheUnreadable) return
        viewModelScope.launch {
            mutable.value = mutable.value.copy(detailBusy = true, detailError = false)
            try {
                graph.notificationInbox.delete(owner, selected.id)
                mutable.value = mutable.value.copy(
                    selected = null,
                    detailBusy = false,
                    detailError = false,
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
            pendingCount = state.pendingCount,
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

        if (state.cacheUnreadable) item(key = "cache-unreadable") {
            MessagePanel(stringResource(R.string.local_data_unreadable), true)
        }

        if (state.pendingCount > 0 && !state.cacheUnreadable) item(key = "pending") {
            MessagePanel(
                stringResource(R.string.inbox_pending_sync, state.pendingCount),
                false,
            ) {
                TextButton(onClick = { vm.load(reset = true) }) {
                    Text(stringResource(R.string.retry))
                }
            }
        }

        if (state.networkError && state.items.isNotEmpty()) item(key = "saved-offline") {
            MessagePanel(stringResource(R.string.inbox_showing_saved), false) {
                TextButton(onClick = { vm.load(reset = true) }) {
                    Text(stringResource(R.string.retry))
                }
            }
        }

        when {
            state.cacheLoading && state.items.isEmpty() -> item(key = "loading") {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            state.refreshing && state.items.isEmpty() -> item(key = "refreshing") {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            state.networkError && state.items.isEmpty() && !state.cacheUnreadable -> item(key = "error") {
                MessagePanel(stringResource(R.string.inbox_load_failed), true) {
                    TextButton(onClick = { vm.load(reset = true) }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            state.items.isEmpty() && !state.cacheUnreadable -> item(key = "empty") {
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
                    .clickable(enabled = !state.cacheUnreadable) { vm.open(item) },
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

        if (state.hasMore) item(key = "more") {
            Button(
                onClick = { vm.load(reset = false) },
                enabled = !state.loadingMore && !state.refreshing && !state.cacheUnreadable,
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
    pendingCount: Int,
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
                    if (pendingCount > 0) {
                        Text(
                            stringResource(R.string.inbox_pending_sync, pendingCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                        Text(stringResource(R.string.delete_action))
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
