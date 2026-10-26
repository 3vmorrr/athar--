package org.athar.app.quran

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ScrollState
import org.athar.app.R
import org.athar.app.core.ui.AtharSpacing
import org.athar.app.core.ui.QuranFont

@Composable
internal fun QuranMushafPage(
    verses: List<AyahText>,
    fontSize: Int,
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = null,
    scrollTargetGlobalIndex: Int? = null,
    scrollRequest: Long = 0L,
    onLongPressAyah: (AyahText) -> Unit,
) {
    val indexed = remember(verses) { verses.associateBy { it.position.globalIndex } }
    val text = remember(verses) { mushafAnnotatedText(verses) }
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }

    LaunchedEffect(layout, scrollTargetGlobalIndex, scrollRequest) {
        val target = scrollTargetGlobalIndex ?: return@LaunchedEffect
        val result = layout ?: return@LaunchedEffect
        val range = text
            .getStringAnnotations(tag = "ayah", start = 0, end = text.length)
            .firstOrNull { it.item == target.toString() }
            ?: return@LaunchedEffect
        val y = result.getBoundingBox(range.start).top.toInt().coerceAtLeast(0)
        scrollState?.scrollTo(y)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quran-mushaf-page"),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp)
                .pointerInput(text) {
                    detectTapGestures(
                        onLongPress = { position ->
                            val result = layout ?: return@detectTapGestures
                            val offset = result.getOffsetForPosition(position)
                            val annotation = text
                                .getStringAnnotations(
                                    tag = "ayah",
                                    start = offset,
                                    end = offset,
                                )
                                .firstOrNull()
                            val global = annotation?.item?.toIntOrNull()
                            if (global != null) indexed[global]?.let(onLongPressAyah)
                        },
                    )
                },
            onTextLayout = { layout = it },
            fontFamily = QuranFont,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.95f).sp,
            style = MaterialTheme.typography.bodyLarge.copy(
                textDirection = TextDirection.Rtl,
            ),
        )
    }
}

@Composable
internal fun QuranSearchResults(
    verses: List<AyahText>,
    fontSize: Int,
    modifier: Modifier = Modifier,
    onLongPressAyah: (AyahText) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = AtharSpacing.Lg,
            vertical = AtharSpacing.Md,
        ),
    ) {
        items(
            verses,
            key = { it.position.globalIndex },
            contentType = { "quran-search-result" },
        ) { ayah ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AtharSpacing.Sm)
                    .pointerInput(ayah.position.globalIndex) {
                        detectTapGestures(onLongPress = { onLongPressAyah(ayah) })
                    },
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
                        ayah.text,
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
                }
            }
        }
    }
}

@Composable
internal fun QuranAyahActionSheet(
    ayah: AyahText,
    bookmarked: Boolean,
    canSavePosition: Boolean,
    onDismiss: () -> Unit,
    onSavePosition: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AtharSpacing.Xl)
                .padding(bottom = 24.dp),
        ) {
            Text(
                "${ayah.position.surah}:${ayah.position.ayah}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                ayah.text,
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = AtharSpacing.Lg),
                fontFamily = QuranFont,
                fontSize = 22.sp,
                lineHeight = 40.sp,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDirection = TextDirection.Rtl,
                ),
            )
            if (canSavePosition) {
                Button(
                    onClick = {
                        onSavePosition()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Text(stringResource(R.string.set_reading_position))
                }
            }
            TextButton(
                onClick = {
                    onToggleBookmark()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(
                    stringResource(
                        if (bookmarked) R.string.remove_bookmark
                        else R.string.bookmark,
                    ),
                )
            }
        }
    }
}

private fun mushafAnnotatedText(verses: List<AyahText>): AnnotatedString =
    buildAnnotatedString {
        verses.forEachIndexed { index, ayah ->
            if (ayah.bismillah != null) {
                if (length > 0) append("\n\n")
                append(ayah.bismillah)
                append("\n\n")
            } else if (index > 0 && ayah.position.ayah == 1) {
                append("\n\n")
            }

            pushStringAnnotation(
                tag = "ayah",
                annotation = ayah.position.globalIndex.toString(),
            )
            append(ayah.text)
            append(" ")
            append(ayahMarker(ayah.position.ayah))
            pop()

            if (index != verses.lastIndex) append(" ")
        }
    }

private fun ayahMarker(number: Int): String {
    val digits = number.toString().map { digit ->
        if (digit in '0'..'9') ('\u0660'.code + (digit - '0')).toChar() else digit
    }.joinToString("")
    return "﴿$digits﴾"
}
