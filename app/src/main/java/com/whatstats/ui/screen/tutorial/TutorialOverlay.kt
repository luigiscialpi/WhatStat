package com.whatstats.ui.screen.tutorial

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.whatstats.R
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 7

@Composable
fun TutorialOverlay(onComplete: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onComplete) {
                        Text(stringResource(R.string.tutorial_skip))
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                ) { page ->
                    when (page) {
                        0 -> WelcomeTutorialPage()
                        1 -> ModesTutorialPage()
                        2 -> ImportTutorialPage()
                        3 -> DashboardTutorialPage()
                        4 -> RadarTutorialPage()
                        5 -> MediaTutorialPage()
                        6 -> ReadyTutorialPage()
                        else -> WelcomeTutorialPage()
                    }
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        repeat(PAGE_COUNT) { index ->
                            val isSelected = pagerState.currentPage == index
                            Box(
                                modifier =
                                    Modifier
                                        .height(8.dp)
                                        .width(if (isSelected) 20.dp else 8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) colorScheme.primary else colorScheme.outlineVariant,
                                        ),
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (pagerState.currentPage == PAGE_COUNT - 1) {
                                onComplete()
                            } else {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                    ) {
                        Text(
                            text =
                                if (pagerState.currentPage == PAGE_COUNT - 1) {
                                    stringResource(R.string.tutorial_start)
                                } else {
                                    stringResource(R.string.tutorial_next)
                                },
                        )
                    }
                }
            }
        }
    }
}

// ── Pagina 0: Benvenuto ───────────────────────────────────────────────────────
@Composable
private fun WelcomeTutorialPage() {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(colorScheme.primaryContainer, colorScheme.surface),
                    ),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = stringResource(R.string.tutorial_title_welcome),
                    tint = colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                )
            }
            Text(
                text = "WhatStat",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.tutorial_desc_welcome),
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Pagina 1: Due Modalità ────────────────────────────────────────────────────
@Composable
private fun ModesTutorialPage() {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(colorScheme.secondaryContainer.copy(alpha = 0.5f), colorScheme.surface),
                    ),
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text(
                text = stringResource(R.string.tutorial_title_modes),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ModeCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.AdminPanelSettings,
                    title = stringResource(R.string.tab_root),
                    subtitle = stringResource(R.string.tutorial_modes_root_sub),
                    iconTint = colorScheme.primary,
                    containerColor = colorScheme.primaryContainer,
                    onContainerColor = colorScheme.onPrimaryContainer,
                )
                ModeCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.FolderZip,
                    title = stringResource(R.string.tab_lite),
                    subtitle = stringResource(R.string.tutorial_modes_lite_sub),
                    iconTint = colorScheme.secondary,
                    containerColor = colorScheme.secondaryContainer,
                    onContainerColor = colorScheme.onSecondaryContainer,
                )
            }
            Text(
                text = stringResource(R.string.tutorial_desc_modes),
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ModeCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: androidx.compose.ui.graphics.Color,
    containerColor: androidx.compose.ui.graphics.Color,
    onContainerColor: androidx.compose.ui.graphics.Color,
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(6.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = onContainerColor,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = onContainerColor.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Pagina 2: Come importare ──────────────────────────────────────────────────
@Composable
private fun ImportTutorialPage() {
    val colorScheme = MaterialTheme.colorScheme
    val moveY =
        rememberInfiniteTransition(label = "stepArrow")
            .animateFloat(
                initialValue = 0f,
                targetValue = 6f,
                animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "arrowY",
            )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(colorScheme.secondary.copy(alpha = 0.12f), colorScheme.surface),
                    ),
                ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.tutorial_title_import),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Chat,
                    contentDescription = stringResource(R.string.tutorial_title_import),
                    tint = colorScheme.secondary,
                    modifier = Modifier.size(40.dp),
                )
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = colorScheme.surfaceContainer),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NumberedStep(number = 1, text = stringResource(R.string.tutorial_import_step_1))
                    StepArrow(moveY.value)
                    NumberedStep(number = 2, text = stringResource(R.string.tutorial_import_step_2))
                    StepArrow(moveY.value)
                    NumberedStep(number = 3, text = stringResource(R.string.tutorial_import_step_3))
                    StepArrow(moveY.value)
                    NumberedStep(number = 4, text = stringResource(R.string.tutorial_import_step_4))
                }
            }
        }
    }
}

@Composable
private fun NumberedStep(
    number: Int,
    text: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape).background(colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StepArrow(offsetY: Float) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowDown,
            contentDescription = null,
            tint = colorScheme.secondary,
            modifier = Modifier.size(16.dp).padding(top = offsetY.dp),
        )
    }
}

