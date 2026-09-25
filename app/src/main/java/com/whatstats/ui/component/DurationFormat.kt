package com.whatstats.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.whatstats.R
import java.util.concurrent.TimeUnit

@Composable
fun formatReplyDuration(ms: Long): String {
    if (ms <= 0) return "-"
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return when {
        hours > 0 -> "${hours}${stringResource(R.string.unit_hours)} ${minutes}${stringResource(R.string.unit_minutes)}"
        minutes > 0 -> "$minutes ${stringResource(R.string.unit_min_full)}"
        else -> stringResource(R.string.less_than_one_min)
    }
}
