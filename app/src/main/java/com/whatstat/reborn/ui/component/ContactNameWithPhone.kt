package com.whatstat.reborn.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ContactNameWithPhone(
    name: String,
    jid: String?,
    isGroup: Boolean = jid?.endsWith("@g.us") == true,
    modifier: Modifier = Modifier,
    nameStyle: TextStyle = MaterialTheme.typography.titleMedium
) {
    val phone = if (!isGroup && jid?.endsWith("@lid") == false) {
        jid?.substringBefore("@")?.takeIf { it.any { c -> c.isDigit() } }
    } else null

    val showPhone = phone != null && phone != name

    if (showPhone) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.Bottom  // allinea alla baseline visiva
        ) {
            Text(
                text = name,
                style = nameStyle,
                maxLines = 1,
                softWrap = false,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alignByBaseline()
            )
            Text(
                text = " - $phone",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                maxLines = 1,
                softWrap = false,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline()
            )
        }
    } else {
        Text(
            text = name,
            style = nameStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}