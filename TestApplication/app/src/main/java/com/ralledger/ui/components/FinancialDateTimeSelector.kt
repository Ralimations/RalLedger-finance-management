package com.ralledger.ui.components

import android.text.format.DateFormat as AndroidDateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ralledger.ui.settings.displayDate
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Consistent, editable local date and time controls for financial forms. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialDateTimeSelector(
    timestamp: Long,
    onTimestampChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val locale = Locale.getDefault()
    val dateLabel = displayDate(timestamp)
    val timeLabel = remember(timestamp, locale) {
        DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(timestamp))
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = { showDatePicker = true },
            enabled = enabled
        ) {
            Text("Date · $dateLabel", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = { showTimePicker = true },
            enabled = enabled
        ) {
            Text("Time · $timeLabel", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = localTimestampToPickerDateMillis(timestamp)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { pickerMillis ->
                        onTimestampChange(pickerDateToLocalTimestamp(pickerMillis, timestamp))
                    }
                    showDatePicker = false
                }) { Text("Set date") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val currentTime = remember(timestamp) { java.util.Calendar.getInstance().apply { timeInMillis = timestamp } }
        val context = LocalContext.current
        val timePickerState = rememberTimePickerState(
            initialHour = currentTime.get(java.util.Calendar.HOUR_OF_DAY),
            initialMinute = currentTime.get(java.util.Calendar.MINUTE),
            is24Hour = AndroidDateFormat.is24HourFormat(context)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Set time") },
            text = { TimePicker(state = timePickerState, modifier = Modifier.padding(vertical = 4.dp)) },
            confirmButton = {
                TextButton(onClick = {
                    onTimestampChange(localTimestampWithTime(timestamp, timePickerState.hour, timePickerState.minute))
                    showTimePicker = false
                }) { Text("Set time") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } }
        )
    }
}
