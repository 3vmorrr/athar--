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

enum class ContentSkeletonKind { List, Grid, Detail }

@Composable
fun ContentLoadingSkeleton(kind: ContentSkeletonKind = ContentSkeletonKind.List) {
    val reduced = reducedMotion()
    val opacity: State<Float>? = if (reduced) null else rememberInfiniteTransition(label = "loading").animateFloat(
        0.55f,
        1f,
        infiniteRepeatable(tween(AtharMotion.LoadingPulse), RepeatMode.Reverse),
        label = "loading-opacity",
    )
    val loading = stringResource(R.string.loading)
    val color = MaterialTheme.colorScheme.outlineVariant
    val shape = MaterialTheme.shapes.medium
    fun Modifier.block() = background(color, shape)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .padding(24.dp)
                .semantics {
                    stateDescription = loading
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                }
                .graphicsLayer { alpha = opacity?.value ?: 1f },
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.width(156.dp).height(20.dp).block())
            when (kind) {
                ContentSkeletonKind.Grid -> {
                    Box(Modifier.fillMaxWidth().height(150.dp).block())
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.weight(1f).height(108.dp).block())
                        Box(Modifier.weight(1f).height(108.dp).block())
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.weight(1f).height(150.dp).block())
                        Box(Modifier.weight(1f).height(150.dp).block())
                    }
                }
                ContentSkeletonKind.Detail -> {
                    Box(Modifier.fillMaxWidth().height(180.dp).block())
                    Box(Modifier.fillMaxWidth(0.92f).height(18.dp).block())
                    Box(Modifier.fillMaxWidth(0.72f).height(14.dp).block())
                    Box(Modifier.fillMaxWidth().height(88.dp).block())
                }
                ContentSkeletonKind.List -> {
                    repeat(3) {
                        SurfaceSkeletonRow(color)
                    }
                }
            }
        }
    }
}

@Composable
private fun SurfaceSkeletonRow(color: androidx.compose.ui.graphics.Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).background(color, MaterialTheme.shapes.medium))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth(0.85f).height(14.dp).background(color, MaterialTheme.shapes.small))
            Box(Modifier.fillMaxWidth(0.6f).height(10.dp).background(color, MaterialTheme.shapes.small))
        }
    }
}
