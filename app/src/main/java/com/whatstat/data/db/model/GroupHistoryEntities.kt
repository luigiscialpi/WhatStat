package com.whatstat.data.db.model

import kotlinx.serialization.Serializable

@Serializable
data class GroupParticipantHistoryEntity(
    val timestamp: Long,
    val gjid: String,
    val jid: String,
    val action: Int,
    val textData: String? = null,
)

@Serializable
data class GroupNameChangeEntity(
    val timestamp: Long,
    val oldData: String,
    val senderJid: String,
    val newNameFallback: String?,
    // Fallback nel caso in cui serva il testo del messaggio
)
