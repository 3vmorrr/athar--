package org.athar.app.nightprayer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import org.athar.app.R
import org.athar.app.core.ui.*

/** Receives only a readable history's measured aggregate; unreadable history is handled by its owner. */
@Composable internal fun NightPaceContent(pace: NightMeasuredPace?, modifier: Modifier = Modifier) {
    Column(modifier.testTag("night-pace"), verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
        AtharSectionHeader(stringResource(R.string.night_pace_heading))
        if (pace == null) Text(stringResource(R.string.night_pace_unavailable), modifier = Modifier.testTag("night-pace-unavailable"))
        else {
            Text(stringResource(R.string.night_measured_pace, pace.secondsPerVerse), modifier = Modifier.testTag("night-pace-average"))
            Text(stringResource(R.string.night_pace_samples, pace.sessions, pace.verses), style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.night_pace_notice), style = MaterialTheme.typography.bodySmall)
    }
}
