@file:Suppress("FunctionName")

package com.whatstat.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
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
    nameStyle: TextStyle = MaterialTheme.typography.titleMedium,
    enableHorizontalScroll: Boolean = true,
) {
    // Mostra il numero solo per JID WhatsApp reali (@s.whatsapp.net).
    // In Lite mode il JID e' l'importKey (es. "dm:mario_rossi:a3f7c1") che non ha
    // il suffisso @s.whatsapp.net, quindi non viene mai interpretato come numero.
    val phone =
        if (!isGroup && jid?.endsWith("@s.whatsapp.net") == true) {
            jid.substringBefore("@").takeIf { it.any { c -> c.isDigit() } }
        } else {
            null
        }

    val showPhone = phone != null && phone != name
    val scrollState = rememberScrollState()

    /* LaunchedEffect(Unit) {
        if (enableHorizontalScroll) {
            delay(3000)
            scrollState.animateScrollTo(scrollState.maxValue)
            delay(1500)
            scrollState.animateScrollTo(0)
        }
    } */

    if (enableHorizontalScroll) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
        ) {
            if (showPhone) {
                val phoneStyle =
                    SpanStyle(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                Text(
                    text =
                        buildAnnotatedString {
                            append(name)
                            withStyle(phoneStyle) { append(" - $phone") }
                        },
                    style = nameStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(
                    text = name,
                    style = nameStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    } else {
        if (showPhone) {
            val phoneStyle =
                SpanStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            Text(
                text =
                    buildAnnotatedString {
                        append(name)
                        withStyle(phoneStyle) { append(" - $phone") }
                    },
                style = nameStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(
                text = name,
                style = nameStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
