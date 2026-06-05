package com.whatstats.ui.screen.media

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.whatstats.R
import com.whatstats.ui.component.AnimatedInfoButton
import com.whatstats.ui.component.SimpleScreenHeader

@Composable
@Suppress("FunctionName")
fun MediaStatsHeader(
    isChatDetail: Boolean,
    onNavigateBack: () -> Unit,
    chatContextName: String?,
    chatContextAvatarPath: String?,
    appLang: String,
) {
    var showInfoDialog by remember { mutableStateOf(false) }

    SimpleScreenHeader(
        title = stringResource(R.string.media_link_title),
        onBack = if (isChatDetail) onNavigateBack else null,
        subtitle = chatContextName,
        avatarPath = chatContextAvatarPath,
    ) {
        AnimatedInfoButton(
            onClick = { showInfoDialog = true },
            contentDescription = stringResource(R.string.info_dialog_title),
            tint = MaterialTheme.colorScheme.onBackground,
            glowColor = MaterialTheme.colorScheme.primary,
        )
        if (showInfoDialog) {
            androidx.compose.runtime.key(appLang) {
                AlertDialog(
                    onDismissRequest = { showInfoDialog = false },
                    title = { Text(stringResource(R.string.media_info_title)) },
                    text = {
                        Text(stringResource(R.string.media_info_text_global))
                    },
                    confirmButton = {
                        TextButton(onClick = { showInfoDialog = false }) {
                            Text(stringResource(R.string.ho_capito))
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
