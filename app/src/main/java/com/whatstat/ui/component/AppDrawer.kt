package com.whatstat.ui.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.whatstat.MainActivity
import com.whatstat.R
import com.whatstat.data.store.ThemeMode
import com.whatstat.ui.MainViewModel
import kotlinx.coroutines.launch

val LocalDrawerState = compositionLocalOf<DrawerState> { error("No DrawerState provided") }

@Composable
@Suppress("FunctionName")
fun AppDrawerContent(
    isOnSetupScreen: Boolean = false,
    onNavigateToSetup: () -> Unit = {},
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val themeMode by mainViewModel.themeMode.collectAsState()
    val appLanguage by mainViewModel.appLanguage.collectAsState()
    val isLiteMode by mainViewModel.isLiteMode.collectAsState()
    val proBillingState by mainViewModel.proBillingUiState.collectAsState()
    val context = LocalContext.current

    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showProDialog by remember { mutableStateOf(false) }

    val drawerState = LocalDrawerState.current
    val coroutineScope = rememberCoroutineScope()

    fun closeDrawer() {
        coroutineScope.launch { drawerState.close() }
    }

    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.drawer_app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 16.dp),
                )

                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

                DrawerItem(
                    icon = Icons.Outlined.BrightnessMedium,
                    label = stringResource(R.string.whatstat_drawer_theme),
                    onClick = {
                        showThemeDialog = true
                        closeDrawer()
                    },
                )

                DrawerItem(
                    icon = Icons.Outlined.Language,
                    label = stringResource(R.string.whatstat_drawer_language),
                    onClick = {
                        showLanguageDialog = true
                        closeDrawer()
                    },
                )

                DrawerItem(
                    icon = Icons.Outlined.Cached,
                    label = stringResource(R.string.whatstat_drawer_cache),
                    onClick = {
                        showCacheDialog = true
                        closeDrawer()
                    },
                )

                DrawerItem(
                    icon = Icons.Outlined.Star,
                    label = if (proBillingState.isPro) stringResource(R.string.pro_active) else stringResource(R.string.drawer_upgrade_pro),
                    onClick = {
                        showProDialog = true
                        closeDrawer()
                    },
                )
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                DrawerItem(
                    icon = Icons.Outlined.Info,
                    label = stringResource(R.string.whatstat_drawer_about),
                    onClick = {
                        showAboutDialog = true
                        closeDrawer()
                    },
                )
            }
        }
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentMode = themeMode,
            onModeSelected = {
                mainViewModel.setThemeMode(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLang = appLanguage,
            onLangSelected = { lang ->
                context
                    .getSharedPreferences(MainActivity.LANGUAGE_PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(MainActivity.LANGUAGE_PREFS_KEY, lang)
                    .commit()
                mainViewModel.setAppLanguage(lang)
                showLanguageDialog = false
                Handler(Looper.getMainLooper()).post {
                    context.findActivity()?.recreate()
                }
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    if (showCacheDialog) {
        key(appLanguage) {
            val clearMessageRes =
                when {
                    isOnSetupScreen -> R.string.dialog_clear_data_message_both
                    !isLiteMode -> R.string.dialog_clear_data_message_root
                    else -> R.string.dialog_clear_data_message_lite
                }
            AlertDialog(
                onDismissRequest = { showCacheDialog = false },
                title = { Text(stringResource(R.string.dialog_clear_cache_title)) },
                text = {
                    Text(
                        stringResource(clearMessageRes),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        mainViewModel.fullReset(
                            clearRoot = isOnSetupScreen || !isLiteMode,
                            clearLite = isOnSetupScreen || isLiteMode,
                        )
                        showCacheDialog = false
                        if (!isOnSetupScreen) {
                            onNavigateToSetup()
                        }
                    }) {
                        Text(stringResource(R.string.dialog_clear))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCacheDialog = false }) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                },
            )
        }
    }

    if (showAboutDialog) {
        key(appLanguage) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                title = { Text(stringResource(R.string.dialog_about_title)) },
                text = {
                    Text(
                        stringResource(R.string.dialog_about_message),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showAboutDialog = false }) {
                        Text(stringResource(R.string.dialog_close))
                    }
                },
            )
        }
    }

    if (showProDialog) {
        key(appLangSafeKey(appLanguage, proBillingState.isPro, proBillingState.productPrice, proBillingState.message)) {
            ProUpgradeDialog(
                isPro = proBillingState.isPro,
                isProcessing = proBillingState.isProcessing,
                productPrice = proBillingState.productPrice,
                message = proBillingState.message,
                onDismiss = {
                    mainViewModel.dismissProBillingMessage()
                    showProDialog = false
                },
                onPurchase = {
                    val activity = context.findActivity()
                    if (activity != null) {
                        mainViewModel.purchasePro(activity)
                    }
                },
                onRestore = { mainViewModel.restoreProPurchases() },
            )
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
@Suppress("FunctionName")
private fun ThemeSelectionDialog(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val mainVm: MainViewModel = hiltViewModel()
    val appLang by mainVm.appLanguage.collectAsState()
    key(appLang) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.dialog_choose_theme)) },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        val text =
                            when (mode) {
                                ThemeMode.SYSTEM -> stringResource(R.string.theme_system_default)
                                ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                ThemeMode.DARK -> stringResource(R.string.theme_dark)
                            }
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onModeSelected(mode) }
                                    .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = mode == currentMode,
                                onClick = { onModeSelected(mode) },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

