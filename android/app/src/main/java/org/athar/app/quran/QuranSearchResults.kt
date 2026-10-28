package org.athar.app.quran

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ScrollState
import org.athar.app.R
import org.athar.app.core.ui.AtharSpacing
import org.athar.app.core.ui.QuranFont

@Composable
internal fun QuranSearchResults(
    page: QuranSearchPage,
    fontSize: Int,
    modifier: Modifier = Modifier,
    onOpenAyah: (AyahText) -> Unit,
    onLongPressAyah: (AyahText) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(page.query, page.offset) { listState.scrollToItem(0) }
    LazyColumn(
        modifier = modifier.testTag("quran-search-results"),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = AtharSpacing.Lg,
            vertical = AtharSpacing.Md,
        ),
    ) {
        item(key = "search-pager-top", contentType = "search-pager") {
            QuranSearchPager(page, onPreviousPage, onNextPage)
        }
        items(
            page.hits,
            key = { it.ayah.position.globalIndex },
            contentType = { "quran-search-result" },
        ) { hit ->
            val ayah = hit.ayah
            val highlight = MaterialTheme.colorScheme.tertiaryContainer
            val foreground = MaterialTheme.colorScheme.onTertiaryContainer
            val annotated = remember(hit, highlight, foreground) {
                buildAnnotatedString {
                    append(ayah.text)
                    hit.highlightRanges.forEach { range ->
                        if (range.first >= 0 && range.last < ayah.text.length) {
                            addStyle(
                                SpanStyle(
                                    background = highlight,
                                    color = foreground,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                range.first,
                                range.last + 1,
                            )
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AtharSpacing.Sm)
                    .testTag("quran-search-result-${ayah.position.globalIndex}")
                    .combinedClickable(
                        onClick = { onOpenAyah(ayah) },
                        onLongClick = { onLongPressAyah(ayah) },
                    ),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(AtharSpacing.Lg)) {
                    Text(
                        "${ayah.position.surah}:${ayah.position.ayah}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        annotated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AtharSpacing.Sm),
                        fontFamily = QuranFont,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.9f).sp,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDirection = TextDirection.Rtl,
                        ),
                    )
                    Text(
                        stringResource(R.string.quran_open_ayah),
                        modifier = Modifier.padding(top = AtharSpacing.Sm),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item(key = "search-pager-bottom", contentType = "search-pager") {
            QuranSearchPager(page, onPreviousPage, onNextPage)
        }
    }
}

@Composable
private fun QuranSearchPager(
    page: QuranSearchPage,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = AtharSpacing.Sm),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(AtharSpacing.Sm),
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.quran_search_results_count, page.total),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                stringResource(
                    R.string.quran_search_page,
                    page.pageNumber,
                    page.pageCount,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            TextButton(
                onClick = onPreviousPage,
                enabled = page.hasPrevious,
            ) {
                Text(stringResource(R.string.previous_page))
            }
            TextButton(
                onClick = onNextPage,
                enabled = page.hasNext,
            ) {
                Text(stringResource(R.string.next_page))
            }
        }
    }
}
