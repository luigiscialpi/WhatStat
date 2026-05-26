package com.whatstats.domain.model

import android.content.Context
import androidx.annotation.StringRes
import com.whatstats.R

sealed class AppError(
    @StringRes val resId: Int,
    val formatArgs: Array<out Any> = emptyArray(),
) {
    object RootNotAvailable : AppError(R.string.error_root_not_available)

    object RootNotGranted : AppError(R.string.error_root_not_granted)

    object KeyFileNotFound : AppError(R.string.error_key_not_found)

    object Crypt14NotFound : AppError(R.string.error_crypt14_not_found)

    object Crypt15Detected : AppError(R.string.error_crypt15_detected)

    data class DecryptionFailed(val reason: String) : AppError(R.string.error_decryption_failed, arrayOf(reason))

    data class DecryptionError(val reason: String) : AppError(R.string.error_decryption_error, arrayOf(reason))

    data class DatabaseError(val reason: String) : AppError(R.string.error_database_open, arrayOf(reason))

    data class ExtractionError(val reason: String) : AppError(R.string.error_extraction, arrayOf(reason))

    data class UnknownError(val throwable: Throwable) : AppError(
        R.string.error_unknown,
        arrayOf(throwable.localizedMessage ?: throwable.javaClass.simpleName),
    )

    fun resolveMessage(context: Context): String =
        if (formatArgs.isEmpty()) context.getString(resId) else context.getString(resId, *formatArgs)
}
