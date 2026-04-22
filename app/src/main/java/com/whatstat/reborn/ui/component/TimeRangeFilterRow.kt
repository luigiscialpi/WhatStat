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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.whatstat.reborn.data.db.model.DateRange
import com.whatstat.reborn.data.db.model.TimeRange

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
    onCancelClicked: (() -> Unit)? = null
) {
    val timeRangeItems = TimeRange.entries.filter { it != TimeRange.CUSTOM }
    val listState = rememberLazyListState()
    LaunchedEffect(selectedRange) {
        val idx = timeRangeItems.indexOf(selectedRange)
        if (idx >= 0) listState.animateScrollToItem(idx)
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
                    onClick = { onRangeSelected(range) },
                    label = { Text(range.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
        DateRangePicker(
            dateRange = customDateRange,
            onDateRangeChanged = onDateRangeChanged,
            onSubmitClicked = onSubmitClicked,
            isSearching = isSearching,
            onCancelClicked = onCancelClicked,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        )
    }
}
