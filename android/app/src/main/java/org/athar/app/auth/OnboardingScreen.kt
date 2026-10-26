package org.athar.app.auth

import org.athar.app.core.ui.AtharIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.athar.app.R
import org.athar.app.core.ui.Brand
import org.athar.app.core.ui.AtharMotion
import org.athar.app.core.ui.reducedMotion

@Composable fun OnboardingScreen(complete: () -> Unit) {
    val pager = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val titles = listOf(R.string.onboard_quran_title, R.string.onboard_knowledge_title, R.string.onboard_worship_title, R.string.onboard_private_title)
    val bodies = listOf(R.string.onboard_quran_body, R.string.onboard_knowledge_body, R.string.onboard_worship_body, R.string.onboard_private_body)
    val reduced = reducedMotion()
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
    Column(Modifier.widthIn(max = 640.dp).fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Brand(Modifier.size(60.dp)); TextButton(onClick = complete, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.skip)) } }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                OnboardingIllustration(page, Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 240.dp).aspectRatio(4f / 3f))
                Spacer(Modifier.height(24.dp))
                Text(stringResource(titles[page]), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(bodies[page]), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { repeat(4) { index ->
            val width by animateDpAsState(if (index == pager.currentPage) 24.dp else 7.dp, tween(if (reduced) 0 else AtharMotion.Fast), label = "onboarding-step")
            Box(Modifier.size(width, 7.dp).background(if (index == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape))
        } }
        Text(stringResource(R.string.step_of, pager.currentPage + 1, 4), Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
        Button(onClick = { if (pager.currentPage == 3) complete() else scope.launch { if (reduced) pager.scrollToPage(pager.currentPage + 1) else pager.animateScrollToPage(pager.currentPage + 1) } }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !pager.isScrollInProgress) { Text(stringResource(if (pager.currentPage == 3) R.string.start else R.string.continue_action)) }
    }
    }
}
