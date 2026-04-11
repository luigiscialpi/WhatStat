package com.whatstat.reborn.data.repository

import android.content.Context
import com.whatstat.reborn.data.crypto.Crypt14Decryptor
import com.whatstat.reborn.data.db.WhatsAppDatabase
import com.whatstat.reborn.data.root.CryptVersionException
import com.whatstat.reborn.data.root.FileExtractor
import com.whatstat.reborn.data.root.RootManager
import com.whatstat.reborn.data.root.RootStatus
import com.whatstat.reborn.domain.model.AppError
import dagger.hilt.android.qualifiers.ApplicationContext
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository principale che orchestra tutto il flusso:
 * Root check → Estrazione file → Decifratura → Apertura database.
 *
 * Questo è il punto di ingresso unico per il setup dell'app.
 */
@Singleton
class WhatsAppRepository @Inject constructor(
    private val rootManager: RootManager,
    private val fileExtractor: FileExtractor,
    private val decryptor: Crypt14Decryptor,
    private val database: WhatsAppDatabase,
    @ApplicationContext private val context: Context
) {
    /**
     * Esegue il flusso completo di setup.
     * Emette un callback per ogni step completato, permettendo alla UI
     * di mostrare il progressso step-by-step.
     *
     * @param onStepComplete callback invocato quando uno step è completato
     * @return Result.success(Unit) se tutto ok, Result.failure con AppError altrimenti
     */
    suspend fun performSetup(
        onStepComplete: suspend (SetupStep) -> Unit = {},
        onDecryptionProgress: (Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Step 1: Verifica root
            val rootStatus = rootManager.checkRootAccess()
            when (rootStatus) {
                RootStatus.NOT_AVAILABLE -> throw AppErrorException(AppError.RootNotAvailable)
                RootStatus.NOT_GRANTED   -> throw AppErrorException(AppError.RootNotGranted)
                RootStatus.GRANTED       -> { /* ok */ }
            }
            onStepComplete(SetupStep.ROOT_CHECK)

            // Step 2: Estrai file da WhatsApp
            val extractResult = fileExtractor.extractAll()
            val files = extractResult.getOrElse { throwable ->
                when (throwable) {
                    is CryptVersionException -> throw AppErrorException(AppError.Crypt15Detected)
                    else -> throw AppErrorException(
                        AppError.ExtractionError(throwable.message ?: "Errore sconosciuto")
                    )
                }
            }
            onStepComplete(SetupStep.FILE_EXTRACTION)

            // Step 3: Decifra il database
            val outputDb = File(context.filesDir, "msgstore.db")
            val decryptResult = decryptor.decrypt(files.crypt14File, files.keyFile, outputDb, onDecryptionProgress)
            decryptResult.getOrElse { throwable ->
                throw AppErrorException(
                    AppError.DecryptionFailed(throwable.message ?: "Errore sconosciuto")
                )
            }
            onStepComplete(SetupStep.DECRYPTION)

            // Step 4: Apri il database per le query
            try {
                database.open(outputDb, files.waDbFile)
            } catch (e: Exception) {
                throw AppErrorException(
                    AppError.DatabaseError(e.message ?: "Impossibile aprire il database")
                )
            }
            onStepComplete(SetupStep.DATABASE_OPEN)
        }
    }

    /**
     * Esegue il flusso di setup saltando root ed estrazione,
     * usando i file già copiati manualmente dall'utente.
     */
    suspend fun performManualSetup(
        onStepComplete: suspend (SetupStep) -> Unit = {},
        onDecryptionProgress: (Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Saltiamo esplicitamente gli step 1 e 2 per feedback visivo
            onStepComplete(SetupStep.ROOT_CHECK)
            onStepComplete(SetupStep.FILE_EXTRACTION)

            val keyFile = File(context.filesDir, FileExtractor.KEY_DEST)
            val crypt14File = File(context.filesDir, FileExtractor.CRYPT14_DEST)
            val waDbFile = File(context.filesDir, FileExtractor.WA_DB_DEST)
            
            if (!keyFile.exists() || !crypt14File.exists()) {
                 throw AppErrorException(AppError.ExtractionError("I file chiave e database non sono stati copiati correttamente."))
            }

            // Step 3: Decifra il database
            val outputDb = File(context.filesDir, "msgstore.db")
            val decryptResult = decryptor.decrypt(crypt14File, keyFile, outputDb, onDecryptionProgress)
            decryptResult.getOrElse { throwable ->
                throw AppErrorException(
                    AppError.DecryptionFailed(throwable.message ?: "Errore sconosciuto")
                )
            }
            onStepComplete(SetupStep.DECRYPTION)

            // Step 4: Apri il database per le query (waDbFile opzionale)
            try {
                database.open(outputDb, waDbFile)
            } catch (e: Exception) {
                throw AppErrorException(
                    AppError.DatabaseError(e.message ?: "Impossibile aprire il database")
                )
            }
            onStepComplete(SetupStep.DATABASE_OPEN)
        }
    }

    /** Verifica se il database è già aperto e pronto (per skip del setup). */
    fun isDatabaseReady(): Boolean = database.isOpen()

    /** Chiude il database e pulisce i file estratti. */
    suspend fun cleanup() = withContext(Dispatchers.IO) {
        database.close()
        fileExtractor.cleanup()
    }

    /** Proxy per copiare file da Uri senza esporre FileExtractor al ViewModel */
    suspend fun copyFileFromUri(uri: Uri, destFileName: String) {
        fileExtractor.copyFromUri(uri, destFileName)
    }
}

enum class SetupStep {
    ROOT_CHECK,
    FILE_EXTRACTION,
    DECRYPTION,
    DATABASE_OPEN
}

/**
 * Wrapper per lanciare AppError come eccezione nel flusso Result.
 */
class AppErrorException(val appError: AppError) : Exception(appError.userMessage)
