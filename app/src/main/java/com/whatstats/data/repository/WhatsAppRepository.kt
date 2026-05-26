package com.whatstats.data.repository

import android.content.Context
import android.net.Uri
import com.whatstats.R
import com.whatstats.data.cache.CacheManager
import com.whatstats.data.crypto.Crypt14Decryptor
import com.whatstats.data.db.WhatsAppDatabase
import com.whatstats.data.root.CryptVersionException
import com.whatstats.data.root.FileExtractor
import com.whatstats.data.root.RootManager
import com.whatstats.data.root.RootStatus
import com.whatstats.domain.model.AppError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
class WhatsAppRepository
    @Inject
    constructor(
        private val rootManager: RootManager,
        private val fileExtractor: FileExtractor,
        private val decryptor: Crypt14Decryptor,
        private val database: WhatsAppDatabase,
        private val cacheManager: CacheManager,
        @ApplicationContext private val context: Context,
    ) {
        private val _setupState = MutableStateFlow(RepositorySetupState())
        val setupState: StateFlow<RepositorySetupState> = _setupState.asStateFlow()

        @Volatile
        private var isCancelled = false

        fun resetState() {
            isCancelled = false
            _setupState.update { RepositorySetupState() }
        }

        fun cancelOperation() {
            isCancelled = true
            _setupState.update {
                it.copy(
                    isLoading = false,
                    error = context.getString(R.string.setup_cancelled),
                    rootStatus = if (it.rootStatus == GlobalStepStatus.LOADING) GlobalStepStatus.IDLE else it.rootStatus,
                    extractionStatus = if (it.extractionStatus == GlobalStepStatus.LOADING) GlobalStepStatus.IDLE else it.extractionStatus,
                    decryptionStatus = if (it.decryptionStatus == GlobalStepStatus.LOADING) GlobalStepStatus.IDLE else it.decryptionStatus,
                    contactsStatus = if (it.contactsStatus == GlobalStepStatus.LOADING) GlobalStepStatus.IDLE else it.contactsStatus,
                )
            }
        }

        fun isOperationCancelled(): Boolean = isCancelled

        /**
         * Esegue il flusso completo di setup.
         * Emette un callback per ogni step completato, permettendo alla UI
         * di mostrare il progressso step-by-step.
         *
         * @param onStepComplete callback invocato quando uno step è completato
         * @return Result.success(Unit) se tutto ok, Result.failure con AppError altrimenti
         */
        suspend fun performSetup(): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    _setupState.update { it.copy(isLoading = true, error = null) }

                    // Step 1: Verifica root
                    _setupState.update { it.copy(rootStatus = GlobalStepStatus.LOADING) }
                    val rootStatus = rootManager.checkRootAccess()
                    when (rootStatus) {
                        RootStatus.NOT_AVAILABLE -> throw AppErrorException(AppError.RootNotAvailable)
                        RootStatus.NOT_GRANTED -> throw AppErrorException(AppError.RootNotGranted)
                        RootStatus.GRANTED -> { /* ok */ }
                    }
                    _setupState.update { it.copy(rootStatus = GlobalStepStatus.SUCCESS, extractionStatus = GlobalStepStatus.LOADING) }

                    // Step 2: Tentativo Live DB (copia diretta senza decifratura)
                    val liveResult = fileExtractor.extractLiveDb()
                    val outputDb: File
                    val waDbFile: File

                    if (liveResult.isSuccess) {
                        // Flusso Live: skip decifratura, DB gia' in chiaro con WAL integrato
                        android.util.Log.d("WhatStat", "Live DB extraction succeeded")
                        val liveFiles = liveResult.getOrThrow()
                        outputDb = liveFiles.msgstoreDb
                        waDbFile = liveFiles.waDbFile
                        _setupState.update {
                            it.copy(
                                extractionStatus = GlobalStepStatus.SUCCESS,
                                // Decifratura saltata: segnala SUCCESS senza passare per LOADING
                                decryptionStatus = GlobalStepStatus.SUCCESS,
                                contactsStatus = GlobalStepStatus.LOADING,
                            )
                        }
                    } else {
                        // Fallback: vecchio flusso crypt14 + key + decifrazione
                        android.util.Log.w(
                            "WhatStat",
                            "Live DB failed, fallback to crypt14: ${liveResult.exceptionOrNull()?.message}",
                        )
                        val extractResult = fileExtractor.extractAll()
                        val files =
                            extractResult.getOrElse { throwable ->
                                _setupState.update { it.copy(extractionStatus = GlobalStepStatus.ERROR) }
                                when (throwable) {
                                    is CryptVersionException ->
                                        throw AppErrorException(AppError.Crypt15Detected)
                                    else ->
                                        throw AppErrorException(
                                            AppError.ExtractionError(
                                                throwable.message ?: context.getString(R.string.error_unknown_simple),
                                            ),
                                        )
                                }
                            }
                        _setupState.update {
                            it.copy(
                                extractionStatus = GlobalStepStatus.SUCCESS,
                                decryptionStatus = GlobalStepStatus.LOADING,
                            )
                        }

                        if (isOperationCancelled()) return@withContext Result.success(Unit)

                        val crypt14OutputDb = File(context.filesDir, "msgstore.db")
                        val decryptResult =
                            decryptor.decrypt(files.crypt14File, files.keyFile, crypt14OutputDb) { progress ->
                                if (isOperationCancelled()) throw InterruptedException("Cancelled")
                                _setupState.update { it.copy(decryptionProgress = progress) }
                            }
                        decryptResult.getOrElse { throwable ->
                            _setupState.update { it.copy(decryptionStatus = GlobalStepStatus.ERROR) }
                            throw AppErrorException(
                                AppError.DecryptionFailed(throwable.message ?: context.getString(R.string.error_unknown_simple)),
                            )
                        }
                        _setupState.update {
                            it.copy(
                                decryptionStatus = GlobalStepStatus.SUCCESS,
                                contactsStatus = GlobalStepStatus.LOADING,
                            )
                        }
                        outputDb = crypt14OutputDb
                        waDbFile = files.waDbFile
                    }

                    // Step 4: Apri il database per le query
                    if (isOperationCancelled()) throw kotlinx.coroutines.CancellationException()

                    try {
                        database.open(outputDb, waDbFile)

                        // Calcola signature WAL-aware per rilevare aggiornamenti futuri
                        val dbSignature =
                            fileExtractor.calculateSourceDbSignature()
                                ?: cacheManager.calculateFileHash(outputDb.absolutePath)
                        if (dbSignature != null) {
                            if (cacheManager.isRootDbChanged(dbSignature)) {
                                android.util.Log.d("WhatStat", "DB changed, clearing root cache")
                                cacheManager.clearRootCache()
                            }
                            cacheManager.saveRootDbHash(dbSignature)
                        }
                    } catch (e: Exception) {
                        _setupState.update { it.copy(contactsStatus = GlobalStepStatus.ERROR) }
                        throw AppErrorException(
                            AppError.DatabaseError(e.message ?: context.getString(R.string.error_db_unavailable)),
                        )
                    }
                    _setupState.update {
                        it.copy(
                            contactsStatus = GlobalStepStatus.SUCCESS,
                            isLoading = false,
                            isComplete = true,
                        )
                    }
                }.onFailure { throwable ->
                    if (throwable is kotlinx.coroutines.CancellationException) {
                        throw throwable
                    }
                    if (isOperationCancelled()) {
                        return@onFailure
                    }
                    val errorMessage =
                        when (throwable) {
                            is AppErrorException -> throwable.appError.resolveMessage(context)
                            else -> throwable.message ?: context.getString(R.string.error_unknown_simple)
                        }
                    _setupState.update { it.copy(isLoading = false, error = errorMessage) }
                }
            }

        /**
         * Esegue il flusso di setup saltando root ed estrazione,
         * usando i file già copiati manualmente dall'utente.
         */
        suspend fun performManualSetup(): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    _setupState.update { it.copy(isLoading = true, error = null) }

                    // Saltiamo esplicitamente gli step 1 e 2 per feedback visivo
                    _setupState.update {
                        it.copy(
                            rootStatus = GlobalStepStatus.SUCCESS,
                            extractionStatus = GlobalStepStatus.SUCCESS,
                            decryptionStatus = GlobalStepStatus.LOADING,
                        )
                    }

                    val keyFile = File(context.filesDir, FileExtractor.KEY_DEST)
                    val crypt14File = File(context.filesDir, FileExtractor.CRYPT14_DEST)
                    val dbDecryptedFile = File(context.filesDir, FileExtractor.DB_DECRYPTED_DEST)
                    val waDbFile = File(context.filesDir, FileExtractor.WA_DB_DEST)

                    // Determina se l'utente ha fornito un db già decifrato
                    val hasDecryptedDb = dbDecryptedFile.exists()

                    if (!keyFile.exists()) {
                        _setupState.update { it.copy(decryptionStatus = GlobalStepStatus.ERROR) }
                        throw AppErrorException(AppError.ExtractionError("Il file chiave non è stato copiato."))
                    }

                    if (!hasDecryptedDb && !crypt14File.exists()) {
                        _setupState.update { it.copy(decryptionStatus = GlobalStepStatus.ERROR) }
                        throw AppErrorException(AppError.ExtractionError("Il file database non è stato copiato."))
                    }

                    // Step 3: Decifra il database OPZIONE: usa db già decifrato
                    val outputDb: File
                    if (hasDecryptedDb) {
                        // Usa il file già decifrato
                        outputDb = dbDecryptedFile
                        // Verifica che sia un SQLite valido
                        try {
                            val header = ByteArray(16)
                            outputDb.inputStream().use { it.read(header) }
                            val magic = String(header, 0, 15)
                            if (!magic.startsWith("SQLite format 3")) {
                                throw AppErrorException(AppError.DecryptionError("Il file non sembra un database SQLite valido."))
                            }
                        } catch (e: AppErrorException) {
                            throw e
                        } catch (e: Exception) {
                            throw AppErrorException(
                                AppError.DecryptionError(
                                    context.getString(R.string.error_file_read_detail, e.message ?: ""),
                                ),
                            )
                        }
                    } else {
                        // Decifra il file crypt14
                        outputDb = File(context.filesDir, "msgstore.db")
                        val decryptResult =
                            decryptor.decrypt(crypt14File, keyFile, outputDb) { progress ->
                                if (isOperationCancelled()) throw InterruptedException("Cancelled")
                                _setupState.update { it.copy(decryptionProgress = progress) }
                            }
                        decryptResult.getOrElse { throwable ->
                            _setupState.update { it.copy(decryptionStatus = GlobalStepStatus.ERROR) }
                            throw AppErrorException(
                                AppError.DecryptionFailed(throwable.message ?: context.getString(R.string.error_unknown_simple)),
                            )
                        }
                    }
                    _setupState.update { it.copy(decryptionStatus = GlobalStepStatus.SUCCESS, contactsStatus = GlobalStepStatus.LOADING) }

                    // Step 4: Apri il database per le query (waDbFile opzionale)
                    if (isOperationCancelled()) throw kotlinx.coroutines.CancellationException()
                    try {
                        database.open(outputDb, waDbFile)

                        // Verifica se il DB è cambiato → invalida la cache
                        val hashSource = if (crypt14File.exists()) crypt14File else outputDb
                        val dbHash = cacheManager.calculateFileHash(hashSource.absolutePath)
                        if (dbHash != null) {
                            if (cacheManager.isRootDbChanged(dbHash)) {
                                android.util.Log.d("WhatStat", "DB changed (manual), clearing cache")
                                cacheManager.clearRootCache()
                            }
                            cacheManager.saveRootDbHash(dbHash)
                        }
                    } catch (e: Exception) {
                        _setupState.update { it.copy(contactsStatus = GlobalStepStatus.ERROR) }
                        throw AppErrorException(
                            AppError.DatabaseError(e.message ?: context.getString(R.string.error_db_unavailable)),
                        )
                    }
                    _setupState.update { it.copy(contactsStatus = GlobalStepStatus.SUCCESS, isLoading = false, isComplete = true) }
                }.onFailure { throwable ->
                    if (throwable is kotlinx.coroutines.CancellationException) {
                        throw throwable
                    }
                    if (isOperationCancelled()) {
                        return@onFailure
                    }
                    val errorMessage =
                        when (throwable) {
                            is AppErrorException -> throwable.appError.resolveMessage(context)
                            else -> throwable.message ?: context.getString(R.string.error_unknown_simple)
                        }
                    _setupState.update { it.copy(isLoading = false, error = errorMessage) }
                }
            }

        /** Verifica se il database è già aperto e pronto (per skip del setup). */
        fun isDatabaseReady(): Boolean = database.isOpen()

        /**
         * Tenta di ripristinare una sessione precedente se i file decifrati
         * sono già presenti nella memoria interna dell'app.
         */
        suspend fun tryRestoreSession(): Boolean =
            withContext(Dispatchers.IO) {
                val outputDb = File(context.filesDir, "msgstore.db")
                val waDbFile = File(context.filesDir, FileExtractor.WA_DB_DEST)

                if (outputDb.exists() && outputDb.length() > 0) {
                    try {
                        database.open(outputDb, waDbFile)
                        if (database.isOpen()) {
                            // Calcola la signature WAL-aware del DB sorgente live
                            val currentSignature = fileExtractor.calculateSourceDbSignature()

                            android.util.Log.d("WhatStat", "tryRestoreSession: sourceSignature=$currentSignature")

                            if (currentSignature != null && cacheManager.isRootDbChanged(currentSignature)) {
                                // DB sorgente cambiato - permettiamo all'utente di scegliere
                                android.util.Log.d("WhatStat", "DB changed, letting user decide")

                                _setupState.update {
                                    it.copy(
                                        isComplete = true,
                                        rootStatus = GlobalStepStatus.SUCCESS,
                                        extractionStatus = GlobalStepStatus.SUCCESS,
                                        decryptionStatus = GlobalStepStatus.SUCCESS,
                                        contactsStatus = GlobalStepStatus.SUCCESS,
                                        hasNewDbAvailable = true,
                                    )
                                }
                                return@withContext true
                            }

                            _setupState.update {
                                it.copy(
                                    isComplete = true,
                                    rootStatus = GlobalStepStatus.SUCCESS,
                                    extractionStatus = GlobalStepStatus.SUCCESS,
                                    decryptionStatus = GlobalStepStatus.SUCCESS,
                                    contactsStatus = GlobalStepStatus.SUCCESS,
                                )
                            }
                            return@withContext true
                        }
                    } catch (e: Exception) {
                        // Se non riusciamo ad aprire, facciamo finta di nulla e chiediamo il setup
                        // NON cancellare msgstore.db: potrebbe essere un errore transiente su wa.db
                        android.util.Log.w("WhatStat", "tryRestoreSession failed: ${e.message}")
                    }
                }
                false
            }

        /** Chiude il database, pulisce i file estratti e invalida la cache. */
        suspend fun cleanup() =
            withContext(Dispatchers.IO) {
                database.close()
                fileExtractor.cleanup()
                cacheManager.clearRootCache(resetWordIndexes = true)
            }

        /** Proxy per copiare file da Uri senza esporre FileExtractor al ViewModel */
        suspend fun copyFileFromUri(
            uri: Uri,
            destFileName: String,
        ) {
            fileExtractor.copyFromUri(uri, destFileName)
        }
    }

// --- Inner classes ---

enum class SetupStep {
    ROOT_CHECK,
    FILE_EXTRACTION,
    DECRYPTION,
    DATABASE_OPEN,
}

data class RepositorySetupState(
    val isLoading: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null,
    val rootStatus: GlobalStepStatus = GlobalStepStatus.IDLE,
    val extractionStatus: GlobalStepStatus = GlobalStepStatus.IDLE,
    val decryptionStatus: GlobalStepStatus = GlobalStepStatus.IDLE,
    val contactsStatus: GlobalStepStatus = GlobalStepStatus.IDLE,
    val decryptionProgress: Float = 0f,
    val hasNewDbAvailable: Boolean = false,
)

enum class GlobalStepStatus { IDLE, LOADING, SUCCESS, ERROR }

/**
 * Wrapper per lanciare AppError come eccezione nel flusso Result.
 */
class AppErrorException(val appError: AppError) : Exception(appError.javaClass.simpleName)
