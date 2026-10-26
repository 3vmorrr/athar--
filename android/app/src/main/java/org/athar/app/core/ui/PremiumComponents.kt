package org.athar.app.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

object AtharSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp
    val Section = 28.dp
}

@Composable
fun AtharHeroSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ),
                )
                .padding(AtharSpacing.Xxl),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
            content = content,
        )
    }
}

@Composable
fun AtharSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    if(LocalDensity.current.fontScale>=1.3f){
        Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(AtharSpacing.Sm)){
            Text(title,Modifier.semantics{heading()},style=MaterialTheme.typography.titleLarge)
            if(actionLabel!=null&&onAction!=null)TextButton(onClick=onAction,modifier=Modifier.align(Alignment.End)){Text(actionLabel)}
        }
        return
    }
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
    ) {
        Text(title, Modifier.weight(1f).semantics{heading()}, style = MaterialTheme.typography.titleLarge)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun AtharQuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    reduceMotion: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = reduceMotion || reducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduced) 0.97f else 1f,
        animationSpec = if (reduced) snap() else spring(
            stiffness = AtharMotion.SmallSpringStiffness,
            dampingRatio = AtharMotion.SmallSpringDamping,
        ),
        label = "athar-quick-action",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .widthIn(min = 48.dp)
            .heightIn(min = 92.dp)
            .semantics { role=Role.Button }
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha=if(enabled)1f else 0.38f },
        shape = MaterialTheme.shapes.large,
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (emphasized) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
        interactionSource = interaction,
    ) {
        Column(
            Modifier.padding(AtharSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (emphasized) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(22.dp),
                    tint = if (emphasized) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.primary,
                )
            }
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
fun AtharStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (emphasized) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (emphasized) MaterialTheme.colorScheme.onTertiaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AtharMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(AtharSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(AtharSpacing.Xs),
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
