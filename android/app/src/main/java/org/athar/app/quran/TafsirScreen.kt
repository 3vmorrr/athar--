package org.athar.app.quran

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.*
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*

@Composable fun TafsirScreen(graph: AppGraph, fontSize: Int) {
    val vm: TafsirViewModel = viewModel(
        key = "tafsir:${graph.session.userId}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                TafsirViewModel(graph, 1, 1, extras.createSavedStateHandle()) as T
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    var surahInput by rememberSaveable { mutableStateOf(vm.initialSurahInput) }
    var ayahInput by rememberSaveable { mutableStateOf(vm.initialAyahInput) }
    var selector by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(surahInput, { surahInput = QuranReferenceInput.canonicalizeDigits(it) ?: surahInput }, Modifier.weight(1f), label = { Text(stringResource(R.string.surah)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(ayahInput, { ayahInput = QuranReferenceInput.canonicalizeDigits(it) ?: ayahInput }, Modifier.weight(1f), label = { Text(stringResource(R.string.ayah)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.openReference(surahInput, ayahInput) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.show_ayah)) }
                OutlinedButton(onClick = { selector = true }, modifier = Modifier.weight(1f)) { Text(state.selectedBook?.name ?: stringResource(R.string.choose_tafsir), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
            }
            if (state.invalidReference) Text(stringResource(R.string.invalid_input), color = MaterialTheme.colorScheme.error)
        }
        when {
            state.selectedBook == null -> Box(Modifier.padding(20.dp)) { AtharScreenIdentity(stringResource(R.string.tafsir), AtharIcons.MenuBook, subtitle = stringResource(R.string.choose_tafsir)) }
            state.contentLoading && state.reading == null -> LoadingScreen()
            state.contentIssue != null && state.reading == null -> TafsirIssuePanel(requireNotNull(state.contentIssue)) { vm.loadContent() }
            state.reading != null -> TafsirReadingContent(state, { vm.loadContent() }, vm::previousContentPage, vm::nextContentPage, fontSize)
        }
    }
    if (selector) TafsirSourceSheet(state, { selector = false }, { vm.loadCatalog(1, it) }, { vm.loadCatalog() }, vm::previousCatalogPage, vm::nextCatalogPage) {
        vm.selectBook(it)
        selector = false
    }
}
