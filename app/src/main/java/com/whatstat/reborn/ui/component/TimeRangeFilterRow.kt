package com.whatstat.reborn.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.unit.dp
import com.whatstat.reborn.data.db.model.DateRange
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.R

/**
 * Riga filtri temporali riutilizzabile: carosello chip + DateRangePicker.
 * Il carosello si auto-scrolla al chip selezionato ad ogni cambio di [selectedRange].
 */
@Composable
fun TimeRangeFilterRow(
    selectedRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit,
    customDateRange: DateRange?,
    onDateRangeChanged: (DateRange?) -> Unit,
    modifier: Modifier = Modifier,
    onSubmitClicked: (() -> Unit)? = null,
    isSearching: Boolean = false,
    onCancelClicked: (() -> Unit)? = null,
    minDate: Long? = null
) {
    var showDatePicker by remember { mutableStateOf(false) }

    val timeRangeItems = TimeRange.entries
    val initialIndex = remember(timeRangeItems, selectedRange) {
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
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
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
                         val labelRes = when (range) {
                             TimeRange.ALL_TIME -> R.string.time_range_all
                             TimeRange.LAST_30_DAYS -> R.string.time_range_30_days
                             TimeRange.LAST_6_MONTHS -> R.string.time_range_6_months
                             TimeRange.LAST_YEAR -> R.string.time_range_year
                             TimeRange.CUSTOM -> R.string.time_range_custom_button
                         }
                         Text(stringResource(labelRes))
                     },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
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
                 minDate = minDate
             )
         }
    }
}
