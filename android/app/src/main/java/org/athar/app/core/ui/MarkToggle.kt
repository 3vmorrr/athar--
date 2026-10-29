package org.athar.app.core.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.athar.app.R
import org.athar.app.core.network.ApiException

@Composable fun MarkToggle(identity: String, initial: Boolean, favorite: Boolean, feedback: SnackbarHostState, enabled: Boolean = true, onChange: suspend (Boolean) -> Unit) {
    var selected by remember(identity, initial) { mutableStateOf(initial) }
    var busy by remember(identity) { mutableStateOf(false) }
    val reduced = reducedMotion()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.network_error)
    val pending = stringResource(R.string.syncing)
    val scale by animateFloatAsState(if (selected) 1.04f else 1f, if (reduced) snap() else spring(dampingRatio = AtharMotion.SmallSpringDamping, stiffness = AtharMotion.SmallSpringStiffness), label = "mark-feedback")
    val label = stringResource(if (favorite) { if (selected) R.string.remove_favorite else R.string.add_favorite } else { if (selected) R.string.remove_bookmark else R.string.bookmark })
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        IconToggleButton(checked = selected, enabled = enabled && !busy, onCheckedChange = { next ->
            val previous = selected; selected = next; busy = true
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch {
                try { onChange(next) }
                catch (failure: CancellationException) { throw failure }
                catch (failure: Exception) { selected = previous; busy = false; feedback.showSnackbar((failure as? ApiException)?.message?.takeIf { it.isNotBlank() } ?: failed) }
                finally { busy = false }
            }
        }, modifier = Modifier.fillMaxSize().semantics { if (busy) stateDescription = pending }) {
            Crossfade(selected, animationSpec = tween(if (reduced) 0 else AtharMotion.Fast), label = "mark-fill") { active ->
                Icon(if (favorite) { if (active) AtharIcons.FavoriteFilled else AtharIcons.Favorite } else { if (active) AtharIcons.BookmarkFilled else AtharIcons.Bookmark }, label, Modifier.size(24.dp).graphicsLayer { scaleX = scale; scaleY = scale }, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (busy) CircularProgressIndicator(Modifier.align(Alignment.BottomEnd).size(10.dp), strokeWidth = 1.5.dp)
    }
}
