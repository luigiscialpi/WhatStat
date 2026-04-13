package com.whatstat.reborn.data.crypto

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import javax.inject.Inject

class WaCryptToolsDecryptor @Inject constructor(
    private val keyReader: com.whatstat.reborn.data.crypto.KeyReader
) {

    companion object {
        private const val TAG = "WaCryptToolsDecryptor"
        private const val PROTO_MAGIC: Byte = 0x0A
        private const val BUFFER_SIZE = 16384
        private val IV_LENGTH = 16
        private val AUTH_TAG_LENGTH = 16
    }

    suspend fun decrypt(
        crypt14File: File,
        keyFile: File,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val key = keyReader.readKey(keyFile)
            val secretKey = SecretKeySpec(key, "AES")

            crypt14File.inputStream().use { inputStream ->
                val data = inputStream.readBytes()

                val (iv, encryptedData) = parseProtobufHeader(data)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(AUTH_TAG_LENGTH * 8, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                val decryptedData = cipher.doFinal(encryptedData)

                val inflater = Inflater()
                val decompressStream = InflaterInputStream(decryptedData.inputStream(), inflater)
                val decompressedData = decompressStream.readBytes()

                outputFile.writeBytes(decompressedData)
            }

            onProgress(1f)

            outputFile
        }
    }

    private fun parseProtobufHeader(data: ByteArray): Pair<ByteArray, ByteArray> {
        var offset = data.indexOf(PROTO_MAGIC)
        if (offset == -1) {
            throw IllegalStateException("Header protobuf non trovato")
        }

        offset++

        var protoLength = 0
        var shift = 0
        while (offset < data.size) {
            val byte = data[offset].toInt()
            protoLength = protoLength or ((byte and 0x7F) shl shift)
            if (byte and 0x80 == 0) break
            shift += 7
            offset++
        }
        offset++

        val protoEnd = offset + protoLength

        if (protoEnd > data.size) {
            throw IllegalStateException("Protobuf troncato")
        }

        val c14CipherStart = findField(data, offset, protoEnd, 2)
        if (c14CipherStart == -1) {
            throw IllegalStateException("Campo C14_cipher non trovato")
        }

        val ivStart = findField(data, c14CipherStart, protoEnd, 5)
        if (ivStart == -1) {
            throw IllegalStateException("Campo IV non trovato")
        }

        var ivOffset = ivStart
        var ivLength = 0
        shift = 0
        while (ivOffset < protoEnd) {
            val byte = data[ivOffset].toInt()
            ivLength = ivLength or ((byte and 0x7F) shl shift)
            if (byte and 0x80 == 0) break
            shift += 7
            ivOffset++
        }
        ivOffset++

        if (ivOffset + ivLength > protoEnd || ivLength != IV_LENGTH) {
            throw IllegalStateException("IV non valido: lunghezza $ivLength")
        }
        val iv = data.sliceArray(ivOffset until ivOffset + ivLength)

        val encryptedData = data.sliceArray(protoEnd until data.size - AUTH_TAG_LENGTH - 16)

        return Pair(iv, encryptedData)
    }

    private fun findField(data: ByteArray, start: Int, end: Int, fieldNumber: Int): Int {
        var offset = start
        while (offset < end) {
            val tag = data[offset].toInt()
            val fieldNum = tag and 0x7F
            val wireType = tag shr 3

            offset++
            var length = 0
            var shift = 0
            while (offset < end) {
                val byte = data[offset].toInt()
                length = length or ((byte and 0x7F) shl shift)
                if (byte and 0x80 == 0) break
                shift += 7
                offset++
            }
            offset += length

            if (fieldNum == fieldNumber) {
                return offset - length
            }
        }
        return -1
    }
}