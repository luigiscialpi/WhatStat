package com.whatstat.reborn.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
        val phoneStyle = SpanStyle(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        Text(
            text = buildAnnotatedString {
                append(name)
                withStyle(phoneStyle) { append(" - $phone") }
            },
            style = nameStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
            color = MaterialTheme.colorScheme.onSurface
        )
    } else {
        Text(
            text = name,
            style = nameStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}