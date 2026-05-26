package com.whatstats.ui.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.whatstats.R

/**
 * Intercetta il tasto back fisico e richiede una doppia pressione entro
 * [EXIT_CONFIRM_WINDOW_MS] ms per chiudere l'app.
 *
 * Alla prima pressione mostra un Toast "Premi di nuovo per uscire".
 * Alla seconda pressione (entro la finestra temporale) termina l'Activity.
 *
 * Usato SOLO sulle schermate tab di primo livello (ChatList, MediaStats,
 * WordSearch, CallsStats) dove il back fisico non ha una destinazione
 * precedente nel navigation stack e causerebbe la chiusura immediata dell'app.
 *
 * NON deve essere usato su schermate di dettaglio che hanno gia' un pulsante
 * back nell'header con [navController.popBackStack()].
 */
@Composable
@Suppress("FunctionName")
fun DoubleBackToExitHandler() {
    val context = LocalContext.current
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    BackHandler {
        val now = System.currentTimeMillis()
        if (now - lastBackPressTime < EXIT_CONFIRM_WINDOW_MS) {
            context.findActivity()?.finish()
        } else {
            lastBackPressTime = now
            Toast.makeText(context, context.getString(R.string.exit_toast), Toast.LENGTH_SHORT).show()
        }
    }
}

/** Finestra temporale per la conferma di uscita (millisecondi). */
private const val EXIT_CONFIRM_WINDOW_MS = 2000L

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
