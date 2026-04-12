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
        private const val KEY_OFFSET = 126
        private const val KEY_LENGTH = 32
        private const val MIN_KEY_FILE_SIZE = KEY_OFFSET + KEY_LENGTH // 158 byte
    }

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

        if (key.all { it == 0.toByte() }) {
            throw IllegalStateException(
                "La chiave estratta è composta interamente da zeri. " +
                "Il file key potrebbe essere corrotto."
            )
        }

        return key
    }

    fun readKeyOptions(keyFile: File): List<ByteArray> {
        val bytes = keyFile.readBytes()
        if (bytes.size < 158) throw IllegalStateException("File key troppo corto")

        val options = mutableListOf<ByteArray>()

        // Chiave principale offset 126
        val key126 = bytes.copyOfRange(126, 158)
        if (!key126.all { it == 0.toByte() }) options.add(key126)

        // Prova anche offset 110 (32 byte prima dei 16 zeri)
        if (bytes.size >= 142) {
            val key110 = bytes.copyOfRange(110, 142)
            if (!key110.all { it == 0.toByte() }) options.add(key110)
        }

        // Prova SHA-256 della chiave principale
        val md = java.security.MessageDigest.getInstance("SHA-256")
        options.add(md.digest(key126))

        // Prova i 32 byte del googleIdSalt come chiave
        val salt = bytes.copyOfRange(30, 62)
        if (!salt.all { it == 0.toByte() }) options.add(salt)

        return options.distinctBy { it.toList() }
    }

    fun readRawKey(keyFile: File): ByteArray? {
        return try {
            val bytes = keyFile.readBytes()
            when {
                bytes.size == 32 -> bytes
                bytes.size == 64 -> bytes
                else -> null
            }
        } catch (e: Exception) { null }
    }

    fun diagnoseKeyFile(keyFile: File): String {
        val sb = StringBuilder()
        val bytes = keyFile.readBytes()

        sb.append("=== DIAGNOSI FILE KEY ===\n")
        sb.append("Nome: ${keyFile.name}\n")
        sb.append("Dimensione: ${bytes.size} byte\n\n")

        sb.append("=== HEX DUMP COMPLETO ===\n")
        bytes.toList().chunked(16).forEachIndexed { i, row ->
            val offset = "%03d".format(i * 16)
            val hex = row.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
            val ascii = row.joinToString("") {
                val c = it.toInt() and 0xFF
                if (c in 32..126) c.toChar().toString() else "."
            }
            sb.append("[$offset] $hex | $ascii\n")
        }

        sb.append("\n=== CHIAVE ALL'OFFSET 126 ===\n")
        if (bytes.size >= 158) {
            val key = bytes.copyOfRange(126, 158)
            sb.append(key.joinToString("") { "%02x".format(it.toInt() and 0xFF) })
            sb.append("\n")
        } else {
            sb.append("File troppo corto! (${bytes.size} < 158)\n")
        }

        return sb.toString()
    }

    /**
     * Verifica se il file key corrisponde al backup confrontando il googleIdSalt.
     *
     * Nel file key (Java serializzato), il googleIdSalt si trova a offset 27 (32 byte).
     * Nell'header crypt14, lo stesso salt appare nel campo protobuf 0x1a 0x20
     * che nel tuo file inizia a offset 16 (dopo: c2 01 08 00 12 4d 0a 02 00 01 12 01 32 1a 20).
     *
     * Se i due salt corrispondono, la chiave appartiene allo stesso account del backup.
     * Se non corrispondono, la chiave è di un account diverso o è stata rigenerata.
     */
    fun verifyKeyMatchesBackup(keyFile: File, crypt14Header: ByteArray): String {
        val sb = StringBuilder()
        val keyBytes = keyFile.readBytes()

        sb.append("=== VERIFICA CORRISPONDENZA KEY ↔ BACKUP ===\n\n")

        // googleIdSalt nel key file: 32 byte a offset 27
        if (keyBytes.size < 59) {
            sb.append("File key troppo corto per leggere il salt (${keyBytes.size} byte)\n")
            return sb.toString()
        }
        val saltInKey = keyBytes.copyOfRange(30, 62)
        sb.append("Salt nel key file (offset 27-58):\n")
        sb.append(saltInKey.joinToString("") { "%02x".format(it.toInt() and 0xFF) })
        sb.append("\n\n")

        // Salt nel backup: campo 0x1a 0x20 nell'header protobuf.
        // Nel tuo file: offset 16 dopo il varint iniziale c2 01.
        // Cerca il pattern 1a 20 nell'header e leggi i 32 byte successivi.
        var saltInBackup: ByteArray? = null
        var foundAt = -1
        for (i in 0 until crypt14Header.size - 33) {
            if (crypt14Header[i].toInt() and 0xFF == 0x1A &&
                crypt14Header[i + 1].toInt() and 0xFF == 0x20) {
                saltInBackup = crypt14Header.copyOfRange(i + 2, i + 34)
                foundAt = i
                break
            }
        }

        if (saltInBackup == null) {
            sb.append("Salt nel backup: NON TROVATO (pattern 1a 20 assente nell'header)\n")
            return sb.toString()
        }

        sb.append("Salt nel backup (trovato a offset $foundAt):\n")
        sb.append(saltInBackup.joinToString("") { "%02x".format(it.toInt() and 0xFF) })
        sb.append("\n\n")

        val match = saltInKey.contentEquals(saltInBackup)
        sb.append("Corrispondono: $match\n")
        if (match) {
            sb.append("✓ La chiave appartiene allo stesso account del backup.\n")
            sb.append("  Il problema è altrove (offset dati o IV errati).\n")
        } else {
            sb.append("✗ La chiave NON corrisponde al backup.\n")
            sb.append("  Il backup è stato cifrato con una chiave diversa.\n")
            sb.append("  Devi ottenere il file key originale usato al momento del backup.\n")
        }

        return sb.toString()
    }

    fun keyToHex(key: ByteArray): String =
        key.joinToString("") { "%02x".format(it) }
}