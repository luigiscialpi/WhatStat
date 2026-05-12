package com.whatstat.reborn.data.db.dao

import android.database.Cursor
import com.whatstat.reborn.data.db.DatabaseProvider
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
    private val database: DatabaseProvider
) {

    /** Conta i contatti nel database wa.db. */
    fun getContactCount(): Int {
        return try {
            val cursor = database.getWaDb()?.rawQuery(
                "SELECT COUNT(*) FROM wa_contacts WHERE display_name IS NOT NULL", null
            ) ?: return 0
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
        // 1. Try wa_contacts (display_name)
        val fromWa = try {
            val cursor = database.getWaDb()?.rawQuery(
                """SELECT COALESCE(display_name, given_name, number) AS name 
                   FROM wa_contacts WHERE jid = ? LIMIT 1""",
                arrayOf(jid)
            )
            cursor?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (_: Exception) {
            null
        }
        if (fromWa != null) return fromWa

        // 2. Fallback to jid table (user field)
        return try {
            val cursor = database.getMsgDb().rawQuery(
                "SELECT user FROM jid WHERE raw_string = ? LIMIT 1",
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
                val cursor = database.getWaDb()?.rawQuery(
                    """SELECT jid, COALESCE(display_name, given_name, number) AS name 
                       FROM wa_contacts WHERE jid IN ($placeholders)""",
                    batch.toTypedArray()
                )
                cursor?.use {
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
        
        // Resolve @lid JIDs via jid_map → jid → wa_contacts
        val unresolvedLid = jids.filter { it !in result && it.endsWith("@lid") }
        if (unresolvedLid.isNotEmpty()) {
            val lidToReal = resolveLidToRealJid(unresolvedLid)
            // For each @lid, use the name already resolved for the real JID, or resolve it
            val needResolve = lidToReal.values.filter { it !in result }
            if (needResolve.isNotEmpty()) {
                val realPlaceholders = needResolve.joinToString(",") { "?" }
                try {
                    val waCursor = database.getWaDb()?.rawQuery(
                        """SELECT jid, COALESCE(display_name, given_name, number) AS name 
                           FROM wa_contacts WHERE jid IN ($realPlaceholders)""",
                        needResolve.toTypedArray()
                    )
                    waCursor?.use {
                        while (it.moveToNext()) {
                            val jid = it.getString(0) ?: continue
                            val name = it.getString(1) ?: continue
                            result[jid] = name
                        }
                    }
                } catch (_: Exception) { }
            }
            
            // Map @lid → name (from resolved real JID or phone fallback)
            lidToReal.forEach { (lidJid, realJid) ->
                val name = result[realJid]
                if (name != null) {
                    result[lidJid] = name
                } else {
                    result[lidJid] = realJid.substringBefore("@")
                }
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
                    // In Lite mode, preferiamo il valore della tabella jid se wa_contacts non ha dati nuovi
                    result[jid] = user
                }
            }
        } catch (_: Exception) {
            // Fallback to extracting phone number from JID
            unresolved.forEach { jid ->
                val phone = jid.substringBefore("@")
                if (phone.length >= 6) {
                    result[jid] = "📱$phone"
                }
            }
        }
        
        return result
    }

    /**
     * Mappa @lid JID ai corrispondenti @s.whatsapp.net JID usando la tabella jid_map.
     * @return Map<lidJid, realJid> es. "137705358913673@lid" → "393204277608@s.whatsapp.net"
     */
    fun resolveLidToRealJid(lidJids: List<String>): Map<String, String> {
        if (lidJids.isEmpty()) return emptyMap()
        val lidToReal = mutableMapOf<String, String>()
        try {
            val placeholders = lidJids.joinToString(",") { "?" }
            val cursor = database.getMsgDb().rawQuery(
                """SELECT lid_jid.raw_string AS lid_raw, real_jid.raw_string AS real_raw
                   FROM jid lid_jid
                   INNER JOIN jid_map ON jid_map.lid_row_id = lid_jid._id
                   INNER JOIN jid real_jid ON real_jid._id = jid_map.jid_row_id
                   WHERE lid_jid.raw_string IN ($placeholders)""",
                lidJids.toTypedArray()
            )
            cursor.use {
                while (it.moveToNext()) {
                    val lidRaw = it.getString(0) ?: continue
                    val realRaw = it.getString(1) ?: continue
                    lidToReal[lidRaw] = realRaw
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WhatStat", "Error resolving @lid JIDs", e)
        }
        return lidToReal
    }
}
