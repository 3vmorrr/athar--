package org.athar.app.quran

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.athar.app.AppGraph
import org.athar.app.R
import org.athar.app.core.ui.*

@Composable
fun BookmarkedAyahScreen(
    graph: AppGraph,
    surah: Int,
    ayah: Int,
) {
    var verse by remember(surah, ayah) { mutableStateOf<AyahText?>(null) }
    var surahName by remember(surah) { mutableStateOf("") }
    var failed by remember(surah, ayah) { mutableStateOf(false) }

    LaunchedEffect(surah, ayah) {
        try {
            val data = graph.quran.data()
            val rows = data.surah(surah)
            verse = rows.firstOrNull { it.position.ayah == ayah }
            surahName = data.index.surahs.getOrNull(surah - 1)?.name.orEmpty()
            failed = verse == null
        } catch (_: Exception) {
            failed = true
        }
    }

    when {
        failed -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(AtharSpacing.Xl),
            ) {
                MessagePanel(
                    stringResource(R.string.account_data_unavailable),
                    true,
                )
            }
        }
        verse == null -> {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        else -> {
            val item = requireNotNull(verse)
            Column(Modifier.fillMaxSize()) {
                AtharImmersiveHero(
                    presentation = AtharHeroPresentation(
                        artwork = AtharHeroArtwork.Quran,
                        eyebrow = stringResource(R.string.bookmarks),
                        title = surahName.ifBlank {
                            stringResource(R.string.quran)
                        },
                        supportingText = stringResource(
                            R.string.reading_position_label,
                            surahName.ifBlank { surah.toString() },
                            ayah,
                        ),
                    ),
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AtharSpacing.Xl),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Text(
                        text = item.text,
                        modifier = Modifier.padding(AtharSpacing.Xxl),
                        fontFamily = QuranFont,
                        fontSize = 30.sp,
                        lineHeight = 56.sp,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDirection = TextDirection.Rtl,
                        ),
                    )
                }
                Text(
                    stringResource(R.string.quran_source),
                    modifier = Modifier.padding(horizontal = AtharSpacing.Xl),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
