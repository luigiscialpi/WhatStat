package com.whatstat.reborn.data.db.model

data class GroupParticipantHistoryEntity(
    val timestamp: Long,
    val gjid: String,
    val jid: String,
    val action: Int,
    val textData: String? = null
)

data class GroupNameChangeEntity(
    val timestamp: Long,
    val oldData: String,
    val senderJid: String,
    val newNameFallback: String? // Fallback nel caso in cui serva il testo del messaggio
)
