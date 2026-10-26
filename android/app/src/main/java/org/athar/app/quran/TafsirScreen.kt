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
import com.google.gson.JsonElement
import kotlinx.coroutines.*
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*

@Composable fun TafsirScreen(graph: AppGraph, fontSize: Int) {
    var surahInput by remember { mutableStateOf("1") }; var ayahInput by remember { mutableStateOf("1") }
    var reference by remember { mutableStateOf(1 to 1) }
    var verse by remember { mutableStateOf<AyahText?>(null) }
    var book by remember { mutableStateOf<JsonElement?>(null) }; var selector by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reference) { val data = graph.quran.data(); verse = data.verses[data.index.global(reference.first, reference.second) - 1] }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(surahInput, { surahInput = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), label = { Text(stringResource(R.string.surah)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(ayahInput, { ayahInput = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), label = { Text(stringResource(R.string.ayah)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch {
                    try { val surah = surahInput.toInt(); val ayah = ayahInput.toInt(); graph.quran.data().index.global(surah, ayah); if (surah != reference.first) book = null; reference = surah to ayah; invalid = false }
                    catch (failure: CancellationException) { throw failure } catch (_: Exception) { invalid = true }
                } }) { Text(stringResource(R.string.show_ayah)) }
                OutlinedButton(onClick = { selector = true }) { Text(book?.text("name") ?: stringResource(R.string.choose_tafsir)) }
            }
            if (invalid) Text(stringResource(R.string.invalid_input), color = MaterialTheme.colorScheme.error)
        }
        if (book == null) Box(Modifier.padding(20.dp)) { Text(stringResource(R.string.choose_tafsir)) }
        else DataScreen(graph, "quran/tafsir", mapOf("surah" to "${reference.first}", "ayah" to "${reference.second}", "book" to book!!.text("id"))) { data, _ ->
            val uri = LocalUriHandler.current
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item(key = "verse", contentType = "ayah") { verse?.let { Text(it.text, fontFamily = QuranFont, fontSize = fontSize.sp, lineHeight = (fontSize * 1.9).sp) } }
                item(key = "book", contentType = "metadata") { Text(data.asJsonObject["book"].text("name"), style = MaterialTheme.typography.titleLarge); Text(data.asJsonObject["book"].text("author"), style = MaterialTheme.typography.bodySmall) }
                items(data.array("items").withIndex().toList(), key = { it.index }, contentType = { "tafsir" }) { (_, block) ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(block.text("text"), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.tafsir_reference, block.text("part"), block.text("page")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (data.array("items").isEmpty()) item { Text(stringResource(R.string.empty_state)) }
                item(key = "source", contentType = "source") { TextButton(onClick = { val link = data.asJsonObject["source"].text("url"); if (link.startsWith("https://api.quranpedia.net/")) uri.openUri(link) }) { Text(stringResource(R.string.quranpedia_source)) } }
            }
        }
    }
    if (selector) {
        var page by remember { mutableIntStateOf(1) }
        var input by remember { mutableStateOf("") }; var query by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { selector = false }, title = { Text(stringResource(R.string.choose_tafsir)) }, text = {
            Column(Modifier.height(460.dp)) {
                Row { OutlinedTextField(input, { input = it.take(80) }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.search)) }); TextButton(onClick = { query = input; page = 1 }) { Text(stringResource(R.string.search)) } }
                DataScreen(graph, "quran/tafsirs", mapOf("surah" to "${reference.first}", "page" to "$page", "per_page" to "20", "q" to query)) { data, _ -> LazyColumn {
                items(data.array("items"), key = { it.text("id") }, contentType = { "book" }) { row -> TextButton(onClick = { book = row; selector = false }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(row.text("name")) } }
                if (data.array("items").isEmpty()) item { Text(stringResource(R.string.empty_state)) }
                item { Row { TextButton(onClick = { page-- }, enabled = page > 1) { Text(stringResource(R.string.back)) }; TextButton(onClick = { page++ }, enabled = data.asJsonObject["pagination"].text("has_more") == "true") { Text(stringResource(R.string.more)) } } }
            } } }
        }, confirmButton = { TextButton(onClick = { selector = false }) { Text(stringResource(R.string.close)) } })
    }
}
