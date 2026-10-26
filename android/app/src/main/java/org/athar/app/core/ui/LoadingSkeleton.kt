package org.athar.app.core.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import org.athar.app.R

@Composable fun ContentLoadingSkeleton() {
    val reduced = reducedMotion()
    val opacity: State<Float>? = if (reduced) null else rememberInfiniteTransition(label = "loading").animateFloat(0.55f, 1f, infiniteRepeatable(tween(AtharMotion.LoadingPulse), RepeatMode.Reverse), label = "loading-opacity")
    val loading = stringResource(R.string.loading)
    val color = MaterialTheme.colorScheme.outlineVariant
    val shape = MaterialTheme.shapes.small
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(24.dp).semantics { stateDescription = loading; progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }.graphicsLayer { alpha = opacity?.value ?: 1f }, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.width(156.dp).height(20.dp).background(color, shape))
            repeat(3) { Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(color, shape))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { Box(Modifier.fillMaxWidth(0.85f).height(14.dp).background(color, shape)); Box(Modifier.fillMaxWidth(0.6f).height(10.dp).background(color, shape)) }
            } }
        }
    }
}
