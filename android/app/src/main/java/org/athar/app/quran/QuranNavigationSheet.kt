package org.athar.app.quran

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.athar.app.R
import org.athar.app.core.ui.AtharSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuranNavigationSheet(
    index: QuranIndex,
    state: QuranState,
    dismiss: () -> Unit,
    openPosition: (Int, Int) -> Unit,
    openSection: (String, Int) -> Unit,
    openRange: (QuranRange) -> Unit,
    initialKind: String = state.sectionKind,
) {
    val currentPosition = remember(index, state.scrollTarget) {
        state.scrollTarget?.let { runCatching { index.position(it) }.getOrNull() }
    }
    val currentRange = state.range
    val initialMode = initialKind
        .takeIf { it in setOf("surah", "juz", "hizb", "page", "range") } ?: "surah"
    var mode by rememberSaveable(initialKind) { mutableStateOf(initialMode) }
    var surah by rememberSaveable(state.scrollTarget) {
        mutableStateOf((currentPosition?.surah ?: state.surah.takeIf { it > 0 } ?: 1).toString())
    }
    var ayah by rememberSaveable(state.scrollTarget) {
        mutableStateOf((currentPosition?.ayah ?: 1).toString())
    }
    var section by rememberSaveable(state.sectionKind, state.sectionNumber) {
        mutableStateOf((state.sectionNumber.takeIf { it > 0 } ?: 1).toString())
    }
    var startSurah by rememberSaveable(currentRange) {
        mutableStateOf((currentRange?.start?.surah ?: 1).toString())
    }
    var startAyah by rememberSaveable(currentRange) {
        mutableStateOf((currentRange?.start?.ayah ?: 1).toString())
    }
    var endSurah by rememberSaveable(currentRange) {
        mutableStateOf((currentRange?.end?.surah ?: 1).toString())
    }
    var endAyah by rememberSaveable(currentRange) {
        mutableStateOf((currentRange?.end?.ayah ?: 1).toString())
    }

    val position = if (mode == "surah") QuranReferenceInput.position(index, surah, ayah) else null
    val sectionNumber = if (mode in setOf("juz", "hizb", "page")) {
        QuranReferenceInput.sectionNumber(index, mode, section)
    } else null
    val range = if (mode == "range") {
        QuranReferenceInput.range(index, startSurah, startAyah, endSurah, endAyah)
    } else null
    val valid = position != null || sectionNumber != null || range != null

    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = AtharSpacing.Xl)
                .padding(bottom = AtharSpacing.Xxl),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
        ) {
            Text(stringResource(R.string.quran_go_to), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.quran_reference_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
            ) {
                listOf(
                    "surah" to R.string.quran_surah_ayah,
                    "juz" to R.string.juz,
                    "hizb" to R.string.hizb,
                    "page" to R.string.page,
                    "range" to R.string.quran_range,
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = mode == key,
                        onClick = { mode = key },
                        label = { Text(stringResource(label)) },
                        modifier = Modifier.testTag("quran-nav-mode-$key"),
                    )
                }
            }
            when (mode) {
                "surah" -> {
                    QuranNumberField(surah, { surah = it }, R.string.surah, "quran-nav-surah")
                    val parsed = QuranReferenceInput.number(surah, 1..114)
                    val maxAyah = parsed?.let { index.surahs[it - 1].ayahCount }
                    QuranNumberField(
                        ayah,
                        { ayah = it },
                        R.string.ayah,
                        "quran-nav-ayah",
                        maxAyah?.let { "1–$it" },
                    )
                }
                "juz", "hizb", "page" -> {
                    val label = when (mode) {
                        "juz" -> R.string.juz
                        "hizb" -> R.string.hizb
                        else -> R.string.page
                    }
                    QuranNumberField(
                        section,
                        { section = it },
                        label,
                        "quran-nav-section",
                        "1–${index.sectionCount(mode)}",
                    )
                }
                "range" -> {
                    QuranNumberField(startSurah, { startSurah = it }, R.string.start_surah, "quran-nav-start-surah")
                    QuranNumberField(startAyah, { startAyah = it }, R.string.start_ayah, "quran-nav-start-ayah")
                    QuranNumberField(endSurah, { endSurah = it }, R.string.end_surah, "quran-nav-end-surah")
                    QuranNumberField(endAyah, { endAyah = it }, R.string.end_ayah, "quran-nav-end-ayah")
                }
            }
            if (!valid) {
                Text(
                    stringResource(
                        if (mode == "range") R.string.quran_range_invalid
                        else R.string.quran_reference_invalid,
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = {
                    when {
                        position != null -> openPosition(position.surah, position.ayah)
                        sectionNumber != null -> openSection(mode, sectionNumber)
                        range != null -> openRange(range)
                    }
                    dismiss()
                },
                enabled = valid,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("quran-nav-open"),
            ) {
                Text(stringResource(R.string.open_reader))
            }
        }
    }
}

@Composable
private fun QuranNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    tag: String,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { candidate ->
            QuranReferenceInput.canonicalizeDigits(candidate)?.let(onValueChange)
        },
        modifier = Modifier.fillMaxWidth().testTag(tag),
        label = { Text(stringResource(label)) },
        supportingText = if (supporting != null) ({ Text(supporting) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
