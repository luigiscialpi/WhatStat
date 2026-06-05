package com.whatstats.ui.screen.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.whatstats.R
import com.whatstats.domain.model.GlobalStats
import com.whatstats.ui.component.StatCard

@Composable
@Suppress("FunctionName")
fun DashboardStatsGrid(
    stats: GlobalStats,
    onNavigateToMedia: () -> Unit,
    onNavigateToChatDetail: (Long) -> Unit,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically { it / 2 },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.stat_total_messages),
                    value = "%,d".format(stats.totalMessages),
                    icon = Icons.AutoMirrored.Outlined.Chat,
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.stat_avg_per_day),
                    value = "%.1f".format(stats.averageMessagesPerDay),
                    icon = Icons.AutoMirrored.Outlined.TrendingUp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.stat_total_media),
                    value = "%,d".format(stats.totalMediaFiles),
                    icon = Icons.Outlined.PhotoLibrary,
                    isSmallText = true,
                    onClick = onNavigateToMedia,
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.stat_most_active_chat),
                    value = stats.mostActiveChat,
                    icon = Icons.AutoMirrored.Outlined.TrendingUp,
                    isSmallText = true,
                    onClick = stats.mostActiveChatId?.let { id -> { onNavigateToChatDetail(id) } },
                )
            }
        }
    }
}
