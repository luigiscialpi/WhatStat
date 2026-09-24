@file:Suppress("FunctionName")

package com.whatstats.ui.screen.badges

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.whatstats.R
import com.whatstats.domain.engagement.BadgeEngine
import com.whatstats.domain.model.BadgeId
import com.whatstats.domain.model.BadgeProgress
import com.whatstats.ui.component.LoadingSection
import com.whatstats.ui.component.SimpleScreenHeader

@Composable
fun BadgesScreen(
    onNavigateBack: () -> Unit,
    viewModel: BadgesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    Column(Modifier.fillMaxSize()) {
        SimpleScreenHeader(title = stringResource(R.string.badges_title), onBack = onNavigateBack)
        when (val current = state) {
            BadgesViewModel.UiState.Loading -> LoadingSection()
            is BadgesViewModel.UiState.Error -> BadgeMessage(current.message ?: "", MaterialTheme.colorScheme.error)
            is BadgesViewModel.UiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(current.badges, key = { it.id.key }) { badge ->
                        BadgeRow(badge, badge.id.key in current.newlyUnlocked)
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeRow(
    badge: BadgeProgress,
    isNew: Boolean,
) {
    val text = badgeText(badge.id)
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (badge.unlocked) text.icon else Icons.Outlined.Lock,
                null,
                tint = if (badge.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (isNew) {
                        Spacer(Modifier.width(8.dp))
                        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                stringResource(R.string.badge_new_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(text.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (badge.unlocked) {
                    Text(
                        stringResource(R.string.badge_unlocked_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(progress = { badge.progress }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.engagement_percent_value, (badge.progress * 100).toInt()),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

private class BadgeText(val icon: ImageVector, val title: String, val description: String)

@Composable
private fun badgeText(id: BadgeId): BadgeText =
    when (id) {
        BadgeId.NIGHT_OWL ->
            BadgeText(
                Icons.Outlined.Bedtime,
                stringResource(R.string.badge_night_owl_title),
                stringResource(R.string.badge_night_owl_desc, id.target, BadgeEngine.MIN_MY_MESSAGES_FOR_TIME_SHARE),
            )
        BadgeId.EARLY_BIRD ->
            BadgeText(
                Icons.Outlined.WbSunny,
                stringResource(R.string.badge_early_bird_title),
                stringResource(R.string.badge_early_bird_desc, id.target, BadgeEngine.MIN_MY_MESSAGES_FOR_TIME_SHARE),
            )
        BadgeId.CHATTERBOX_1K, BadgeId.CHATTERBOX_10K, BadgeId.CHATTERBOX_50K, BadgeId.CHATTERBOX_100K ->
            BadgeText(
                Icons.Outlined.Forum,
                stringResource(R.string.badge_chatterbox_title),
                stringResource(R.string.badge_chatterbox_desc, "%,d".format(id.target)),
            )
        BadgeId.MARATHON_30, BadgeId.MARATHON_100, BadgeId.MARATHON_365 ->
            BadgeText(
                Icons.Outlined.LocalFireDepartment,
                stringResource(R.string.badge_marathon_title),
                stringResource(R.string.badge_marathon_desc, id.target),
            )
    }

@Composable
private fun BadgeMessage(
    text: String,
    color: Color,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color, textAlign = TextAlign.Center)
    }
}
