package com.whatstats.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatstats.R

/**
 * Una pillola fluttuante per indicare operazioni in background (es. calcoli pesanti)
 * in modo non bloccante. Compare animandosi dal basso/alto in base al caso d'uso.
 * Presenta un tasto di chiusura per annullare volontariamente l'azione.
 */
@Composable
@Suppress("FunctionName")
fun DynamicLoadingPill(
    isVisible: Boolean,
    message: String,
    progress: Int? = null,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastNonNullProgress by remember { mutableStateOf<Int?>(null) }

    if (progress != null) {
        lastNonNullProgress = progress
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            lastNonNullProgress = progress
        }
    }
    AnimatedVisibility(
        visible = isVisible,
        enter =
            slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 300),
            ) + fadeIn(),
        exit =
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 300),
            ) + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier =
                    Modifier
                        .animateContentSize()
                        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )

                Spacer(modifier = Modifier.width(12.dp))

                val displayText = if (lastNonNullProgress != null) "$message $lastNonNullProgress%" else message
                Text(
                    text = displayText,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp,
                        ),
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
