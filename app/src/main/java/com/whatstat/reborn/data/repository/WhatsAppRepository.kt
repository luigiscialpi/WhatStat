package com.whatstat.reborn.data.repository

import android.content.Context
import com.whatstat.reborn.data.crypto.Crypt14Decryptor
import com.whatstat.reborn.data.db.WhatsAppDatabase
import com.whatstat.reborn.data.root.CryptVersionException
import com.whatstat.reborn.data.root.FileExtractor
import com.whatstat.reborn.data.root.RootManager
import com.whatstat.reborn.data.root.RootStatus
import com.whatstat.reborn.domain.model.AppError
import com.whatstat.reborn.data.cache.CacheManager
import dagger.hilt.android.qualifiers.ApplicationContext
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    private val cacheManager: CacheManager,
    @ApplicationContext private val context: Context
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
        _setupState.update { it.copy(isLoading = false, error = "Operazione annullata") }
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
    suspend fun performSetup(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            _setupState.update { it.copy(isLoading = true, error = null) }

            // Step 1: Verifica root
            _setupState.update { it.copy(rootStatus = GlobalStepStatus.LOADING) }
            val rootStatus = rootManager.checkRootAccess()
            when (rootStatus) {
                RootStatus.NOT_AVAILABLE -> throw AppErrorException(AppError.RootNotAvailable)
                RootStatus.NOT_GRANTED   -> throw AppErrorException(AppError.RootNotGranted)
                RootStatus.GRANTED       -> { /* ok */ }
            }
            _setupState.update { it.copy(rootStatus = GlobalStepStatus.SUCCESS, extractionStatus = GlobalStepStatus.LOADING) }

            // Step 2: Estrai file da WhatsApp
            val extractResult = fileExtractor.extractAll()
            val files = extractResult.getOrElse { throwable ->
                _setupState.update { it.copy(extractionStatus = GlobalStepStatus.ERROR) }
                when (throwable) {
                    is CryptVersionException -> throw AppErrorException(AppError.Crypt15Detected)
                    else -> throw AppErrorException(
                        AppError.ExtractionError(throwable.message ?: "Errore sconosciuto")
                    )
                }
            }
            _setupState.update { it.copy(extractionStatus = GlobalStepStatus.SUCCESS, decryptionStatus = GlobalStepStatus.LOADING) }

            // Step 3: Decifra il database
            if (isOperationCancelled()) {
                return@withContext Result.success(Unit)
            }
            val outputDb = File(context.filesDir, "msgstore.db")
            val decryptResult = decryptor.decrypt(files.crypt14File, files.keyFile, outputDb) { progress ->
                if (isOperationCancelled()) {
                    throw InterruptedException("Cancelled")
                }
                _setupState.update { it.copy(decryptionProgress = progress) }
            }
            decryptResult.getOrElse { throwable ->
                _setupState.update { it.copy(decryptionStatus = GlobalStepStatus.ERROR) }
                throw AppErrorException(
                    AppError.DecryptionFailed(throwable.message ?: "Errore sconosciuto")
                )
            }
            _setupState.update { it.copy(decryptionStatus = GlobalStepStatus.SUCCESS, contactsStatus = GlobalStepStatus.LOADING) }

            // Step 4: Apri il database per le query
            try {
                database.open(outputDb, files.waDbFile)
                
                // Verifica se il DB è cambiato → invalida la cache
                val dbHash = cacheManager.calculateFileHash(files.crypt14File.absolutePath)
                if (dbHash != null) {
                    if (cacheManager.isDbChanged(dbHash)) {
                        android.util.Log.d("WhatStat", "DB changed, clearing cache")
                        cacheManager.clearCache()
                    }
                    cacheManager.saveDbHash(dbHash, files.crypt14File.absolutePath)
                }
            } catch (e: Exception) {
                _setupState.update { it.copy(contactsStatus = GlobalStepStatus.ERROR) }
                throw AppErrorException(
                    AppError.DatabaseError(e.message ?: "Impossibile aprire il database")
                )
            }
            _setupState.update { it.copy(contactsStatus = GlobalStepStatus.SUCCESS, isLoading = false, isComplete = true) }
        }.onFailure { throwable ->
            val errorMessage = when (throwable) {
                is AppErrorException -> throwable.appError.userMessage
                else -> throwable.message ?: "Errore sconosciuto"
            }
            _setupState.update { it.copy(isLoading = false, error = errorMessage) }
        }
    }

    /**
     * Esegue il flusso di setup saltando root ed estrazione,
     * usando i file già copiati manualmente dall'utente.
     */
    suspend fun performManualSetup(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            _setupState.update { it.copy(isLoading = true, error = null) }

            // Saltiamo esplicitamente gli step 1 e 2 per feedback visivo
            _setupState.update { it.copy(rootStatus = GlobalStepStatus.SUCCESS, extractionStatus = GlobalStepStatus.SUCCESS, decryptionStatus = GlobalStepStatus.LOADING) }

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
                } catch (e: AppErrorException) { throw e }
                catch (e: Exception) {
                    throw AppErrorException(AppError.DecryptionError("Errore lettura file: ${e.message}"))
                }
            } else {
                // Decifra il file crypt14
                outputDb = File(context.filesDir, "msgstore.db")
                val decryptResult = decryptor.decrypt(crypt14File, keyFile, outputDb) { progress ->
                    _setupState.update { it.copy(decryptionProgress = progress) }
                }
                decryptResult.getOrElse { throwable ->
                    _setupState.update { it.copy(decryptionStatus = GlobalStepStatus.ERROR) }
                    throw AppErrorException(
                        AppError.DecryptionFailed(throwable.message ?: "Errore sconosciuto")
                    )
                }
            }
            _setupState.update { it.copy(decryptionStatus = GlobalStepStatus.SUCCESS, contactsStatus = GlobalStepStatus.LOADING) }

            // Step 4: Apri il database per le query (waDbFile opzionale)
            try {
                database.open(outputDb, waDbFile)

                // Verifica se il DB è cambiato → invalida la cache
                val hashSource = if (crypt14File.exists()) crypt14File else outputDb
                val dbHash = cacheManager.calculateFileHash(hashSource.absolutePath)
                if (dbHash != null) {
                    if (cacheManager.isDbChanged(dbHash)) {
                        android.util.Log.d("WhatStat", "DB changed (manual), clearing cache")
                        cacheManager.clearCache()
                    }
                    cacheManager.saveDbHash(dbHash, hashSource.absolutePath)
                }
            } catch (e: Exception) {
                _setupState.update { it.copy(contactsStatus = GlobalStepStatus.ERROR) }
                throw AppErrorException(
                    AppError.DatabaseError(e.message ?: "Impossibile aprire il database")
                )
            }
            _setupState.update { it.copy(contactsStatus = GlobalStepStatus.SUCCESS, isLoading = false, isComplete = true) }
        }.onFailure { throwable ->
            val errorMessage = when (throwable) {
                is AppErrorException -> throwable.appError.userMessage
                else -> throwable.message ?: "Errore sconosciuto"
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
    suspend fun tryRestoreSession(): Boolean = withContext(Dispatchers.IO) {
        val outputDb = File(context.filesDir, "msgstore.db")
        val waDbFile = File(context.filesDir, FileExtractor.WA_DB_DEST)

        if (outputDb.exists() && outputDb.length() > 0) {
            try {
                database.open(outputDb, waDbFile)
                if (database.isOpen()) {
                    // Calcola hash del file crypt14 SORGENTE sul dispositivo (via root/SuFileInputStream)
                    // Non la copia locale che è ferma all'ultimo setup
                    val currentHash = fileExtractor.calculateSourceCrypt14Hash()

                    android.util.Log.d("WhatStat", "tryRestoreSession: sourceHash=$currentHash")

                    if (currentHash != null && cacheManager.isDbChanged(currentHash)) {
                        // DB sorgente cambiato - invalida cache e rigetta la sessione
                        android.util.Log.d("WhatStat", "DB changed, invalidating session")
                        cacheManager.clearCache()
                        database.close()
                        outputDb.delete()
                        
                        _setupState.update { it.copy(
                            isLoading = false,
                            isComplete = false,
                            error = "Nuovo backup di WhatsApp rilevato rispetto a quello attuale. Esegui nuovamente il setup."
                        ) }
                        return@withContext false
                    }
                    
                    _setupState.update { it.copy(
                        isComplete = true,
                        rootStatus = GlobalStepStatus.SUCCESS,
                        extractionStatus = GlobalStepStatus.SUCCESS,
                        decryptionStatus = GlobalStepStatus.SUCCESS,
                        contactsStatus = GlobalStepStatus.SUCCESS
                    ) }
                    return@withContext true
                }
            } catch (e: Exception) {
                // Se non riusciamo ad aprire, facciamo finta di nulla e chiediamo il setup
                outputDb.delete()
            }
        }
        false
    }

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

// --- Inner classes ---

enum class SetupStep {
    ROOT_CHECK,
    FILE_EXTRACTION,
    DECRYPTION,
    DATABASE_OPEN
}

data class RepositorySetupState(
    val isLoading: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null,
    val rootStatus: GlobalStepStatus = GlobalStepStatus.IDLE,
    val extractionStatus: GlobalStepStatus = GlobalStepStatus.IDLE,
    val decryptionStatus: GlobalStepStatus = GlobalStepStatus.IDLE,
    val contactsStatus: GlobalStepStatus = GlobalStepStatus.IDLE,
    val decryptionProgress: Float = 0f
)

enum class GlobalStepStatus { IDLE, LOADING, SUCCESS, ERROR }

/**
 * Wrapper per lanciare AppError come eccezione nel flusso Result.
 */
class AppErrorException(val appError: AppError) : Exception(appError.userMessage)
