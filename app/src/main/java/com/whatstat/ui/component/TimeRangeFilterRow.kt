package com.whatstat.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whatstat.R
import com.whatstat.data.db.model.DateRange
import com.whatstat.data.db.model.TimeRange

/**
 * Riga filtri temporali riutilizzabile: carosello chip + DateRangePicker.
 * Il carosello si auto-scrolla al chip selezionato ad ogni cambio di [selectedRange].
 */
@Composable
@Suppress("FunctionName")
fun TimeRangeFilterRow(
    selectedRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit,
    customDateRange: DateRange?,
    onDateRangeChanged: (DateRange?) -> Unit,
    modifier: Modifier = Modifier,
    onSubmitClicked: (() -> Unit)? = null,
    isSearching: Boolean = false,
    onCancelClicked: (() -> Unit)? = null,
    minDate: Long? = null,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    val timeRangeItems = TimeRange.entries
    val initialIndex =
        remember(timeRangeItems, selectedRange) {
            timeRangeItems.indexOf(selectedRange).coerceAtLeast(0)
        }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    LaunchedEffect(selectedRange) {
        val idx = timeRangeItems.indexOf(selectedRange)
        if (idx >= 0) {
            listState.scrollToItem(idx)
        }
        if (selectedRange != TimeRange.CUSTOM) {
            showDatePicker = false
        }
    }
    Column(modifier = modifier) {
        /* Label contestuale sopra al carosello: chiarisce allo sguardo che i chip
         * controllano il periodo di analisi di tutte le statistiche a schermo. */
        Text(
            text = stringResource(R.string.time_range_label),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 0.dp),
        )
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            items(timeRangeItems) { range ->
                FilterChip(
                    selected = selectedRange == range,
                    onClick = {
                        if (range == TimeRange.CUSTOM) {
                            showDatePicker = !showDatePicker
                        } else {
                            showDatePicker = false
                            onRangeSelected(range)
                        }
                    },
                    label = {
                        val labelRes =
                            when (range) {
                                TimeRange.ALL_TIME -> R.string.time_range_all
                                TimeRange.LAST_30_DAYS -> R.string.time_range_30_days
                                TimeRange.LAST_6_MONTHS -> R.string.time_range_6_months
                                TimeRange.LAST_YEAR -> R.string.time_range_year
                                TimeRange.CUSTOM -> R.string.time_range_custom_button
                            }
                        Text(stringResource(labelRes))
                    },
                     /* Icona calendario solo per il chip CUSTOM, per comunicare visivamente
                      * che aprira' un date picker. Negli altri chip nessuna icona. */
                    leadingIcon =
                        if (range == TimeRange.CUSTOM) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.DateRange,
                                    contentDescription = null,
                                )
                            }
                        } else {
                            null
                        },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                )
            }
        }
        if (showDatePicker) {
            DateRangePicker(
                dateRange = customDateRange,
                onDateRangeChanged = onDateRangeChanged,
                onSubmitClicked = onSubmitClicked,
                isSearching = isSearching,
                onCancelClicked = onCancelClicked,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                minDate = minDate,
            )
        }
    }
}
