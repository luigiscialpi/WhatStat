package com.whatstat.reborn.data.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Crypt14Decryptor @Inject constructor(
    private val keyReader: KeyReader
) {
    companion object {
        private const val GCM_ALGO = "AES/GCM/NoPadding"
        private const val AUTH_TAG_BITS = 128
        private const val BUFFER_SIZE = 262144
    }

    data class IVResult(val iv: ByteArray, val dataOffset: Int)

    // ─── Header parsing ───────────────────────────────────────────────────────

    fun parseHeader(data: ByteArray): IVResult? {
        var pos = 0
        if (data[0].toInt() and 0xFF > 0x0F) {
            while (pos < data.size && data[pos].toInt() and 0x80 != 0) pos++
            pos++
        }
        val protobufStart = pos

        return try {
            val log = StringBuilder()
            val iv = findIVInProtobuf(data, pos, depth = 0, log)
                ?: throw DecryptionException(
                    "IV non trovato nel protobuf.\n\n" +
                    "=== PARSING LOG ===\n$log\n\n" +
                    "=== HEADER HEX (primi 120 byte) ===\n" +
                    data.take(120).chunked(16).joinToString("\n") { row ->
                        row.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
                    }
                )

            val dataOffset = computeDataOffset(data, protobufStart)
            IVResult(iv, dataOffset)
        } catch (e: DecryptionException) {
            throw e
        } catch (e: Exception) {
            throw DecryptionException(
                "parseHeader exception: ${e.message}\n" +
                "Header: ${data.take(32).joinToString(",") { "%02x".format(it.toInt() and 0xFF) }}"
            )
        }
    }

    private fun findIVInProtobuf(
        data: ByteArray,
        startPos: Int,
        depth: Int,
        log: StringBuilder
    ): ByteArray? {
        if (depth > 2) return null
        var pos = startPos

        while (pos < data.size - 1) {
            val tagByte = data[pos].toInt() and 0xFF
            if (tagByte == 0x00) { pos++; continue }
            val wireType = tagByte and 0x07
            val fieldNum = tagByte ushr 3
            pos++

            when (wireType) {
                0 -> {
                    val (value, consumed) = readVarint(data, pos)
                    log.append("  [depth=$depth] field=$fieldNum varint=$value\n")
                    pos += consumed
                }
                2 -> {
                    val (len, consumed) = readVarint(data, pos)
                    pos += consumed
                    val fieldEnd = pos + len.toInt()
                    if (fieldEnd > data.size) {
                        log.append("  [depth=$depth] field=$fieldNum len=$len FUORI BOUNDS\n")
                        return null
                    }

                    val preview = data.copyOfRange(pos, minOf(pos + 8, fieldEnd))
                        .joinToString(",") { "%02x".format(it.toInt() and 0xFF) }
                    log.append("  [depth=$depth] field=$fieldNum len=$len preview=[$preview]\n")

                    if (len == 12L || len == 16L) {
                        val candidate = data.copyOfRange(pos, fieldEnd)
                        if (!candidate.all { it == 0.toByte() }) {
                            log.append("  → IV TROVATO len=$len a pos=$pos depth=$depth\n")
                            return candidate
                        } else {
                            log.append("  → len=$len ma tutti zeri, skip\n")
                        }
                    }

                    if (len in 2..200 && depth < 2) {
                        val nested = data.copyOfRange(pos, fieldEnd)
                        val found = findIVInProtobuf(nested, 0, depth + 1, log)
                        if (found != null) return found
                    }

                    pos = fieldEnd
                }
                5 -> { log.append("  [depth=$depth] field=$fieldNum 32bit\n"); pos += 4 }
                1 -> { log.append("  [depth=$depth] field=$fieldNum 64bit\n"); pos += 8 }
                else -> {
                    log.append("  [depth=$depth] wireType=$wireType SCONOSCIUTO a pos=${pos - 1}\n")
                    if (depth > 0) return null
                    pos++
                }
            }
        }
        return null
    }

    private fun computeDataOffset(data: ByteArray, startPos: Int): Int {
        var pos = startPos
        var lastValidPos = startPos

        while (pos < data.size - 1) {
            val tagByte = data[pos].toInt() and 0xFF
            if (tagByte == 0x00) break
            val wireType = tagByte and 0x07
            pos++

            when (wireType) {
                0 -> { val (_, c) = readVarint(data, pos); pos += c }
                2 -> {
                    val (len, c) = readVarint(data, pos)
                    pos += c
                    val fieldEnd = pos + len.toInt()
                    if (fieldEnd > data.size) break
                    pos = fieldEnd
                }
                5 -> pos += 4
                1 -> pos += 8
                else -> break
            }
            lastValidPos = pos
        }

        return if (lastValidPos in 50..300) lastValidPos else 67
    }

    private fun readVarint(data: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var offset = start
        while (offset < data.size) {
            val b = data[offset].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            offset++
            if (b and 0x80 == 0) break
            shift += 7
        }
        return Pair(result, offset - start)
    }

    // ─── Decrypt entry point ──────────────────────────────────────────────────

    suspend fun decrypt(
        crypt14File: File,
        keyFile: File,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val keyOptions = keyReader.readKeyOptions(keyFile)
            if (keyOptions.isEmpty()) throw DecryptionException("Nessuna chiave trovata nel file key")

            val headerBuffer = ByteArray(512)
            crypt14File.inputStream().use { it.read(headerBuffer) }

            val ivResult = try {
                parseHeader(headerBuffer)
                    ?: throw DecryptionException("parseHeader ha restituito null")
            } catch (e: DecryptionException) { throw e }

            // IV candidati dal protobuf
            val protobufIvs = if (ivResult.iv.size == 16) {
                listOf(ivResult.iv, ivResult.iv.copyOf(12))
            } else {
                listOf(ivResult.iv)
            }

            // Diagnosi brute force offset con IV dal protobuf — salva file e continua
            val diagOffset = StringBuilder()
            for (key in keyOptions) {
                val secretKey = SecretKeySpec(key, "AES")

                // IV dal protobuf (field 4)
                for (iv in protobufIvs) {
                    diagOffset.append(bruteForceOffsetDiag(crypt14File, secretKey, iv))
                }

                // IV field 5 hardcoded dal tuo header (2a 10 + 16 byte)
                val field5iv = byteArrayOf(
                    0x31, 0x15, 0x40, 0x70, 0x3f, 0x34, 0x07, 0xdf.toByte(),
                    0x88.toByte(), 0x09, 0x5a, 0xfb.toByte(), 0x73, 0x6a, 0x00, 0x77
                )
                diagOffset.append(bruteForceOffsetDiag(crypt14File, secretKey, field5iv))
                diagOffset.append(bruteForceOffsetDiag(crypt14File, secretKey, field5iv.copyOf(12)))
            }
            saveDiagnosisToFile(diagOffset.toString())

            // IV candidati letti direttamente dai byte del file crypt14
            val rawIvCandidates = mutableListOf<Pair<String, ByteArray>>()
            FileInputStream(crypt14File).use { fis ->
                val buf = ByteArray(512)
                fis.read(buf)
                for (ivOff in listOf(67, 69, 71, 51, 53, 8, 10)) {
                    if (ivOff + 16 <= buf.size) {
                        val iv16 = buf.copyOfRange(ivOff, ivOff + 16)
                        if (!iv16.all { it == 0.toByte() })
                            rawIvCandidates.add("raw_iv16@$ivOff" to iv16)
                    }
                    if (ivOff + 12 <= buf.size) {
                        val iv12 = buf.copyOfRange(ivOff, ivOff + 12)
                        if (!iv12.all { it == 0.toByte() })
                            rawIvCandidates.add("raw_iv12@$ivOff" to iv12)
                    }
                }
                for (i in 0..288) {
                    val candidate = buf.copyOfRange(i, i + 12)
                    if (!candidate.all { it == 0.toByte() })
                        rawIvCandidates.add("scan@$i" to candidate)
                }
            }

            val offsetsToTry = (listOf(
                ivResult.dataOffset,
                275, 277, 280, 270, 265, 260,
                193, 191, 192, 190,
                83, 67, 99, 114, 131, 155
            ) + (250..290 step 1).toList()).distinct()

            // FASE 1: prova IV dal protobuf
            for (key in keyOptions) {
                val secretKey = SecretKeySpec(key, "AES")
                for (iv in protobufIvs) {
                    for (dataOffset in offsetsToTry) {
                        if (quickProbe(crypt14File, secretKey, iv, dataOffset)) {
                            if (tryDecryptFull(crypt14File, secretKey, iv, dataOffset, outputFile, onProgress)) {
                                return@runCatching outputFile
                            }
                        }
                    }
                }
            }

            // FASE 2: scansione esaustiva IV
            val diagLog = StringBuilder("=== SCANSIONE ESAUSTIVA IV ===\n")
            diagLog.append("KEY: ${keyOptions.first().joinToString("") { "%02x".format(it) }}\n\n")

            for (key in keyOptions) {
                val secretKey = SecretKeySpec(key, "AES")
                for ((ivLabel, iv) in rawIvCandidates) {
                    for (dataOffset in offsetsToTry) {
                        if (quickProbeWithLog(crypt14File, secretKey, iv, dataOffset, ivLabel, diagLog)) {
                            if (tryDecryptFull(crypt14File, secretKey, iv, dataOffset, outputFile, onProgress)) {
                                saveDiagnosisToFile(diagLog.toString())
                                return@runCatching outputFile
                            }
                        }
                    }
                }
            }

            saveDiagnosisToFile(diagLog.toString())
            outputFile.takeIf { it.exists() }?.delete()
            throw DecryptionException(
                "Decifratura fallita.\n" +
                "IV protobuf: ${ivResult.iv.joinToString("") { "%02x".format(it) }}\n" +
                "dataOffset: ${ivResult.dataOffset}\n\n" +
                "Log salvato in Download/whatstat_diag_*.txt"
            )
        }
    }

    private fun bruteForceOffsetDiag(
        file: File,
        secretKey: SecretKeySpec,
        iv: ByteArray
    ): String {
        val sb = StringBuilder()
        sb.append("=== BRUTE FORCE OFFSET (AES-CTR) ===\n")
        sb.append("IV: ${iv.copyOf(12).joinToString("") { "%02x".format(it) }}\n\n")

        val ctrIv = iv.copyOf(12) + byteArrayOf(0, 0, 0, 1)

        for (offset in 50..400) {
            try {
                FileInputStream(file).use { fis ->
                    skipBytes(fis, offset)
                    val chunk = ByteArray(32)
                    val read = fis.read(chunk)
                    if (read < 16) return@use

                    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, secretKey,
                        javax.crypto.spec.IvParameterSpec(ctrIv))
                    val dec = cipher.update(chunk, 0, read) ?: return@use

                    val ascii = String(dec, 0, minOf(16, dec.size), Charsets.US_ASCII)
                        .replace(Regex("[^\\x20-\\x7E]"), ".")
                    // Logga SEMPRE, non solo SQLite
                    sb.append("off=$offset → $ascii\n")
                }
            } catch (e: Exception) {
                sb.append("off=$offset → ERR: ${e.message}\n")
            }
        }
        return sb.toString()
    }

    // ─── Probe ────────────────────────────────────────────────────────────────

    private fun quickProbe(
        file: File,
        secretKey: SecretKeySpec,
        iv: ByteArray,
        dataOffset: Int
    ): Boolean {
        return try {
            FileInputStream(file).use { fis ->
                skipBytes(fis, dataOffset)
                // Leggi solo i primi 32 byte — GCM decifra in streaming
                // anche senza auth tag usando update() invece di doFinal()
                val chunk = ByteArray(32)
                val read = fis.read(chunk)
                if (read < 16) return false

                val cipher = Cipher.getInstance(GCM_ALGO)
                cipher.init(Cipher.DECRYPT_MODE, secretKey,
                    GCMParameterSpec(AUTH_TAG_BITS, iv.copyOf(12)))
                // update() decifra senza verificare l'auth tag
                val dec = cipher.update(chunk, 0, read) ?: return false
                if (dec.size < 15) return false
                isSqlite(dec)
            }
        } catch (e: Exception) { false }
    }

    private fun quickProbeWithLog(
        file: File,
        secretKey: SecretKeySpec,
        iv: ByteArray,
        dataOffset: Int,
        ivLabel: String,
        log: StringBuilder
    ): Boolean {
        return try {
            FileInputStream(file).use { fis ->
                skipBytes(fis, dataOffset)
                val chunk = ByteArray(32)
                val read = fis.read(chunk)
                if (read < 16) return false

                val cipher = Cipher.getInstance(GCM_ALGO)
                cipher.init(Cipher.DECRYPT_MODE, secretKey,
                    GCMParameterSpec(AUTH_TAG_BITS, iv.copyOf(12)))
                val dec = cipher.update(chunk, 0, read) ?: return false

                val ascii = if (dec.size >= 16)
                    String(dec, 0, 16, Charsets.US_ASCII).replace(Regex("[^\\x20-\\x7E]"), ".")
                else "too_short"

                val found = dec.size >= 15 && isSqlite(dec)
                if (found) {
                    log.append("*** TROVATO! $ivLabel offset=$dataOffset → $ascii\n")
                    log.append("    IV: ${iv.joinToString("") { "%02x".format(it) }}\n")
                }
                found
            }
        } catch (e: Exception) { false }
    }

    // ─── Decifratura completa ─────────────────────────────────────────────────

    private fun tryDecryptFull(
        file: File,
        secretKey: SecretKeySpec,
        iv: ByteArray,
        dataOffset: Int,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): Boolean {
        return try {
            FileInputStream(file).use { fis ->
                skipBytes(fis, dataOffset)
                val ciphertext = fis.readBytes()

                // Prova 1: GCM con IV troncato a 12 byte (standard)
                // Prova 2: GCM con IV a 16 byte intero (non standard)
                val plaintext: ByteArray = tryGcmDecrypt(ciphertext, secretKey, iv.copyOf(12))
                    ?: (if (iv.size == 16) tryGcmDecrypt(ciphertext, secretKey, iv) else null)
                    ?: return false

                // Controlla SQLite diretto
                if (isSqlite(plaintext)) {
                    FileOutputStream(outputFile).use { it.write(plaintext) }
                    onProgress(1f)
                    return true
                }

                // Prova decompressione zlib
                val decompressed: ByteArray = tryInflate(plaintext) ?: return false
                if (isSqlite(decompressed)) {
                    FileOutputStream(outputFile).use { it.write(decompressed) }
                    onProgress(1f)
                    return true
                }

                false
            }
        } catch (e: Exception) {
            outputFile.takeIf { it.exists() }?.delete()
            false
        }
    }

    private fun tryGcmDecrypt(
        ciphertext: ByteArray,
        secretKey: SecretKeySpec,
        iv: ByteArray
    ): ByteArray? {
        return try {
            val cipher = Cipher.getInstance(GCM_ALGO)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(AUTH_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) { null }
    }

    private fun tryInflate(data: ByteArray): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(data)
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(BUFFER_SIZE)
            while (!inflater.finished()) {
                val count = inflater.inflate(buf)
                if (count == 0) break
                out.write(buf, 0, count)
            }
            out.toByteArray().takeIf { it.isNotEmpty() }
        } catch (e: Exception) { null }
        finally { inflater.end() }
    }

    private fun isSqlite(data: ByteArray): Boolean =
        data.size >= 15 &&
        String(data, 0, 15, Charsets.US_ASCII).startsWith("SQLite format 3")

    // ─── Utils ────────────────────────────────────────────────────────────────

    private fun skipBytes(fis: FileInputStream, count: Int) {
        var skipped = 0L
        while (skipped < count) {
            val s = fis.skip(count - skipped)
            if (s <= 0) break
            skipped += s
        }
    }

    private suspend fun saveDiagnosisToFile(content: String) {
        withContext(Dispatchers.IO) {
            try {
                val dir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                File(dir, "whatstat_diag_${System.currentTimeMillis()}.txt").writeText(content)
            } catch (e: Exception) {
                try {
                    File("/sdcard/Download/whatstat_diag.txt").writeText(content)
                } catch (e2: Exception) { /* niente da fare */ }
            }
        }
    }
}

class DecryptionException(message: String) : Exception(message)
