package com.whatstat.reborn.data.crypto

import java.io.File
import java.io.ObjectInputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Legge e decodifica la chiave AES-256 dal file key di WhatsApp.
 *
 * Supporta sia crypt14 (file key come Java Serialized Object di 131 byte)
 * che crypt15 (root key di 32 byte passata a HMAC-SHA256).
 */
@Singleton
class KeyReader @Inject constructor() {

    companion object {
        private const val CRYPT14_KEY_ARRAY_SIZE = 131
        private const val CRYPT15_KEY_ARRAY_SIZE = 32
        private const val AES_KEY_OFFSET = 99
        private const val AES_KEY_LENGTH = 32
    }

    data class KeyData(
        val aesKey: ByteArray,
        val keyType: KeyType
    ) {
        enum class KeyType { CRYPT14, CRYPT15 }
    }

    /**
     * Legge il file key di WhatsApp.
     * Se è crypt14, esegue la deserializzazione Java per estrarre la chiave.
     */
    fun readKey(keyFile: File): KeyData {
        require(keyFile.exists()) { "File key non trovato: ${keyFile.absolutePath}" }

        // WhatsApp salva la chiave come Java Serialized Object
        val keyArray: ByteArray = try {
            ObjectInputStream(keyFile.inputStream().buffered()).use { ois ->
                ois.readObject() as ByteArray
            }
        } catch (e: Exception) {
            // Se fallisce, proviamo a leggere come raw byte array (forse è già stato estratto in qualche modo)
            val rawBytes = keyFile.readBytes()
            if (rawBytes.size == CRYPT15_KEY_ARRAY_SIZE) {
                 rawBytes
            } else {
                 throw IllegalStateException(
                     "Impossibile deserializzare il file key come Java Object (size=${rawBytes.size}): ${e.message}", e
                 )
            }
        }

        return when (keyArray.size) {
            CRYPT14_KEY_ARRAY_SIZE -> {
                // Crypt14: l'array deserializzato include header e metadata.
                // La chiave AES è all'offset 99.
                val cipherVersion = keyArray.copyOfRange(0, 2)
                if (!cipherVersion.contentEquals(byteArrayOf(0x00, 0x01))) {
                    throw IllegalStateException("Versione cipher non supportata: ${cipherVersion.toHex()}")
                }

                val aesKey = keyArray.copyOfRange(AES_KEY_OFFSET, AES_KEY_OFFSET + AES_KEY_LENGTH)
                
                if (aesKey.all { it == 0.toByte() }) {
                    throw IllegalStateException("La chiave AES estratta è interamente composta da zeri")
                }
                
                KeyData(aesKey, KeyData.KeyType.CRYPT14)
            }

            CRYPT15_KEY_ARRAY_SIZE -> {
                // Crypt15: l'array contiene la root key
                if (keyArray.all { it == 0.toByte() }) {
                    throw IllegalStateException("La chiave estratta è interamente composta da zeri")
                }
                KeyData(keyArray, KeyData.KeyType.CRYPT15)
            }

            else -> throw IllegalStateException(
                "Formato key non riconosciuto: ${keyArray.size} byte " +
                "(attesi $CRYPT14_KEY_ARRAY_SIZE per crypt14 o $CRYPT15_KEY_ARRAY_SIZE per crypt15)"
            )
        }
    }

    /**
     * Deriva la chiave di cifratura per crypt15 dalla root key
     * usando due passaggi di HMAC-SHA256.
     */
    fun deriveEncryptionKey(rootKey: ByteArray): ByteArray {
        val seed = ByteArray(32) // 32 byte di zeri
        val mac1 = Mac.getInstance("HmacSHA256")
        mac1.init(SecretKeySpec(seed, "HmacSHA256"))
        val privateKey = mac1.doFinal(rootKey)

        val mac2 = Mac.getInstance("HmacSHA256")
        mac2.init(SecretKeySpec(privateKey, "HmacSHA256"))
        mac2.update("backup encryption".toByteArray(Charsets.US_ASCII))
        mac2.update(byteArrayOf(0x01))
        return mac2.doFinal()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}