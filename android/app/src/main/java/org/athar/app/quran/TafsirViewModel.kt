package org.athar.app.quran

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.athar.app.AppGraph
import org.athar.app.core.network.ApiException

internal enum class TafsirIssue { Network, SourceUnavailable }

internal data class TafsirUiState(
    val reference: QuranPosition? = null,
    val verse: AyahText? = null,
    val invalidReference: Boolean = false,
    val catalog: TafsirCatalog? = null,
    val catalogQuery: String = "",
    val catalogRequestedPage: Int = 1,
    val contentRequestedPage: Int = 1,
    val catalogLoading: Boolean = false,
    val catalogOffline: Boolean = false,
    val catalogIssue: TafsirIssue? = null,
    val selectedBook: TafsirBook? = null,
    val reading: TafsirReading? = null,
    val contentLoading: Boolean = false,
    val contentOffline: Boolean = false,
    val contentIssue: TafsirIssue? = null,
)

internal class TafsirViewModel(
    private val graph: AppGraph,
    initialSurah: Int,
    initialAyah: Int,
    private val saved: SavedStateHandle,
) : ViewModel() {
    val initialSurahInput: String = saved["surah"] ?: initialSurah.toString()
    val initialAyahInput: String = saved["ayah"] ?: initialAyah.toString()
    private val mutable = MutableStateFlow(TafsirUiState(selectedBook =
        saved.get<Int>("book_id")?.takeIf { it > 0 }?.let { id ->
            saved.get<String>("book_name")?.takeIf { it.isNotBlank() }?.let { name ->
                TafsirBook(id, name, saved["book_author"] ?: "")
            }
        },
    ))
    val state = mutable.asStateFlow()
    private var referenceJob: Job? = null
    private var catalogJob: Job? = null
    private var contentJob: Job? = null

    init {
        openReference(initialSurahInput, initialAyahInput, (saved.get<Int>("content_page") ?: 1).coerceIn(1, 10000))
        loadCatalog()
    }

    private fun issue(failure: Throwable): TafsirIssue =
        if (
            failure is ApiException &&
            (failure.status == 404 || failure.status >= 500)
        ) {
            TafsirIssue.SourceUnavailable
        } else {
            TafsirIssue.Network
        }

    fun openReference(surahInput: String, ayahInput: String, contentPage: Int = 1) {
        referenceJob?.cancel()
        referenceJob = viewModelScope.launch {
            try {
                val data = graph.quran.data()
                val position = QuranReferenceInput.position(
                    data.index,
                    surahInput,
                    ayahInput,
                ) ?: error("Invalid Quran reference")
                val verse = data.verses[position.globalIndex - 1]
                contentJob?.cancel()
                saved["surah"] = position.surah.toString()
                saved["ayah"] = position.ayah.toString()
                mutable.value = mutable.value.copy(
                    contentLoading = false,
                    reference = position,
                    verse = verse,
                    invalidReference = false,
                    reading = null,
                    contentIssue = null,
                    contentOffline = false,
                )
                if (mutable.value.selectedBook != null) loadContent(contentPage)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(invalidReference = true)
            }
        }
    }

    fun loadCatalog(
        page: Int = mutable.value.catalogRequestedPage,
        query: String = mutable.value.catalogQuery,
    ) {
        val reference = mutable.value.reference ?: QuranPosition(1, 1, 1)
        val safeQuery = query.trim().take(80)
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            mutable.value = mutable.value.copy(
                catalogQuery = safeQuery,
                catalogRequestedPage = page.coerceAtLeast(1),
                catalogLoading = true,
                catalogIssue = null,
            )
            try {
                val result = graph.content.get(
                    "quran/tafsirs",
                    mapOf(
                        "surah" to reference.surah.toString(),
                        "page" to page.coerceAtLeast(1).toString(),
                        "per_page" to CATALOG_PAGE_SIZE.toString(),
                        "q" to safeQuery,
                    ),
                )
                mutable.value = mutable.value.copy(
                    catalog = TafsirContract.catalog(result.data),
                    catalogLoading = false,
                    catalogOffline = result.offline,
                    catalogIssue = null,
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                mutable.value = mutable.value.copy(
                    catalogLoading = false,
                    catalogIssue = issue(failure),
                )
            }
        }
    }

    fun selectBook(book: TafsirBook) {
        saved["book_id"] = book.id
        saved["book_name"] = book.name
        saved["book_author"] = book.author
        mutable.value = mutable.value.copy(
            selectedBook = book,
            reading = null,
            contentIssue = null,
            contentOffline = false,
        )
        loadContent(1)
    }

    fun loadContent(page: Int = mutable.value.contentRequestedPage) {
        val reference = mutable.value.reference ?: return
        val book = mutable.value.selectedBook ?: return
        contentJob?.cancel()
        contentJob = viewModelScope.launch {
            mutable.value = mutable.value.copy(
                contentLoading = true,
                contentRequestedPage = page.coerceAtLeast(1),
                contentIssue = null,
            )
            try {
                val result = graph.content.get(
                    "quran/tafsir",
                    mapOf(
                        "surah" to reference.surah.toString(),
                        "ayah" to reference.ayah.toString(),
                        "book" to book.id.toString(),
                        "page" to page.coerceAtLeast(1).toString(),
                        "per_page" to CONTENT_PAGE_SIZE.toString(),
                    ),
                )
                val reading = TafsirContract.reading(result.data)
                if (mutable.value.reference != reference || mutable.value.selectedBook?.id != book.id) return@launch
                require(
                    reading.surah == reference.surah &&
                        reading.ayah == reference.ayah &&
                        reading.book.id == book.id,
                )
                saved["content_page"] = reading.pagination.page
                mutable.value = mutable.value.copy(
                    reading = reading,
                    selectedBook = reading.book,
                    contentLoading = false,
                    contentOffline = result.offline,
                    contentIssue = null,
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                mutable.value = mutable.value.copy(
                    contentLoading = false,
                    contentIssue = issue(failure),
                )
            }
        }
    }

    fun previousCatalogPage() {
        val page = mutable.value.catalog?.pagination ?: return
        if (page.hasPrevious) loadCatalog(page.page - 1)
    }

    fun nextCatalogPage() {
        val page = mutable.value.catalog?.pagination ?: return
        if (page.hasMore) loadCatalog(page.page + 1)
    }

    fun previousContentPage() {
        val page = mutable.value.reading?.pagination ?: return
        if (page.hasPrevious) loadContent(page.page - 1)
    }

    fun nextContentPage() {
        val page = mutable.value.reading?.pagination ?: return
        if (page.hasMore) loadContent(page.page + 1)
    }

    companion object {
        private const val CATALOG_PAGE_SIZE = 20
        private const val CONTENT_PAGE_SIZE = 8
    }
}
