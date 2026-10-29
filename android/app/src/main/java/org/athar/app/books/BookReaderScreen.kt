package org.athar.app.books

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*
import org.athar.app.quran.QuranReferenceInput

private enum class BookReadError { MISSING, INTEGRITY, PROGRESS, UNSUPPORTED }
private data class BookReaderState(val loading: Boolean = true, val error: BookReadError? = null, val pages: Int = 0, val page: Int = 0, val rendering: Boolean = false, val bitmap: Bitmap? = null)

private class BookReaderViewModel(private val graph: AppGraph, application: Application, private val scope: BookScope, private val book: Int) : AndroidViewModel(application) {
    private fun authorize() = graph.session.requireVerifiedOwner(scope.owner)
    private val mutable = MutableStateFlow(BookReaderState())
    val state = mutable.asStateFlow()
    private var client: BookPdfClient? = null
    private var rendering: Job? = null
    private var opening: Job? = null
    private val renderLock = Mutex()
    private var copy: BookCopy? = null
    private var generation = 0L
    private val reading = BookReadingStore(application, graph)

    init {
        open()
        viewModelScope.launch {
            combine(graph.session.state,graph.deletionStore.blockedOwners){_,_->runCatching{authorize()}.isSuccess}.collect{allowed->
                if(!allowed){generation++;opening?.cancel();rendering?.cancel();client?.close();client=null;copy=null;mutable.value=BookReaderState(loading=false,error=BookReadError.PROGRESS)}
            }
        }
    }
    fun open() {
        val ticket = ++generation
        opening?.cancel(); rendering?.cancel(); client?.close(); client = null
        opening = viewModelScope.launch {
            mutable.value = BookReaderState()
            var descriptor: ParcelFileDescriptor? = null
            var verified: BookCopy? = null
            var reader: BookPdfClient? = null
            try {
                try {
                    withContext(Dispatchers.IO) {
                        authorize()
                        descriptor = bookFiles(getApplication(), scope).openVerified(book) { snapshot ->
                            verified = snapshot
                            ParcelFileDescriptor.open(snapshot.file, ParcelFileDescriptor.MODE_READ_ONLY)
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (ticket != generation) return@launch
                    mutable.value = BookReaderState(loading = false, error = if (failure.message == "book_copy_missing") BookReadError.MISSING else BookReadError.INTEGRITY)
                    return@launch
                }
                authorize(); check(ticket == generation)
                copy = checkNotNull(verified)
                val connected = BookPdfClient(getApplication(), ::authorize); reader = connected; client = connected
                val pages = connected.open(checkNotNull(descriptor))
                require(pages > 0)
                val page = try { withContext(Dispatchers.IO) { authorize(); reading.read(scope, book, checkNotNull(copy).sha256, pages) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { connected.close(); currentCoroutineContext().ensureActive(); if (ticket == generation) mutable.value = BookReaderState(loading = false, error = BookReadError.PROGRESS); return@launch }
                authorize(); if (ticket == generation) mutable.value = BookReaderState(loading = false, pages = pages, page = page)
            } catch (_: TimeoutCancellationException) { reader?.close(); if (ticket == generation) mutable.value = BookReaderState(loading = false, error = BookReadError.UNSUPPORTED) }
            catch (cancelled: CancellationException) { reader?.close(); throw cancelled }
            catch (_: Exception) { reader?.close(); currentCoroutineContext().ensureActive(); if (ticket == generation) mutable.value = BookReaderState(loading = false, error = BookReadError.UNSUPPORTED) }
            finally { runCatching { descriptor?.close() } }
        }
    }

    fun page(index: Int) {
        if (mutable.value.rendering || index !in 0 until mutable.value.pages || index == mutable.value.page) return
        val ticket = generation
        val digest = checkNotNull(copy).sha256
        val pages = mutable.value.pages
        mutable.value = mutable.value.copy(rendering = true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { authorize(); reading.save(scope, book, digest, pages, index) }
                authorize(); if (ticket == generation) mutable.value = mutable.value.copy(page = index, bitmap = null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { currentCoroutineContext().ensureActive(); if (ticket == generation) mutable.value = mutable.value.copy(error = BookReadError.PROGRESS, rendering = false) }
        }
    }

    fun render(width: Int) {
        rendering?.cancel(); client?.cancelRender()
        val page = mutable.value.page
        rendering = viewModelScope.launch {
            mutable.value = mutable.value.copy(rendering = true)
            try {
                val bitmap = renderLock.withLock { checkNotNull(client).render(page, width) }
                authorize()
                if (mutable.value.page == page) mutable.value = mutable.value.copy(bitmap = bitmap, rendering = false)
                else bitmap.recycle()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { currentCoroutineContext().ensureActive(); mutable.value = mutable.value.copy(rendering = false, error = BookReadError.UNSUPPORTED) }
        }
    }

    suspend fun external(context: Context) = withContext(Dispatchers.IO) {
        authorize()
        val uri = bookFiles(context, scope).openVerified(book) { FileProvider.getUriForFile(context, context.packageName + ".books", it.file, "book-$book.pdf") }
        withContext(Dispatchers.Main) {
            authorize()
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.book_open_external)))
        }
    }
    override fun onCleared() { client?.close(); super.onCleared() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun BookReaderScreen(graph: AppGraph, book: Int) {
    val context = LocalContext.current
    val owner = requireNotNull(graph.session.userId)
    val scope = remember(graph.partition, owner) { BookScope(graph.partition, owner) }
    val application = context.applicationContext as Application
    val vm: BookReaderViewModel = viewModel(key = "book-reader:${scope.key}:$book", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = BookReaderViewModel(graph, application, scope, book) as T
    })
    val state by vm.state.collectAsStateWithLifecycle()
    var zoom by rememberSaveable(scope.key, book) { mutableFloatStateOf(1f) }
    var pageInput by rememberSaveable(scope.key, book, state.page) { mutableStateOf("${state.page + 1}") }
    var externalFailed by remember { mutableStateOf(false) }
    var controls by rememberSaveable(scope.key, book) { mutableStateOf(false) }
    val coroutine = rememberCoroutineScope()
    val canExternal = !state.loading && state.error !in setOf(BookReadError.INTEGRITY, BookReadError.MISSING)
    fun external() { coroutine.launch { try { vm.external(context); externalFailed = false } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { externalFailed = true } } }
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val viewport = (maxWidth - 24.dp).coerceAtLeast(1.dp)
            val density = LocalDensity.current
            val pixels = with(density) { (viewport * zoom).toPx().toInt().coerceIn(1, 1800) }
            if (!state.loading && state.error == null) {
                LaunchedEffect(state.page, pixels) { vm.render(pixels) }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(12.dp)) {
                    if (state.rendering) LinearProgressIndicator(Modifier.width(viewport))
                    state.bitmap?.let { bitmap -> Image(bitmap.asImageBitmap(), stringResource(R.string.book_page_description, state.page + 1, state.pages), Modifier.width(viewport * zoom).aspectRatio(bitmap.width.toFloat() / bitmap.height).testTag("book-pdf-page"), contentScale = ContentScale.Fit) }
                }
            } else if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (state.error == BookReadError.MISSING) QuietEmptyState(stringResource(R.string.empty_state))
            else Column(Modifier.padding(20.dp)) {
                MessagePanel(stringResource(when (state.error) {
                    BookReadError.PROGRESS -> R.string.local_data_unreadable
                    BookReadError.INTEGRITY -> R.string.book_integrity_failed
                    else -> R.string.book_render_failed
                }), true) { TextButton(onClick = vm::open) { Text(stringResource(R.string.retry)) } }
                if (canExternal) TextButton(onClick = ::external) { Text(stringResource(R.string.book_open_external)) }
            }
        }
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.pages > 0 && state.error == null) {
                IconButton(onClick = { vm.page(state.page - 1) }, enabled = state.page > 0 && !state.rendering) { Icon(AtharIcons.ArrowBack, stringResource(R.string.book_previous_page)) }
                Text(stringResource(R.string.book_page_count, state.page + 1, state.pages), Modifier.padding(vertical = 12.dp))
                IconButton(onClick = { vm.page(state.page + 1) }, enabled = state.page < state.pages - 1 && !state.rendering) { Icon(AtharIcons.ArrowForward, stringResource(R.string.book_next_page)) }
            }
            TextButton(onClick = { controls = true }, enabled = !state.loading) { Text(stringResource(R.string.book_reader_controls)) }
        }
        if (externalFailed) Text(stringResource(R.string.book_render_failed), color = MaterialTheme.colorScheme.error)
    }
    if (controls) ModalBottomSheet(onDismissRequest = { controls = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.pages > 0 && state.error == null) {
                Row {
                    TextButton(onClick = { zoom = (zoom - .25f).coerceAtLeast(1f) }, enabled = zoom > 1f && !state.rendering) { Text(stringResource(R.string.book_zoom_out)) }
                    TextButton(onClick = { zoom = (zoom + .25f).coerceAtMost(3f) }, enabled = zoom < 3f && !state.rendering) { Text(stringResource(R.string.book_zoom_in)) }
                }
                OutlinedTextField(pageInput, { value -> if (QuranReferenceInput.canonicalizeDigits(value, 10) != null) pageInput = value }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.book_page)) })
                val page = QuranReferenceInput.number(pageInput, 1..state.pages, 10)
                TextButton(onClick = { page?.let { vm.page(it - 1); controls = false } }, enabled = page != null && !state.rendering) { Text(stringResource(R.string.book_go_page)) }
            }
            TextButton(onClick = ::external, enabled = canExternal) { Text(stringResource(R.string.book_open_external)) }
        }
    }
}
