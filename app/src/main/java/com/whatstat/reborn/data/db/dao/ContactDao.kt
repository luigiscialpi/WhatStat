package com.whatstat.reborn.data.db.dao

import com.whatstat.reborn.data.db.WhatsAppDatabase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DAO per i contatti WhatsApp.
 * Legge dalla tabella `wa_contacts` del database wa.db.
 *
 * wa.db contiene i nomi dei contatti come appaiono in WhatsApp,
 * che possono differire da quelli nella cache di msgstore.db.
 */
@Singleton
class ContactDao @Inject constructor(
    private val database: WhatsAppDatabase
) {

    /** Conta i contatti nel database wa.db. */
    fun getContactCount(): Int {
        return try {
            val cursor = database.getWaDb().rawQuery(
                "SELECT COUNT(*) FROM wa_contacts WHERE display_name IS NOT NULL", null
            )
            cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
        } catch (_: Exception) {
            0 // wa.db potrebbe avere uno schema diverso
        }
    }

    /**
     * Risolve un JID nel nome del contatto.
     * Cerca prima in wa.db, poi ritorna il numero come fallback.
     *
     * @param jid es. "3391234567@s.whatsapp.net"
     * @return nome del contatto o numero di telefono
     */
    fun resolveContactName(jid: String): String? {
        return try {
            val cursor = database.getWaDb().rawQuery(
                """SELECT COALESCE(display_name, given_name, number) AS name 
                   FROM wa_contacts WHERE jid = ? LIMIT 1""",
                arrayOf(jid)
            )
            cursor.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Risolve una lista di JID nei nomi dei contatti.
     * Preferisce wa_contacts se disponibile, altrimenti usa jid.user.
     * Restituisce una mappa JID → nome.
     */
    fun resolveContactNames(jids: List<String>): Map<String, String> {
        if (jids.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, String>()
        
        // Try wa_contacts first
        jids.chunked(50).forEach { batch ->
            val placeholders = batch.joinToString(",") { "?" }
            try {
                val cursor = database.getWaDb().rawQuery(
                    """SELECT jid, COALESCE(display_name, given_name, number) AS name 
                       FROM wa_contacts WHERE jid IN ($placeholders)""",
                    batch.toTypedArray()
                )
                cursor.use {
                    while (it.moveToNext()) {
                        val jid = it.getString(0) ?: continue
                        val name = it.getString(1) ?: continue
                        result[jid] = name
                    }
                }
            } catch (_: Exception) {
                // wa_contacts not available, fall through to jid table
            }
        }
        
        // Fallback: resolve remaining JIDs using jid table
        val unresolved = jids.filter { it !in result }
        if (unresolved.isEmpty()) return result
        
        val placeholders = unresolved.joinToString(",") { "?" }
        try {
            val cursor = database.getMsgDb().rawQuery(
                """SELECT raw_string, user FROM jid WHERE raw_string IN ($placeholders)""",
                unresolved.toTypedArray()
            )
            cursor.use {
                while (it.moveToNext()) {
                    val jid = it.getString(0) ?: continue
                    val user = it.getString(1) ?: continue
                    if (jid !in result) {
                        result[jid] = user
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback to extracting phone number from JID
            unresolved.forEach { jid ->
                val phone = jid.substringBefore("@")
                if (phone.length >= 6) {
                    result[jid] = phone
                }
            }
        }
        
        return result
    }
}
