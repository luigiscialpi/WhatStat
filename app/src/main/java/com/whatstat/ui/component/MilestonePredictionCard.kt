@file:Suppress("FunctionName")

package com.whatstat.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.whatstat.R
import com.whatstat.domain.model.MilestonePrediction
import com.whatstat.domain.model.MilestonePredictionState
import com.whatstat.domain.model.PredictionConfidence
import com.whatstat.domain.prediction.MilestonePredictionCalculator
import com.whatstat.ui.MainViewModel
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date

private sealed interface PredictionSelection {
    data class Suggested(val index: Int) : PredictionSelection

    data object Custom : PredictionSelection
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestonePredictionCard(
    state: MilestonePredictionState,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.prediction_title),
    periodLabel: String? = null,
) {
    val mainVm: MainViewModel = hiltViewModel()
    val appLang by mainVm.appLanguage.collectAsState()
    val numberFormat = remember { NumberFormat.getNumberInstance() }

    var selectedMode by remember(state.currentCount) {
        mutableStateOf<PredictionSelection>(
            if (state.suggestedPredictions.isNotEmpty()) {
                PredictionSelection.Suggested(0)
            } else {
                PredictionSelection.Custom
            },
        )
    }

    var customTargetText by rememberSaveable(state.currentCount) { mutableStateOf("") }

    val currentPrediction =
        when (val mode = selectedMode) {
            is PredictionSelection.Suggested -> state.suggestedPredictions.getOrNull(mode.index)
            is PredictionSelection.Custom -> {
                val target = customTargetText.toLongOrNull() ?: 0L
                MilestonePredictionCalculator.calculateForTarget(
                    currentCount = state.currentCount,
                    targetCount = target,
                    dailyRate = state.dailyRate,
                    confidence = state.confidence,
                )
            }
        }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (periodLabel != null) {
                        Text(
                            text = periodLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }

                var showInfo by remember { mutableStateOf(false) }
                AnimatedInfoButton(
                    onClick = { showInfo = true },
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.primary,
                    glowColor = MaterialTheme.colorScheme.primary,
                )

                if (showInfo) {
                    key(appLang) {
                        AlertDialog(
                            onDismissRequest = { showInfo = false },
                            title = { Text(stringResource(R.string.prediction_info_title)) },
                            text = { Text(stringResource(R.string.prediction_info_desc)) },
                            confirmButton = {
                                TextButton(onClick = { showInfo = false }) {
                                    Text(stringResource(android.R.string.ok))
                                }
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chip per selezione target
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.suggestedPredictions.forEachIndexed { index, prediction ->
                    val label = formatShortCount(prediction.targetCount)
                    FilterChip(
                        selected = selectedMode == PredictionSelection.Suggested(index),
                        onClick = { selectedMode = PredictionSelection.Suggested(index) },
                        label = { Text(label) },
                    )
                }
                FilterChip(
                    selected = selectedMode == PredictionSelection.Custom,
                    onClick = { selectedMode = PredictionSelection.Custom },
                    label = { Text(stringResource(R.string.prediction_custom)) },
                )
            }

            // Input per target personalizzato
            AnimatedVisibility(visible = selectedMode == PredictionSelection.Custom) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customTargetText,
                        onValueChange = { input ->
                            val sanitized = input.filter { it.isDigit() }.take(9)
                            customTargetText = sanitized
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.prediction_target_label)) },
                        placeholder = { Text(stringResource(R.string.prediction_target_placeholder)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )

                    val customVal = customTargetText.toLongOrNull() ?: 0L
                    if (customTargetText.isNotEmpty() && customVal <= state.currentCount) {
                        Text(
                            text = stringResource(R.string.prediction_error_already_reached),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (currentPrediction != null) {
                PredictionDetails(
                    prediction = currentPrediction,
                    numberFormat = numberFormat,
                    confidence = state.confidence,
                )
            } else {
                Text(
                    text = stringResource(R.string.prediction_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PredictionDetails(
    prediction: MilestonePrediction,
    numberFormat: NumberFormat,
    confidence: PredictionConfidence,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = stringResource(R.string.prediction_messages_format, numberFormat.format(prediction.targetCount)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )

            val progressPercent = (prediction.progress * 100).toInt()
            Text(
                text = "$progressPercent%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val animatedProgress by animateFloatAsState(
            targetValue = prediction.progress,
            label = "prediction_progress",
        )

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(prediction.progress, 0f..1f)
                    },
            strokeCap = StrokeCap.Round,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Data stimata e giorni rimanenti
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.prediction_estimated_date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                val dateText =
                    when {
                        prediction.targetCount <= prediction.dailyRate.toLong() && prediction.progress >= 1f ->
                            stringResource(R.string.prediction_reached)
                        prediction.estimatedDate != null ->
                            formatMonthYear(Date(prediction.estimatedDate))
                        prediction.daysRemaining != null ->
                            stringResource(R.string.prediction_date_unavailable)
                        else -> stringResource(R.string.prediction_uncalculable)
                    }
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color =
                        if (prediction.estimatedDate != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
            }

            if (prediction.daysRemaining != null && prediction.daysRemaining > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                ) {
                    Text(
                        text = formatDaysRemaining(prediction.daysRemaining),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Footer con ritmo e confidenza
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.prediction_daily_rate_format, prediction.dailyRate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )

            if (confidence == PredictionConfidence.LOW) {
                Text(
                    text = stringResource(R.string.prediction_low_confidence),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private fun formatShortCount(count: Long): String {
    return when {
        count >= 1_000_000 -> "${count / 1_000_000}M"
        count >= 1_000 -> "${count / 1_000}k"
        else -> count.toString()
    }
}

@Composable
private fun formatDaysRemaining(days: Long): String {
    return when {
        days < 30 -> stringResource(R.string.days_remaining_format, days)
        days < 365 -> {
            val months = days / 30
            if (months == 1L) stringResource(R.string.month_remaining_format) else stringResource(R.string.months_remaining_format, months)
        }
        else -> {
            val years = days / 365
            if (years == 1L) stringResource(R.string.year_remaining_format) else stringResource(R.string.years_remaining_format, years)
        }
    }
}

@Composable
private fun formatMonthYear(date: Date): String {
    val cal = Calendar.getInstance().apply { time = date }
    val month = cal.get(Calendar.MONTH)
    val year = cal.get(Calendar.YEAR)
    val monthName =
        when (month) {
            Calendar.JANUARY -> stringResource(R.string.month_january)
            Calendar.FEBRUARY -> stringResource(R.string.month_february)
            Calendar.MARCH -> stringResource(R.string.month_march)
            Calendar.APRIL -> stringResource(R.string.month_april)
            Calendar.MAY -> stringResource(R.string.month_may_full)
            Calendar.JUNE -> stringResource(R.string.month_june)
            Calendar.JULY -> stringResource(R.string.month_july)
            Calendar.AUGUST -> stringResource(R.string.month_august)
            Calendar.SEPTEMBER -> stringResource(R.string.month_september)
            Calendar.OCTOBER -> stringResource(R.string.month_october)
            Calendar.NOVEMBER -> stringResource(R.string.month_november)
            Calendar.DECEMBER -> stringResource(R.string.month_december)
            else -> ""
        }
    return "$monthName $year"
}
