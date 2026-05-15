package com.whatstat.data.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Crypt14Decryptor
    @Inject
    constructor(
        private val keyReader: KeyReader,
    ) {
        companion object {
            private const val CTR_ALGO = "AES/CTR/NoPadding"
            private const val CIPHER_ALGO = "AES/GCM/NoPadding"
            private const val IV_LENGTH = 16
            private const val AUTH_TAG_BITS = 128
            private const val FOOTER_SIZE = 32 // auth_tag (16) + md5_checksum (16)
            private const val BUFFER_SIZE = 65536
        }

        suspend fun decrypt(
            crypt14File: File,
            keyFile: File,
            outputFile: File,
            onProgress: (Float) -> Unit = {},
        ): Result<File> =
            withContext(Dispatchers.IO) {
                runCatching {
                    onProgress(0.05f)

                    // 1. Carica e deserializza la chiave
                    val keyData = keyReader.readKey(keyFile)
                    val aesKey: ByteArray =
                        when (keyData.keyType) {
                            KeyReader.KeyData.KeyType.CRYPT14 -> keyData.aesKey
                            KeyReader.KeyData.KeyType.CRYPT15 -> keyReader.deriveEncryptionKey(keyData.aesKey)
                        }
                    val secretKey = SecretKeySpec(aesKey, "AES")
                    onProgress(0.1f)

                    // 2. Leggi SOLO il file parzialmente per identificare l'header protobuf
                    val fileLength = crypt14File.length()
                    val headerBuffer = ByteArray(4096)
                    var bytesReadInHeader = 0
                    crypt14File.inputStream().use { fis ->
                        bytesReadInHeader = fis.read(headerBuffer)
                    }
                    if (bytesReadInHeader < 50) {
                        throw DecryptionException("File crypt14 troppo corto!")
                    }
                    val headerResult = parseProtobufHeader(headerBuffer.copyOf(bytesReadInHeader))
                    onProgress(0.2f)

                    // 3. Estrai limiti dati
                    val debugLog = java.lang.StringBuilder("--- VERBOSE DEBUG ---\n")

                    val dataStart = headerResult.dataOffset
                    val dataEnd = fileLength - FOOTER_SIZE
                    val dataLength = dataEnd - dataStart
                    if (dataLength <= 0) {
                        throw DecryptionException("File cifrato troppo piccolo o corrotto (size: $fileLength, dataStart: $dataStart)")
                    }

                    debugLog.append("Len: $fileLength\n")
                    debugLog.append("Start: $dataStart | End: $dataEnd | Len: $dataLength\n")
                    debugLog.append("Protobuf IV: ${headerResult.iv.joinToString("") { "%02X".format(it) }}\n")

                    val aesEcb = Cipher.getInstance("AES/ECB/NoPadding")
                    aesEcb.init(Cipher.ENCRYPT_MODE, secretKey)
                    val h = aesEcb.doFinal(ByteArray(16))

                    val j0 = ByteArray(16)
                    for (i in 0 until 16) j0[i] = (j0[i].toInt() xor headerResult.iv[i].toInt()).toByte()
                    gfMul(j0, h)

                    val lenBlock = ByteArray(16)
                    lenBlock[15] = 0x80.toByte()
                    for (i in 0 until 16) j0[i] = (j0[i].toInt() xor lenBlock[i].toInt()).toByte()
                    gfMul(j0, h)

                    debugLog.append("J0 (GHASH): ${j0.joinToString("") { "%02X".format(it) }}\n")

                    val ctrIv = j0.copyOf()
                    var carry = 1
                    for (i in 15 downTo 12) {
                        val sum = (ctrIv[i].toInt() and 0xFF) + carry
                        ctrIv[i] = sum.toByte()
                        carry = sum ushr 8
                        if (carry == 0) break
                    }

                    debugLog.append("CTR IV (+1): ${ctrIv.joinToString("") { "%02X".format(it) }}\n")

                    val cipher = Cipher.getInstance(CTR_ALGO)
                    cipher.init(
                        Cipher.DECRYPT_MODE,
                        secretKey,
                        javax.crypto.spec.IvParameterSpec(ctrIv),
                    )
                    onProgress(0.25f)

                    var bytesProcessed = 0L
                    val progressUpdateInterval = maxOf(1L, dataLength / 100)

                    crypt14File.inputStream().use { fis ->
                        var skipped = 0L
                        while (skipped < dataStart) {
                            val s = fis.skip(dataStart - skipped)
                            if (s <= 0L) {
                                if (fis.read() == -1) break
                                skipped++
                            } else {
                                skipped += s
                            }
                        }

                        val limitedFis =
                            object : java.io.FilterInputStream(fis) {
                                var readCount = 0L

                                override fun read(): Int {
                                    if (readCount >= dataLength) return -1
                                    val b = super.read()
                                    if (b != -1) readCount++
                                    return b
                                }

                                override fun read(
                                    b: ByteArray,
                                    off: Int,
                                    len: Int,
                                ): Int {
                                    if (readCount >= dataLength) return -1
                                    val limitLen = minOf(len.toLong(), dataLength - readCount).toInt()
                                    val r = super.read(b, off, limitLen)
                                    if (r != -1) readCount += r
                                    return r
                                }
                            }

                        val cis = javax.crypto.CipherInputStream(limitedFis, cipher)

                        val peekBuffer = ByteArray(32)
                        var peekRead = 0
                        while (peekRead < 32) {
                            val r = cis.read(peekBuffer, peekRead, 32 - peekRead)
                            if (r == -1) break
                            peekRead += r
                        }

                        debugLog.append(
                            "PEEK Decrtpted (32B): ${peekBuffer.copyOfRange(0, peekRead).joinToString("") { "%02X".format(it) }}\n",
                        )

                        val pushbackStream = java.io.SequenceInputStream(java.io.ByteArrayInputStream(peekBuffer, 0, peekRead), cis)

                        val inflater = Inflater()
                        val iis = java.util.zip.InflaterInputStream(pushbackStream, inflater, BUFFER_SIZE)

                        outputFile.outputStream().use { fos ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var count: Int
                            try {
                                while (iis.read(buffer).also { count = it } != -1) {
                                    fos.write(buffer, 0, count)
                                    val currentRead = limitedFis.readCount
                                    if (currentRead - bytesProcessed > progressUpdateInterval) {
                                        bytesProcessed = currentRead
                                        val prog = 0.25f + (0.75f * (currentRead.toFloat() / dataLength.toFloat()))
                                        onProgress(prog)
                                    }
                                }
                            } catch (e: Exception) {
                                debugLog.append("ERR a byte $bytesProcessed: ${e.message}")
                                throw DecryptionException(debugLog.toString())
                            }
                        }
                    }

                    onProgress(1f)

                    // 6. Verifica SQLite dal nuovo file creato
                    val checkBuffer = ByteArray(16)
                    outputFile.inputStream().use { it.read(checkBuffer) }
                    val magic = String(checkBuffer, 0, 15, Charsets.US_ASCII)
                    if (magic != "SQLite format 3") {
                        outputFile.delete()
                        throw DecryptionException(
                            "Output file non ha intestazione database valida: apparentemente decompressione ha prodotto roba corrotta.",
                        )
                    }

                    outputFile
                }
            }

        /**
         * Risultato del parsing dell'header protobuf.
         */
        data class HeaderParseResult(
            val iv: ByteArray,
            val dataOffset: Int,
        )

        /**
         * Parsa l'header protobuf di un file .crypt14/.crypt15.
         * Restituisce l'IV estratto e l'offset da cui iniziano i dati veri e propri.
         */
        private fun parseProtobufHeader(data: ByteArray): HeaderParseResult {
            var offset = 0

            // Byte 0: dimensione del messaggio protobuf (1 byte)
            val protobufSize = data[offset].toInt() and 0xFF
            offset++

            // Byte 1: verifica se c'e il feature flag
            if (data[offset].toInt() and 0xFF == 0x01) {
                offset++ // Consuma il feature flag
            }

            // Leggi il messaggio protobuf raw
            val protobufRaw = data.copyOfRange(offset, offset + protobufSize)
            offset += protobufSize

            val iv =
                extractIvFromProtobuf(protobufRaw)
                    ?: throw DecryptionException("Impossibile estrarre IV dal header protobuf")

            if (iv.size != IV_LENGTH) {
                throw DecryptionException("IV ha lunghezza errata: trovati ${iv.size} byte, attesi $IV_LENGTH")
            }

            return HeaderParseResult(iv = iv, dataOffset = offset)
        }

        /**
         * Estrae il campo IV (Initialization Vector) dal messaggio protobuf BackupPrefix.
         */
        private fun extractIvFromProtobuf(data: ByteArray): ByteArray? {
            var pos = 0
            while (pos < data.size) {
                val tag = readVarint(data, pos)
                pos = tag.second
                val fieldNumber = (tag.first ushr 3).toInt()
                val wireType = (tag.first and 0x07).toInt()

                when (wireType) {
                    0 -> {
                        val v = readVarint(data, pos)
                        pos = v.second
                    }
                    2 -> {
                        val lenResult = readVarint(data, pos)
                        val len = lenResult.first.toInt()
                        pos = lenResult.second
                        val fieldData = data.copyOfRange(pos, pos + len)
                        pos += len

                        when (fieldNumber) {
                            2 -> {
                                // c14_cipher: field 5 è l'IV
                                val iv = extractFieldFromSubmessage(fieldData, targetField = 5)
                                if (iv != null) return iv
                            }
                            3 -> {
                                // c15_iv: field 1 è l'IV
                                val iv = extractFieldFromSubmessage(fieldData, targetField = 1)
                                if (iv != null) return iv
                            }
                        }
                    }
                    else -> break
                }
            }
            return null
        }

        /**
         * Parsing minimale dei sottomessaggi per estrarre bytes (length-delimited).
         */
        private fun extractFieldFromSubmessage(
            data: ByteArray,
            targetField: Int,
        ): ByteArray? {
            var pos = 0
            while (pos < data.size) {
                val tag = readVarint(data, pos)
                pos = tag.second
                val fieldNumber = (tag.first ushr 3).toInt()
                val wireType = (tag.first and 0x07).toInt()

                when (wireType) {
                    0 -> {
                        val v = readVarint(data, pos)
                        pos = v.second
                    }
                    2 -> {
                        val lenResult = readVarint(data, pos)
                        val len = lenResult.first.toInt()
                        pos = lenResult.second
                        val fieldData = data.copyOfRange(pos, pos + len)
                        pos += len

                        if (fieldNumber == targetField) return fieldData
                    }
                    else -> break
                }
            }
            return null
        }

        /**
         * Utilità per il Protobuf wire format (leggere i varint, numeri interi a lunghezza variabile).
         */
        private fun readVarint(
            data: ByteArray,
            startPos: Int,
        ): Pair<Long, Int> {
            var result = 0L
            var shift = 0
            var pos = startPos
            while (pos < data.size) {
                val b = data[pos].toInt() and 0xFF
                result = result or ((b.toLong() and 0x7F) shl shift)
                pos++
                if (b and 0x80 == 0) break
                shift += 7
            }
            return Pair(result, pos)
        }

        private fun getLong(
            bytes: ByteArray,
            off: Int,
        ): Long {
            var result = 0L
            for (i in 0 until 8) {
                result = (result shl 8) or (bytes[off + i].toLong() and 0xFFL)
            }
            return result
        }

        private fun putLong(
            value: Long,
            bytes: ByteArray,
            off: Int,
        ) {
            for (i in 7 downTo 0) {
                bytes[off + i] = (value ushr ((7 - i) * 8)).toByte()
            }
        }

        private fun gfMul(
            x: ByteArray,
            y: ByteArray,
        ) {
            var z0 = 0L
            var z1 = 0L
            var v0 = getLong(y, 0)
            var v1 = getLong(y, 8)

            for (i in 0 until 16) {
                val b = x[i].toInt() and 0xFF
                for (j in 7 downTo 0) {
                    if ((b and (1 shl j)) != 0) {
                        z0 = z0 xor v0
                        z1 = z1 xor v1
                    }
                    val msb = v1 and 1L
                    v1 = (v0 shl 63) or (v1 ushr 1)
                    v0 = v0 ushr 1
                    if (msb != 0L) {
                        v0 = v0 xor -0x1f00000000000000L // 0xe100000000000000L in signed format
                    }
                }
            }
            putLong(z0, x, 0)
            putLong(z1, x, 8)
        }
    }

class DecryptionException(message: String) : Exception(message)