// ── Pagina 3: Dashboard ───────────────────────────────────────────────────────
@Composable
private fun DashboardTutorialPage() {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(colorScheme.primary.copy(alpha = 0.1f), colorScheme.surface),
                    ),
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = colorScheme.surfaceContainerHigh),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FakeStatCard(
                            modifier = Modifier.weight(1f),
                            value = "12.450",
                            label = stringResource(R.string.stat_total_messages),
                            tint = colorScheme.primary,
                        )
                        FakeStatCard(
                            modifier = Modifier.weight(1f),
                            value = "42",
                            label = stringResource(R.string.stat_avg_per_day),
                            tint = colorScheme.secondary,
                        )
                        FakeStatCard(
                            modifier = Modifier.weight(1f),
                            value = "8 gg",
                            label = stringResource(R.string.stat_longest_streak),
                            tint = colorScheme.tertiary,
                        )
                    }
                    val barHeights = listOf(0.45f, 0.80f, 0.55f, 1.0f, 0.70f, 0.90f, 0.35f)
                    val barColors =
                        listOf(
                            colorScheme.primary,
                            colorScheme.secondary,
                            colorScheme.primary,
                            colorScheme.tertiary,
                            colorScheme.primary,
                            colorScheme.secondary,
                            colorScheme.primary,
                        )
                    val dayLabels = listOf("L", "M", "M", "G", "V", "S", "D")
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(72.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            barHeights.forEachIndexed { i, h ->
                                Box(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .fillMaxHeight(h)
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(barColors[i]),
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            dayLabels.forEach { day ->
                                Text(
                                    text = day,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.tutorial_title_dashboard),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.tutorial_desc_dashboard),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun FakeStatCard(
    modifier: Modifier,
    value: String,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
) {
    val colorScheme = MaterialTheme.colorScheme
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = colorScheme.surface),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Pagina 4: Radar Parole ────────────────────────────────────────────────────
@Composable
private fun RadarTutorialPage() {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(colorScheme.tertiary.copy(alpha = 0.12f), colorScheme.surface),
                    ),
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text(
                text = stringResource(R.string.tutorial_title_radar),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = colorScheme.surfaceContainer),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // OutlinedTextField mock — uguale alla schermata reale
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colorScheme.surface)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(R.string.tutorial_radar_search_example),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.tertiary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    // Riga FilterChip: Aa / ab / ? / Multi
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf("Aa", "ab", "?", stringResource(R.string.filter_multi)).forEach { label ->
                            Box(
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(50.dp))
                                        .background(colorScheme.secondaryContainer)
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    // Occorrenze per mittente — barre orizzontali
                    Text(
                        text = stringResource(R.string.occurrences_by_sender),
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                    SenderOccurrenceBar(
                        name = "Marco",
                        fraction = 0.82f,
                        count = "142",
                        tint = colorScheme.primary,
                    )
                    SenderOccurrenceBar(
                        name = stringResource(R.string.fallback_me),
                        fraction = 0.55f,
                        count = "89",
                        tint = colorScheme.tertiary,
                    )
                    SenderOccurrenceBar(
                        name = "Sara",
                        fraction = 0.30f,
                        count = "48",
                        tint = colorScheme.secondary,
                    )

                    // Anteprima messaggio con parola evidenziata
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        val highlight = colorScheme.tertiary
                        val highlightBg = colorScheme.tertiaryContainer
                        val word = stringResource(R.string.tutorial_radar_keyword)
                        val before = stringResource(R.string.tutorial_radar_preview_before)
                        val after = stringResource(R.string.tutorial_radar_preview_after)
                        Text(
                            text =
                                buildAnnotatedString {
                                    append(before)
                                    append(" ")
                                    withStyle(
                                        SpanStyle(
                                            fontWeight = FontWeight.Bold,
                                            color = highlight,
                                            background = highlightBg,
                                        ),
                                    ) { append(word) }
                                    append(" ")
                                    append(after)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurface,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.tutorial_desc_radar),
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SenderOccurrenceBar(
    name: String,
    fraction: Float,
    count: String,
    tint: androidx.compose.ui.graphics.Color,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurface,
            modifier = Modifier.width(48.dp),
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colorScheme.surfaceContainerHighest),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(tint),
            )
        }
        Text(
            text = count,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.width(30.dp),
        )
    }
}

// ── Pagina 5: Galleria Media ──────────────────────────────────────────────────
@Composable
private fun MediaTutorialPage() {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(colorScheme.tertiary.copy(alpha = 0.12f), colorScheme.surface),
                    ),
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = colorScheme.surfaceContainerHigh),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FakeTab(
                            label = stringResource(R.string.tab_media),
                            selected = true,
                        )
                        FakeTab(
                            label = stringResource(R.string.tab_links),
                            selected = false,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MediaCountChip(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.Image,
                            count = "148",
                            tint = colorScheme.primary,
                        )
                        MediaCountChip(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.VideoLibrary,
                            count = "32",
                            tint = colorScheme.secondary,
                        )
                        MediaCountChip(
                            modifier = Modifier.weight(1f),
                            icon = Icons.AutoMirrored.Outlined.VolumeUp,
                            count = "17",
                            tint = colorScheme.tertiary,
                        )
                        MediaCountChip(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Outlined.Link,
                            count = "64",
                            tint = colorScheme.secondary,
                        )
                    }
                    val thumbColors =
                        listOf(
                            colorScheme.primaryContainer,
                            colorScheme.secondaryContainer,
                            colorScheme.tertiaryContainer,
                            colorScheme.secondaryContainer,
                            colorScheme.primaryContainer,
                            colorScheme.tertiaryContainer,
                        )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (row in 0..1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                for (col in 0..2) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(thumbColors[row * 3 + col]),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.tutorial_title_media),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.tutorial_desc_media),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun FakeTab(
    label: String,
    selected: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) colorScheme.primary else colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MediaCountChip(
    modifier: Modifier,
    icon: ImageVector,
    count: String,
    tint: androidx.compose.ui.graphics.Color,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = count,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
        )
    }
}

// ── Pagina 6: Pronto ──────────────────────────────────────────────────────────
@Composable
private fun ReadyTutorialPage() {
    val colorScheme = MaterialTheme.colorScheme
    val scale =
        rememberInfiniteTransition(label = "checkPulse")
            .animateFloat(
                initialValue = 1f,
                targetValue = 1.12f,
                animationSpec =
                    infiniteRepeatable(
                        tween(1000, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse,
                    ),
                label = "checkScale",
            )
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(colorScheme.primaryContainer, colorScheme.surface),
                    ),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = stringResource(R.string.tutorial_title_ready),
                tint = colorScheme.primary,
                modifier =
                    Modifier
                        .size(96.dp)
                        .scale(scale.value),
            )
            Text(
                text = stringResource(R.string.tutorial_title_ready),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.tutorial_desc_ready),
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