@Composable
@Suppress("FunctionName")
private fun LanguageSelectionDialog(
    currentLang: String,
    onLangSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val mainVm: MainViewModel = hiltViewModel()
    val appLang by mainVm.appLanguage.collectAsState()
    key(appLang) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.dialog_choose_language)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    LanguageEntry("system", "System default", currentLang, onLangSelected)
                    LanguageEntry("en", "English", currentLang, onLangSelected)
                    LanguageEntry("it", "Italiano", currentLang, onLangSelected)
                    LanguageEntry("es", "Español", currentLang, onLangSelected)
                    LanguageEntry("fr", "Français", currentLang, onLangSelected)
                    LanguageEntry("de", "Deutsch", currentLang, onLangSelected)
                    LanguageEntry("pt_BR", "Português (BR)", currentLang, onLangSelected)
                    LanguageEntry("tr", "Türkçe", currentLang, onLangSelected)
                    LanguageEntry("hi", "हिन्दी", currentLang, onLangSelected)
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun LanguageEntry(
    code: String,
    label: String,
    currentLang: String,
    onLangSelected: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onLangSelected(code) }
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = code == currentLang,
            onClick = { onLangSelected(code) },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
@Suppress("FunctionName")
private fun ProUpgradeDialog(
    isPro: Boolean,
    isProcessing: Boolean,
    productPrice: String?,
    message: String?,
    onDismiss: () -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
) {
    val context = LocalContext.current
    val activityAvailable = context.findActivity() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isPro) {
                    stringResource(R.string.pro_active)
                } else {
                    stringResource(R.string.drawer_upgrade_pro)
                },
            )
        },
        text = {
            Column {
                Text(
                    text =
                        if (isPro) {
                            stringResource(R.string.pro_active_description)
                        } else {
                            stringResource(R.string.pro_upgrade_description)
                        },
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (!isPro) {
                    Text(
                        text =
                            if (productPrice != null) {
                                stringResource(R.string.pro_price_label, productPrice)
                            } else {
                                stringResource(R.string.pro_price_loading)
                            },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                if (message != null) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (!isPro) {
                TextButton(
                    enabled = !isProcessing && activityAvailable,
                    onClick = onPurchase,
                ) {
                    Text(
                        if (isProcessing) {
                            stringResource(R.string.processing_label)
                        } else {
                            stringResource(R.string.pro_buy_button)
                        },
                    )
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_close))
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    enabled = !isProcessing,
                    onClick = onRestore,
                ) {
                    Text(stringResource(R.string.pro_restore_button))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        },
    )
}

private fun appLangSafeKey(
    appLanguage: String,
    isPro: Boolean,
    productPrice: String?,
    message: String?,
): String {
    return "$appLanguage|$isPro|${productPrice ?: "-"}|${message ?: "-"}"
}
