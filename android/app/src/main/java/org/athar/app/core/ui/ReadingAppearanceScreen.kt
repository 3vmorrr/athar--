package org.athar.app.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.data.AppSettings
import kotlin.math.roundToInt

@Composable
fun ReadingAppearanceScreen(graph: AppGraph) {
    val stored by graph.settings.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings(),
    )
    var previewSize by remember(stored.quranFontSize) {
        mutableFloatStateOf(stored.quranFontSize.toFloat())
    }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AtharSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Column(
                Modifier.padding(AtharSpacing.Xl),
                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            AtharIcons.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.padding(AtharSpacing.Md),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.reading_appearance),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            stringResource(R.string.appearance_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    stringResource(R.string.appearance_device_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AtharSectionHeader(stringResource(R.string.theme))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
        ) {
            listOf(
                "light" to R.string.light,
                "system" to R.string.system_theme,
                "dark" to R.string.dark,
            ).forEach { (value, label) ->
                FilterChip(
                    selected = stored.theme == value,
                    onClick = {
                        scope.launch {
                            graph.settings.theme(value)
                        }
                    },
                    label = { Text(stringResource(label)) },
                )
            }
        }

        AtharSectionHeader(stringResource(R.string.font_size))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Column(
                Modifier.padding(AtharSpacing.Xl),
                verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
            ) {
                Text(
                    stringResource(
                        R.string.quran_font_size_value,
                        previewSize.roundToInt(),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Slider(
                    value = previewSize,
                    onValueChange = { previewSize = it },
                    onValueChangeFinished = {
                        scope.launch {
                            graph.settings.quranFontSize(previewSize.roundToInt())
                        }
                    },
                    valueRange = 18f..48f,
                    steps = 29,
                    modifier = Modifier.fillMaxWidth(),
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        Modifier.padding(AtharSpacing.Lg),
                        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                    ) {
                        Text(
                            stringResource(R.string.appearance_preview),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.quran),
                            modifier = Modifier.fillMaxWidth(),
                            fontFamily = QuranFont,
                            fontSize = previewSize.sp,
                            lineHeight = (previewSize * 1.75f).sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
