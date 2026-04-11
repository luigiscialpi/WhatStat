package com.whatstat.reborn.data.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Crypt14Decryptor @Inject constructor(
    private val keyReader: KeyReader
) {
    companion object {
        private const val CIPHER_ALGO    = "AES/GCM/NoPadding"
        private const val IV_LENGTH      = 12   // byte
        private const val AUTH_TAG_BITS  = 128  // bit (= 16 byte)
        private const val BUFFER_SIZE    = 262144 // 256KB per massime prestazioni IO

        private val KNOWN_IV_OFFSETS = intArrayOf(67, 51, 99, 91, 86, 15)
    }

    suspend fun decrypt(
        crypt14File: File,
        keyFile: File,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val key = keyReader.readKey(keyFile)
            val secretKey = SecretKeySpec(key, "AES")

            val headerBuffer = ByteArray(1024) // Aumentato a 1024 per sicurezza
            var bytesRead = 0
            crypt14File.inputStream().use {
                bytesRead = it.read(headerBuffer)
            }

            if (bytesRead < 50) {
                throw DecryptionException("File crypt14 troppo piccolo per essere valido.")
            }

            val dynamicOffset = tryParseProtobufHeaders(headerBuffer)
            val offsetsToTry = mutableListOf<Int>()
            if (dynamicOffset != null) {
                offsetsToTry.add(dynamicOffset)
            }
            offsetsToTry.addAll(KNOWN_IV_OFFSETS.toList())

            var success = false
            var errors = mutableSetOf<String>()
            for (offset in offsetsToTry) {
                if (offset + IV_LENGTH >= crypt14File.length()) continue

                try {
                    if (tryStandardStreamWithOffset(crypt14File, secretKey, offset, outputFile, wrapZlib = true, onProgress)) {
                        success = true
                        break
                    }
                } catch (e: Exception) { errors.add("O$offset-Z: ${e.message}") }

                try {
                    if (tryStandardStreamWithOffset(crypt14File, secretKey, offset, outputFile, wrapZlib = false, onProgress)) {
                        success = true
                        break
                    }
                } catch (e: Exception) { errors.add("O$offset-R: ${e.message}") }
            }

            if (!success) {
                if (outputFile.exists()) outputFile.delete()
                throw DecryptionException(
                    "Impossibile decifrare il database. Nessun offset valido. Errori riscontrati: " + errors.joinToString(" | ")
                )
            }

            outputFile
        }
    }

    private fun tryStandardStreamWithOffset(
        crypt14File: File,
        secretKey: SecretKeySpec,
        ivOffset: Int,
        outputFile: File,
        wrapZlib: Boolean,
        onProgress: (Float) -> Unit
    ): Boolean {
        val totalSize = crypt14File.length()
        var totalReadFromSource = 0L

        FileInputStream(crypt14File).use { fis ->
            // Skip IV offset sicuro
            var skipped = 0L
            while (skipped < ivOffset) {
                val s = fis.skip(ivOffset - skipped)
                if (s <= 0) break
                skipped += s
            }
            totalReadFromSource += skipped

            val iv = ByteArray(IV_LENGTH)
            val read = fis.read(iv)
            if (read != IV_LENGTH) return false
            totalReadFromSource += read

            val cipher = Cipher.getInstance(CIPHER_ALGO)
            val gcmSpec = GCMParameterSpec(AUTH_TAG_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            // Usiamo un wrapper per monitorare i byte letti dal file sorgente
            val reportingFis = object : java.io.FilterInputStream(fis) {
                private var lastReportedProgress = -1f

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val r = super.read(b, off, len)
                    if (r > 0) {
                        totalReadFromSource += r
                        val currentProgress = totalReadFromSource.toFloat() / totalSize.toFloat()
                        // Throttle: invia solo se c'è un salto dell'1% o se siamo alla fine
                        if (currentProgress - lastReportedProgress >= 0.01f || currentProgress >= 0.99f) {
                            onProgress(currentProgress)
                            lastReportedProgress = currentProgress
                        }
                    }
                    return r
                }
            }

            val cis = CipherInputStream(reportingFis, cipher)
            val inflater = if (wrapZlib) Inflater() else Inflater(true)
            val iis = InflaterInputStream(cis, inflater, BUFFER_SIZE)

            FileOutputStream(outputFile).use { fos ->
                val buffer = ByteArray(BUFFER_SIZE)
                
                // Primo blocco per verificare SQLite magic
                val firstRead = try {
                    iis.read(buffer)
                } catch (e: Exception) {
                    throw Exception("Inflater error: ${e.message}")
                }
                
                if (firstRead < 16) throw Exception("Dati decifrati troppo corti: $firstRead byte")

                val magic = String(buffer, 0, 16)
                if (!magic.startsWith("SQLite format 3")) {
                    throw Exception("Magic code non valido: '${magic.replace("\u0000", "")}'")
                }

                // Scrive il primo blocco buono
                fos.write(buffer, 0, firstRead)

                // Copia il resto
                try {
                    var bRead: Int
                    while (iis.read(buffer).also { bRead = it } != -1) {
                        fos.write(buffer, 0, bRead)
                    }
                } catch (e: Exception) {
                    throw Exception("Errore copia decifratura: ${e.message}")
                }
            }
        }
        onProgress(1.0f) // Assicura 100% fine
        return true
    }

    private fun tryParseProtobufHeaders(data: ByteArray): Int? {
        return try {
            var offset = 0
            for (section in 0 until 2) {
                if (offset >= data.size) return null
                val tag = data[offset].toInt() and 0xFF
                val wireType = tag and 0x07

                if (wireType == 2) {
                    offset++
                    val (length, bytesRead) = readVarint(data, offset)
                    offset += bytesRead
                    offset += length.toInt()
                } else if (wireType == 0) {
                    offset++
                    val (_, bytesRead) = readVarint(data, offset)
                    offset += bytesRead
                } else {
                    return null
                }
            }
            if (offset > 0 && offset + IV_LENGTH < data.size) offset else null
        } catch (_: Exception) {
            null
        }
    }

    private fun readVarint(data: ByteArray, startOffset: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var offset = startOffset
        while (offset < data.size) {
            val b = data[offset].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            offset++
            if (b and 0x80 == 0) break
            shift += 7
            if (shift >= 64) throw IllegalStateException("Varint troppo lungo")
        }
        return Pair(result, offset - startOffset)
    }
}
class DecryptionException(message: String) : Exception(message)
