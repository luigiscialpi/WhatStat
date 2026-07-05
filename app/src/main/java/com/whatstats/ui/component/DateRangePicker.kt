package com.whatstats.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.whatstats.R
import com.whatstats.data.db.model.DateRange
import com.whatstats.telemetry.TrackOverlayView
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionName")
fun DateRangePicker(
    dateRange: DateRange?,
    onDateRangeChanged: (DateRange?) -> Unit,
    modifier: Modifier = Modifier,
    onSubmitClicked: (() -> Unit)? = null,
    isSearching: Boolean = false,
    onCancelClicked: (() -> Unit)? = null,
    minDate: Long? = null,
    maxDate: Long? = null,
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yy", Locale.ITALIAN) }

    // Vincoli globali
    val globalMaxDate = maxDate ?: System.currentTimeMillis()
    val globalMinDate =
        minDate?.let { ms ->
            Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
                .toLocalDate()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val transparentColors =
                TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                )

            // Da: Field
            Box(modifier = Modifier.weight(1f)) {
                TextField(
                    value = dateRange?.startTime?.let { dateFormatter.format(Date(it)) } ?: "",
                    onValueChange = {},
                    placeholder = {
                        Text(
                            stringResource(R.string.date_from),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    },
                    readOnly = true,
                    colors = transparentColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(Color.Transparent)
                            .clickable { showStartPicker = true },
                )
            }

            Text("-", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))

            // A: Field
            Box(modifier = Modifier.weight(1f)) {
                TextField(
                    value = dateRange?.endTime?.let { dateFormatter.format(Date(it)) } ?: "",
                    onValueChange = {},
                    placeholder = {
                        Text(
                            stringResource(R.string.date_to),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    },
                    readOnly = true,
                    colors = transparentColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(Color.Transparent)
                            .clickable { showEndPicker = true },
                )
            }

            if (dateRange != null) {
                IconButton(onClick = { onDateRangeChanged(null) }) {
                    Icon(Icons.Default.Clear, tint = Color.Gray, contentDescription = stringResource(R.string.clear))
                }
            }

            if (onSubmitClicked != null && dateRange != null) {
                FilledTonalButton(
                    onClick = {
                        if (isSearching && onCancelClicked != null) {
                            onCancelClicked()
                        } else {
                            onSubmitClicked()
                        }
                    },
                    colors =
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor =
                                if (isSearching) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                            contentColor =
                                if (isSearching) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                        ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Icon(
                        if (isSearching) Icons.Default.Close else Icons.Filled.Search,
                        contentDescription = if (isSearching) stringResource(R.string.cancel) else stringResource(R.string.search_button),
                    )
                }
            }
        }
    }

    if (showStartPicker) {
        val minYear =
            globalMinDate
                ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).year }
                ?: 1900
        val maxYear = Instant.ofEpochMilli(globalMaxDate).atZone(ZoneId.systemDefault()).year

        val startPickerState =
            rememberDatePickerState(
                // Se non c'è ancora una data "Da", preseleziona la data "A" già scelta (se esiste)
                initialSelectedDateMillis = dateRange?.startTime ?: dateRange?.endTime,
                yearRange = minYear..maxYear,
                selectableDates =
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                            val minOk = globalMinDate?.let { utcTimeMillis >= it } ?: true
                            val maxOk = utcTimeMillis <= globalMaxDate
                            // Non permettere date dopo quella di fine già impostata
                            val vsEndOk = dateRange?.endTime?.let { utcTimeMillis <= it } ?: true
                            return minOk && maxOk && vsEndOk
                        }

                        override fun isSelectableYear(year: Int) = year in minYear..maxYear
                    },
            )

        TrackOverlayView("date_picker_start")
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        startPickerState.selectedDateMillis?.let { selected ->
                            onDateRangeChanged(
                                DateRange(startTime = selected, endTime = dateRange?.endTime),
                            )
                            showStartPicker = false
                        }
                    },
                    enabled = startPickerState.selectedDateMillis != null,
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(
                state = startPickerState,
                title = {
                    Text(
                        text = stringResource(R.string.date_from),
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
    }

    if (showEndPicker) {
        // Minimo per la data di fine: il maggiore tra globalMinDate e la data di inizio già scelta
        val effectiveMin = listOfNotNull(globalMinDate, dateRange?.startTime).maxOrNull()
        val minYear =
            effectiveMin
                ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).year }
                ?: 1900
        val maxYear = Instant.ofEpochMilli(globalMaxDate).atZone(ZoneId.systemDefault()).year

        val endPickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = dateRange?.endTime,
                yearRange = minYear..maxYear,
                selectableDates =
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                            val minOk = effectiveMin?.let { utcTimeMillis >= it } ?: true
                            val maxOk = utcTimeMillis <= globalMaxDate
                            return minOk && maxOk
                        }

                        override fun isSelectableYear(year: Int) = year in minYear..maxYear
                    },
            )

        TrackOverlayView("date_picker_end")
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        endPickerState.selectedDateMillis?.let { selected ->
                            onDateRangeChanged(
                                DateRange(startTime = dateRange?.startTime, endTime = selected),
                            )
                            showEndPicker = false
                        }
                    },
                    enabled = endPickerState.selectedDateMillis != null,
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(
                state = endPickerState,
                title = {
                    Text(
                        text = stringResource(R.string.date_to),
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
    }
}
