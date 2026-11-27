package com.whatstats.ui.screen.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whatstats.R
import com.whatstats.ui.component.LocalDrawerState
import kotlinx.coroutines.launch

@Composable
@Suppress("FunctionName")
fun DashboardHeader(
    isLiteMode: Boolean,
    onNavigateBack: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val drawerState = LocalDrawerState.current
        val scope = rememberCoroutineScope()
        IconButton(onClick = { scope.launch { drawerState.open() } }) {
            Icon(
                Icons.Default.Menu,
                contentDescription = stringResource(R.string.menu_desc),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.dashboard_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (isLiteMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(
                        stringResource(R.string.lite_mode_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        IconButton(onClick = onNavigateBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back_button_desc),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
