package com.whatstats.ui.screen.dashboard

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatstats.R
import com.whatstats.domain.model.GlobalStats
import com.whatstats.ui.component.InteractiveEmojiCloud
import com.whatstats.ui.component.InteractiveStickerCloud
import com.whatstats.ui.component.InteractiveWordCloud
import com.whatstats.ui.component.LimitSelector
import com.whatstats.ui.component.SectionCard

@Composable
@Suppress("FunctionName")
fun DashboardTextAnalysisSection(
    stats: GlobalStats,
    isLiteMode: Boolean,
    selectedLimit: Int,
    onLimitSelected: (Int) -> Unit,
    onEmojiClick: (String, Int) -> Unit,
    onStickerClick: (String, Int) -> Unit,
) {
    val tabs =
        if (isLiteMode) {
            listOf(
                stringResource(R.string.tab_words),
                stringResource(R.string.tab_emojis),
            )
        } else {
            listOf(
                stringResource(R.string.tab_words),
                stringResource(R.string.tab_emojis),
                stringResource(R.string.tab_stickers),
            )
        }
    val selectedTabState = remember { mutableIntStateOf(0) }
    if (selectedTabState.intValue >= tabs.size) selectedTabState.intValue = 0

    SectionCard(
        title = stringResource(R.string.text_analysis_title),
        subtitle = stringResource(R.string.text_analysis_subtitle),
        trailingContent = {
            LimitSelector(
                limits = if (isLiteMode) listOf(10, 20, 30, 50) else listOf(5, 10, 20),
                selectedLimit = selectedLimit,
                onLimitSelected = onLimitSelected,
            )
        },
    ) {
        DashboardTextAnalysisTabs(
            tabs = tabs,
            selectedTab = selectedTabState.intValue,
            isLiteMode = isLiteMode,
            onTabSelected = { selectedTabState.intValue = it },
        )

        when (selectedTabState.intValue) {
            0 -> DashboardWordsContent(stats = stats, selectedLimit = selectedLimit)
            1 ->
                DashboardEmojiContent(
                    stats = stats,
                    selectedLimit = selectedLimit,
                    onEmojiClick = onEmojiClick,
                )
            2 ->
                DashboardStickerContent(
                    stats = stats,
                    selectedLimit = selectedLimit,
                    onStickerClick = onStickerClick,
                    isLiteMode = isLiteMode,
                )
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun DashboardTextAnalysisTabs(
    tabs: List<String>,
    selectedTab: Int,
    isLiteMode: Boolean,
    onTabSelected: (Int) -> Unit,
) {
    val sharedIndicator: @Composable (List<TabPosition>) -> Unit = { tabPositions ->
        if (selectedTab < tabPositions.size) {
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    if (isLiteMode) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {},
            modifier = Modifier.padding(bottom = 12.dp),
            indicator = sharedIndicator,
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    text = { Text(title, fontSize = 12.sp) },
                )
            }
        }
    } else {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 0.dp,
            divider = {},
            modifier = Modifier.padding(bottom = 12.dp),
            indicator = sharedIndicator,
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    text = { Text(title, fontSize = 12.sp) },
                )
            }
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun DashboardWordsContent(
    stats: GlobalStats,
    selectedLimit: Int,
) {
    if (stats.topWords.isEmpty()) {
        EmptyTextAnalysisMessage(text = stringResource(R.string.no_words_found))
    } else {
        InteractiveWordCloud(words = stats.topWords.take(selectedLimit))
    }
}

@Composable
@Suppress("FunctionName")
private fun DashboardEmojiContent(
    stats: GlobalStats,
    selectedLimit: Int,
    onEmojiClick: (String, Int) -> Unit,
) {
    if (stats.topEmojis.isEmpty()) {
        EmptyTextAnalysisMessage(text = stringResource(R.string.no_emojis_found))
    } else {
        InteractiveEmojiCloud(
            emojis = stats.topEmojis.take(selectedLimit),
            onEmojiClick = onEmojiClick,
        )
    }
}

@Composable
@Suppress("FunctionName")
private fun DashboardStickerContent(
    stats: GlobalStats,
    selectedLimit: Int,
    onStickerClick: (String, Int) -> Unit,
    isLiteMode: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.sticker_info_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (stats.topStickers.isEmpty()) {
        EmptyTextAnalysisMessage(text = stringResource(R.string.no_stickers_found))
    } else {
        InteractiveStickerCloud(
            stickers = stats.topStickers.take(selectedLimit),
            onStickerClick = onStickerClick,
            isLiteMode = isLiteMode,
        )
    }
}

@Composable
@Suppress("FunctionName")
private fun EmptyTextAnalysisMessage(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp),
    )
}
