package org.athar.app.books

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.gson.JsonElement
import org.athar.app.R
import org.athar.app.core.ui.*

internal fun bookDownloadAllowed(item: JsonElement): Boolean = item.text("can_download") == "true" &&
    item.text("rights_basis").isNotBlank() && BookDownloadPolicy.safeHttps(item.text("source_url"))

@Composable internal fun BookRights(item: JsonElement, failed: () -> Unit) {
    val handler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("publication_year" to R.string.book_publication_year, "language" to R.string.book_language,
            "rights_holder" to R.string.book_rights_holder, "rights_basis" to R.string.book_rights_basis).forEach { (field, label) ->
            item.text(field).takeIf { it.isNotBlank() }?.let { value ->
                if (field == "publication_year" || field == "language") Text(stringResource(label, value), style = MaterialTheme.typography.bodyMedium)
                else {
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text(stringResource(if (bookDownloadAllowed(item)) R.string.book_download_allowed else R.string.book_download_unavailable))
        val source = item.text("source_url")
        if (BookDownloadPolicy.safeHttps(source)) TextButton(onClick = {
            try { handler.openUri(source) } catch (_: Exception) { failed() }
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.book_open_source)) }
    }
}
