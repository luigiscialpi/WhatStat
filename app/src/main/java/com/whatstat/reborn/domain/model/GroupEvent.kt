package com.whatstat.reborn.domain.model

sealed class GroupEvent {
    abstract val timestamp: Long

    data class ParticipantAction(
        override val timestamp: Long,
        val userJid: String,
        val actionType: Int, // 1 = Add, 2 = Remove, 3 = Promote, 4 = Demote, 5 = Join, 6 = Leave (da DB)
        val adminJid: String? = null // JID di chi ha eseguito l'azione (se applicabile)
    ) : GroupEvent()

    data class NameChange(
        override val timestamp: Long,
        val oldName: String,
        val newName: String,
        val userJid: String // JID di chi ha cambiato il nome
    ) : GroupEvent()
}

data class GroupEventUiModel(
    val event: GroupEvent,
    val userName: String,
    val isMe: Boolean,
    val primaryText: String,
    val secondaryText: String?,
    val avatarPath: String?,
    val formattedDate: String,
    val formattedTime: String
)
