package org.athar.app.books

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.athar.app.R
import org.athar.app.core.ui.AtharSpacing
import org.athar.app.core.ui.MessagePanel
import org.athar.app.core.ui.QuietEmptyState
import kotlin.math.roundToInt

private data class BookReaderUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val corrupt: Boolean = false,
    val pageCount: Int = 0,
    val page: Int = 0,
    val bitmap: Bitmap? = null,
    val rendering: Boolean = false,
)

private class BookReaderViewModel(
    application: Application,
    private val owner: Long,
    private val bookId: Int,
) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(BookReaderUiState())
    val state = mutable.asStateFlow()

    private val renderMutex = Mutex()
    private var renderJob: Job? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val preferences = application.getSharedPreferences(
        BookStorage.READER_PREFERENCES,
        Context.MODE_PRIVATE,
    )
    private val pageKey = "page:$owner:$bookId"

    init {
        open()
    }

    private fun open() {
        viewModelScope.launch {
            mutable.value = BookReaderUiState(loading = true)
            try {
                val context = getApplication<Application>()
                if (!BookStorage.isDownloaded(context, owner, bookId)) {
                    mutable.value = BookReaderUiState(
                        loading = false,
                        missing = true,
                    )
                    return@launch
                }

                val valid = BookStorage.verify(context, owner, bookId)
                if (!valid) {
                    mutable.value = BookReaderUiState(
                        loading = false,
                        corrupt = true,
                    )
                    return@launch
                }

                val opened = withContext(Dispatchers.IO) {
                    val file = BookStorage.pdf(context, owner, bookId)
                    val descriptor = ParcelFileDescriptor.open(
                        file,
                        ParcelFileDescriptor.MODE_READ_ONLY,
                    )
                    descriptor to PdfRenderer(descriptor)
                }
                descriptor = opened.first
                renderer = opened.second

                val pageCount = opened.second.pageCount
                if (pageCount < 1) {
                    closeRenderer()
                    mutable.value = BookReaderUiState(
                        loading = false,
                        corrupt = true,
                    )
                    return@launch
                }

                val page = preferences
                    .getInt(pageKey, 0)
                    .coerceIn(0, pageCount - 1)
                mutable.value = BookReaderUiState(
                    loading = false,
                    pageCount = pageCount,
                    page = page,
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                closeRenderer()
                mutable.value = BookReaderUiState(
                    loading = false,
                    corrupt = true,
                )
            }
        }
    }

    fun page(index: Int) {
        val current = mutable.value
        if (
            current.pageCount < 1 ||
            index !in 0 until current.pageCount ||
            index == current.page
        ) {
            return
        }
        preferences.edit().putInt(pageKey, index).apply()
        mutable.value = current.copy(
            page = index,
            bitmap = null,
        )
    }

    fun render(widthPx: Int) {
        val current = mutable.value
        if (
            current.loading ||
            current.corrupt ||
            current.missing ||
            current.pageCount < 1 ||
            widthPx < 1
        ) {
            return
        }

        renderJob?.cancel()
        val pageIndex = current.page
        renderJob = viewModelScope.launch {
            try {
                mutable.value = mutable.value.copy(rendering = true)
                val bitmap = withContext(Dispatchers.IO) {
                    renderMutex.withLock {
                        val renderer = renderer ?: throw IllegalStateException()
                        if (pageIndex !in 0 until renderer.pageCount) {
                            throw IllegalArgumentException()
                        }
                        renderer.openPage(pageIndex).use { page ->
                            val width = widthPx.coerceIn(320, 1800)
                            val proportional = (
                                page.height.toDouble() /
                                    page.width.toDouble() *
                                    width.toDouble()
                                )
                                .roundToInt()
                                .coerceAtLeast(1)
                            val height = proportional.coerceAtMost(3600)
                            Bitmap.createBitmap(
                                width,
                                height,
                                Bitmap.Config.ARGB_8888,
                            ).also { target ->
                                target.eraseColor(Color.WHITE)
                                page.render(
                                    target,
                                    null,
                                    null,
                                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                                )
                            }
                        }
                    }
                }

                if (mutable.value.page == pageIndex) {
                    mutable.value = mutable.value.copy(
                        bitmap = bitmap,
                        rendering = false,
                    )
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(
                    rendering = false,
                    corrupt = true,
                    bitmap = null,
                )
            }
        }
    }

    fun removeCorruptCopy() {
        renderJob?.cancel()
        closeRenderer()
        BookStorage.remove(getApplication(), owner, bookId)
        preferences.edit().remove(pageKey).apply()
        mutable.value = BookReaderUiState(
            loading = false,
            missing = true,
        )
    }

    private fun closeRenderer() {
        runCatching { renderer?.close() }
        renderer = null
        runCatching { descriptor?.close() }
        descriptor = null
    }

    override fun onCleared() {
        renderJob?.cancel()
        closeRenderer()
        super.onCleared()
    }
}

@Composable
fun BookReaderScreen(
    owner: Long,
    bookId: Int,
) {
    val application = LocalContext.current.applicationContext as Application
    val vm: BookReaderViewModel = viewModel(
        key = "book-reader:$owner:$bookId",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BookReaderViewModel(application, owner, bookId) as T
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    when {
        state.loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        state.missing -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(AtharSpacing.Xl),
                contentAlignment = Alignment.Center,
            ) {
                QuietEmptyState(stringResource(R.string.book_not_downloaded))
            }
        }

        state.corrupt -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(AtharSpacing.Xl),
                contentAlignment = Alignment.Center,
            ) {
                MessagePanel(
                    stringResource(R.string.book_integrity_failed),
                    true,
                ) {
                    TextButton(onClick = vm::removeCorruptCopy) {
                        Text(stringResource(R.string.book_remove_corrupt))
                    }
                }
            }
        }

        else -> {
            Column(Modifier.fillMaxSize()) {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val widthPx = with(density) {
                        maxWidth.toPx().roundToInt()
                    }.coerceAtLeast(1)

                    LaunchedEffect(state.page, widthPx) {
                        vm.render(widthPx)
                    }

                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(AtharSpacing.Md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when {
                            state.bitmap != null -> {
                                Image(
                                    bitmap = state.bitmap!!.asImageBitmap(),
                                    contentDescription = stringResource(
                                        R.string.book_page_description,
                                        state.page + 1,
                                        state.pageCount,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("book-pdf-page"),
                                    contentScale = ContentScale.FillWidth,
                                )
                            }
                            else -> {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(320.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp,
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(AtharSpacing.Md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                    ) {
                        OutlinedButton(
                            onClick = { vm.page(state.page - 1) },
                            enabled = state.page > 0 && !state.rendering,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                        ) {
                            Text(stringResource(R.string.back))
                        }

                        Text(
                            stringResource(
                                R.string.book_page_count,
                                state.page + 1,
                                state.pageCount,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )

                        Button(
                            onClick = { vm.page(state.page + 1) },
                            enabled = state.page < state.pageCount - 1 &&
                                !state.rendering,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                        ) {
                            Text(stringResource(R.string.book_next_page))
                        }
                    }
                }
            }
        }
    }
}
