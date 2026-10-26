package org.athar.app.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.athar.app.R
import java.time.*

/** Date picker milliseconds represent a calendar day in UTC, never a prayer instant. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DateField(value: String, change: (String) -> Unit, label: String, modifier: Modifier = Modifier, latest: LocalDate? = null) {
    var picker by remember { mutableStateOf(false) }
    OutlinedTextField(value, {}, modifier.fillMaxWidth(), label = { Text(label) }, readOnly = true, singleLine = true, trailingIcon = { IconButton(onClick = { picker = true }) { Icon(AtharIcons.CalendarMonth, label) } })
    if (picker) {
        val selected = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
        val state = rememberDatePickerState(initialSelectedDateMillis = selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), yearRange = 1900..LocalDate.now().year + 5, selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = latest == null || Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= latest
        })
        DatePickerDialog(onDismissRequest = { picker = false }, confirmButton = { TextButton(enabled = state.selectedDateMillis != null, onClick = { state.selectedDateMillis?.let { change(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; picker = false }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = { picker = false }) { Text(stringResource(R.string.cancel)) } }) { DatePicker(state) }
    }
}
