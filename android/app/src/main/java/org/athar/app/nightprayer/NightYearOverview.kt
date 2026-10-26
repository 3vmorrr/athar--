package org.athar.app.nightprayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.athar.app.R
import org.athar.app.core.ui.AtharIcons
import org.athar.app.core.ui.AtharSectionHeader
import org.athar.app.core.ui.AtharSpacing
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun NightYearOverview(
    totals: Map<LocalDate, Int>,
    exemptions: Set<LocalDate>,
    initialYear: Int,
    onShowMonth: () -> Unit,
    onOpenMonth: (YearMonth) -> Unit,
) {
    val currentYear = LocalDate.now().year
    var year by rememberSaveable(initialYear) {
        mutableIntStateOf(initialYear.coerceIn(1900, currentYear))
    }
    val months = remember(year, totals, exemptions) {
        NightCalendar.year(
            year = year,
            totals = totals,
            exemptions = exemptions,
        )
    }
    val activeNights = remember(months) { months.sumOf { it.activeNights } }
    val verses = remember(months) { months.sumOf { it.verseCount } }
    val exemptDays = remember(months) { months.sumOf { it.exemptDays } }
    val locale = Locale.getDefault()
    val monthFormat = remember(locale) {
        DateTimeFormatter.ofPattern("LLLL", locale)
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AtharSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Lg),
    ) {
        item(key = "year-mode", contentType = "controls") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
            ) {
                FilterChip(
                    selected = false,
                    onClick = onShowMonth,
                    label = { Text(stringResource(R.string.calendar_month_view)) },
                )
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text(stringResource(R.string.calendar_year_view)) },
                )
            }
        }

        item(key = "year-navigation", contentType = "controls") {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { year-- },
                    enabled = year > 1900,
                ) {
                    Icon(
                        AtharIcons.ArrowBack,
                        stringResource(R.string.previous_year),
                    )
                }
                Text(
                    year.toString(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(
                    onClick = { year++ },
                    enabled = year < currentYear,
                ) {
                    Icon(
                        AtharIcons.ArrowForward,
                        stringResource(R.string.next_year),
                    )
                }
            }
        }

        item(key = "year-summary", contentType = "summary") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    Modifier.padding(AtharSpacing.Xl),
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                ) {
                    AtharSectionHeader(stringResource(R.string.calendar_year_summary))
                    Text(
                        stringResource(
                            R.string.calendar_year_totals,
                            activeNights,
                            verses,
                            exemptDays,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.calendar_year_private),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "year-legend", contentType = "legend") {
            Column(verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm)) {
                AtharSectionHeader(stringResource(R.string.calendar_legend))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(AtharSpacing.Sm),
                ) {
                    YearLegendItem(
                        kind = YearDayKind.Recorded,
                        label = stringResource(R.string.calendar_recorded),
                    )
                    YearLegendItem(
                        kind = YearDayKind.Exempt,
                        label = stringResource(R.string.calendar_local_exemption),
                    )
                    YearLegendItem(
                        kind = YearDayKind.Empty,
                        label = stringResource(R.string.calendar_no_entry),
                    )
                    YearLegendItem(
                        kind = YearDayKind.Future,
                        label = stringResource(R.string.calendar_future),
                    )
                }
            }
        }

        months.forEach { summary ->
            item(
                key = "year-month-${summary.month}",
                contentType = "month-summary",
            ) {
                val monthLabel = summary.month.format(monthFormat)
                val description = stringResource(
                    R.string.calendar_month_accessibility,
                    monthLabel,
                    summary.activeNights,
                    summary.verseCount,
                    summary.exemptDays,
                )
                Surface(
                    onClick = { onOpenMonth(summary.month) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = description },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Column(
                        Modifier.padding(AtharSpacing.Lg),
                        verticalArrangement = Arrangement.spacedBy(AtharSpacing.Md),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                monthLabel,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(
                                    R.string.calendar_month_active_nights,
                                    summary.activeNights,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clearAndSetSemantics { },
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            summary.days.forEach { day ->
                                YearDayDot(
                                    when {
                                        day.future -> YearDayKind.Future
                                        day.verseCount > 0 -> YearDayKind.Recorded
                                        day.exempt -> YearDayKind.Exempt
                                        else -> YearDayKind.Empty
                                    },
                                )
                            }
                        }

                        Text(
                            stringResource(
                                R.string.calendar_month_summary,
                                summary.verseCount,
                                summary.exemptDays,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private enum class YearDayKind {
    Recorded,
    Exempt,
    Empty,
    Future,
}

@Composable
private fun YearLegendItem(
    kind: YearDayKind,
    label: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AtharSpacing.Xs),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        YearDayDot(kind)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun YearDayDot(kind: YearDayKind) {
    val color = when (kind) {
        YearDayKind.Recorded -> MaterialTheme.colorScheme.primary
        YearDayKind.Exempt -> MaterialTheme.colorScheme.tertiary
        YearDayKind.Empty -> MaterialTheme.colorScheme.surfaceVariant
        YearDayKind.Future -> MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        Modifier
            .size(10.dp)
            .background(
                color = color,
                shape = RoundedCornerShape(3.dp),
            ),
    )
}
