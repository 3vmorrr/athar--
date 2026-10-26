package org.athar.app.core.ui

import android.animation.ValueAnimator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

object AtharMotion {
    const val Instant = 0
    const val Fast = 120
    const val Normal = 200
    const val Slow = 280
    const val LoadingPulse = 1000
    val Standard = FastOutSlowInEasing
    val Enter = LinearOutSlowInEasing
    val Exit = FastOutLinearInEasing
    const val SmallSpringStiffness = 700f
    const val SmallSpringDamping = 0.85f
}

@Composable fun reducedMotion(): Boolean {
    var reduced by remember { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) reduced = !ValueAnimator.areAnimatorsEnabled() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return reduced
}

/** Never wrap authentication transitions: removing private content on logout is immediate. */
@Composable fun <T> AtharPageTransition(target: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val reduced = reducedMotion()
    AnimatedContent(targetState = target, modifier = modifier, label = "athar-page", transitionSpec = {
        if (reduced) (fadeIn(snap()) togetherWith fadeOut(snap())).using(null)
        else ((fadeIn(tween(AtharMotion.Normal, easing = AtharMotion.Enter)) + scaleIn(tween(AtharMotion.Normal), initialScale = 0.99f)) togetherWith fadeOut(tween(AtharMotion.Fast, easing = AtharMotion.Exit))).using(null)
    }) { current -> content(current) }
}
