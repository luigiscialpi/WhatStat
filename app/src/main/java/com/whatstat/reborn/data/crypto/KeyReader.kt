package com.whatstat.reborn.data.crypto

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Legge e decodifica la chiave AES-256 dal file key di WhatsApp.
 *
 * Il file key è un blob binario strutturato. La chiave AES-256 effettiva
 * (32 byte) si trova all'offset 126. Questo offset è stato reverse-engineered
 * dalla community (ElDavoo/wa-crypt-tools).
 */
@Singleton
class KeyReader @Inject constructor() {

    companion object {
        /**
         * Offset della chiave AES-256 nel file key.
         * La struttura del file key:
         *   [0..125]   : metadata (account info, backup token, etc.)
         *   [126..157] : 32 byte AES-256 key
         */
        private const val KEY_OFFSET = 126
        private const val KEY_LENGTH = 32
        private const val MIN_KEY_FILE_SIZE = KEY_OFFSET + KEY_LENGTH // 158 byte
    }

    /**
     * Legge e restituisce la chiave AES-256 raw dal file key di WhatsApp.
     *
     * @param keyFile il file key estratto da /data/data/com.whatsapp/files/key
     * @return ByteArray di 32 byte contenente la chiave AES-256
     * @throws IllegalStateException se il file è malformato o troppo piccolo
     */
    fun readKey(keyFile: File): ByteArray {
        require(keyFile.exists()) { "File key non trovato: ${keyFile.absolutePath}" }

        val keyFileBytes = keyFile.readBytes()

        if (keyFileBytes.size < MIN_KEY_FILE_SIZE) {
            throw IllegalStateException(
                "File key malformato: attesi almeno $MIN_KEY_FILE_SIZE byte, " +
                "trovati ${keyFileBytes.size}. Il file potrebbe essere corrotto " +
                "o appartenere a una versione non supportata di WhatsApp."
            )
        }

        val key = keyFileBytes.copyOfRange(KEY_OFFSET, KEY_OFFSET + KEY_LENGTH)

        // Verifica che la chiave non sia tutta zeri (indicherebbe corruzione)
        if (key.all { it == 0.toByte() }) {
            throw IllegalStateException(
                "La chiave estratta è composta interamente da zeri. " +
                "Il file key potrebbe essere corrotto."
            )
        }

        return key
    }

    /**
     * Restituisce la chiave come stringa esadecimale.
     * Utile per debug — MAI loggare in produzione.
     */
    fun keyToHex(key: ByteArray): String =
        key.joinToString("") { "%02x".format(it) }
}
