package com.whatstat.reborn.data.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Motore di decifratura per file WhatsApp .crypt14.
 *
 * Struttura del file crypt14:
 *   [Header Protobuf 1]  — lunghezza variabile (~67 byte)
 *   [Header Protobuf 2]  — lunghezza variabile (~24 byte)
 *   [IV]                  — 12 byte (Initialization Vector per AES-GCM)
 *   [Dati cifrati]        — payload AES-GCM (include auth tag finale di 16 byte)
 *
 * Dopo la decifratura, il payload è compresso con zlib e contiene il database SQLite.
 */
@Singleton
class Crypt14Decryptor @Inject constructor(
    private val keyReader: KeyReader
) {
    companion object {
        private const val CIPHER_ALGO    = "AES/GCM/NoPadding"
        private const val IV_LENGTH      = 12   // byte
        private const val AUTH_TAG_BITS  = 128  // bit (= 16 byte)
        private const val SQLITE_MAGIC   = "SQLite format 3\u0000"

        /**
         * Offset noti per l'inizio dell'IV nelle varie versioni di WhatsApp.
         * L'header protobuf ha lunghezza variabile, quindi proviamo più offset.
         * Gli offset sono ordinati dal più comune al meno comune.
         */
        private val KNOWN_IV_OFFSETS = intArrayOf(67, 51, 99, 91, 86, 15)
    }

    /**
     * Decifra il file crypt14 e scrive il database SQLite risultante.
     *
     * Prova automaticamente diversi offset dell'header per supportare
     * più versioni di WhatsApp. Il primo offset che produce un output
     * SQLite valido viene usato.
     *
     * @param crypt14File  File cifrato (.crypt14)
     * @param keyFile      File contenente la chiave AES
     * @param outputFile   Dove scrivere il database SQLite decifrato
     * @return Result contenente il file di output o l'errore
     */
    suspend fun decrypt(
        crypt14File: File,
        keyFile: File,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val key = keyReader.readKey(keyFile)
            val secretKey = SecretKeySpec(key, "AES")
            val fileData = crypt14File.readBytes()

            // Prima prova: parsing protobuf per trovare l'offset del IV dinamicamente
            val dynamicOffset = tryParseProtobufHeaders(fileData)
            if (dynamicOffset != null) {
                val result = tryDecryptWithOffset(fileData, secretKey, dynamicOffset)
                if (result != null) {
                    outputFile.writeBytes(result)
                    return@runCatching outputFile
                }
            }

            // Fallback: prova offset noti
            for (offset in KNOWN_IV_OFFSETS) {
                if (offset + IV_LENGTH >= fileData.size) continue
                val result = tryDecryptWithOffset(fileData, secretKey, offset)
                if (result != null) {
                    outputFile.writeBytes(result)
                    return@runCatching outputFile
                }
            }

            throw DecryptionException(
                "Impossibile decifrare il database. Nessun offset dell'header " +
                "ha prodotto un output SQLite valido. La chiave potrebbe non " +
                "corrispondere al backup, o il formato potrebbe essere cambiato."
            )
        }
    }

    /**
     * Tenta di decifrare con un dato offset per l'IV.
     * Restituisce i byte del database SQLite decompresso, o null se fallisce.
     */
    private fun tryDecryptWithOffset(
        fileData: ByteArray,
        secretKey: SecretKeySpec,
        ivOffset: Int
    ): ByteArray? {
        return try {
            // Estrai IV (12 byte a partire dall'offset)
            val iv = fileData.copyOfRange(ivOffset, ivOffset + IV_LENGTH)

            // Il payload cifrato inizia subito dopo l'IV
            val encryptedData = fileData.copyOfRange(ivOffset + IV_LENGTH, fileData.size)

            // Configura e esegui AES-GCM (il tag è incluso nel payload)
            val cipher = Cipher.getInstance(CIPHER_ALGO)
            val gcmSpec = GCMParameterSpec(AUTH_TAG_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            val decryptedData = cipher.doFinal(encryptedData)

            // Decomprimi (il payload decifrato è compresso con zlib)
            val decompressed = decompress(decryptedData)

            // Verifica che il risultato sia un SQLite valido
            val magic = String(decompressed, 0, minOf(16, decompressed.size))
            if (magic.startsWith("SQLite format 3")) {
                decompressed
            } else {
                null // Non è un SQLite valido, prova altro offset
            }
        } catch (_: Exception) {
            null // Decifratura fallita con questo offset
        }
    }

    /**
     * Tenta di parsare gli header protobuf per determinare dinamicamente
     * dove inizia l'IV. Usa parsing basico dei varint protobuf.
     *
     * Il formato crypt14 ha due messaggi protobuf length-delimited consecutivi.
     * Dopo entrambi, i successivi 12 byte sono l'IV.
     */
    private fun tryParseProtobufHeaders(data: ByteArray): Int? {
        return try {
            var offset = 0

            // Ci aspettiamo due sezioni protobuf length-delimited.
            // Ogni sezione inizia con un tag byte (field_number << 3 | wire_type)
            // seguito da un varint che indica la lunghezza del contenuto.
            for (section in 0 until 2) {
                if (offset >= data.size) return null

                // Leggi il tag byte
                val tag = data[offset].toInt() and 0xFF
                val wireType = tag and 0x07

                if (wireType == 2) {
                    // Length-delimited: leggi il varint per la lunghezza
                    offset++ // skip tag
                    val (length, bytesRead) = readVarint(data, offset)
                    offset += bytesRead
                    offset += length.toInt() // skip il contenuto della sezione
                } else if (wireType == 0) {
                    // Varint: leggi e salta
                    offset++ // skip tag
                    val (_, bytesRead) = readVarint(data, offset)
                    offset += bytesRead
                } else {
                    // Wire type non riconosciuto
                    return null
                }
            }

            // Verifica plausibilità dell'offset
            if (offset > 0 && offset + IV_LENGTH < data.size) offset else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Legge un varint protobuf (encoding a lunghezza variabile).
     * @return Pair(valore, numero di byte letti)
     */
    private fun readVarint(data: ByteArray, startOffset: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var offset = startOffset

        while (offset < data.size) {
            val b = data[offset].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            offset++
            if (b and 0x80 == 0) break // MSB = 0 → ultimo byte del varint
            shift += 7
            if (shift >= 64) throw IllegalStateException("Varint troppo lungo")
        }

        return Pair(result, offset - startOffset)
    }

    /**
     * Decomprime dati zlib.
     * WhatsApp usa zlib standard (con header) per comprimere il database.
     */
    private fun decompress(compressed: ByteArray): ByteArray {
        // Prova prima con zlib header (standard), poi senza (raw deflate)
        return try {
            decompressWithInflater(compressed, wrapZlib = true)
        } catch (_: Exception) {
            decompressWithInflater(compressed, wrapZlib = false)
        }
    }

    private fun decompressWithInflater(compressed: ByteArray, wrapZlib: Boolean): ByteArray {
        val inflater = if (wrapZlib) Inflater() else Inflater(true)
        try {
            inflater.setInput(compressed)
            val outputStream = ByteArrayOutputStream(compressed.size * 4)
            val buffer = ByteArray(8192)

            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && inflater.needsInput()) break
                outputStream.write(buffer, 0, count)
            }

            return outputStream.toByteArray()
        } finally {
            inflater.end()
        }
    }
}

class DecryptionException(message: String) : Exception(message)
