package com.whatstat.reborn.domain.model

sealed class GroupEvent {
    abstract val timestamp: Long

    data class ParticipantAction(
        override val timestamp: Long,
        val userJid: String,
        // 1 = Add, 2 = Remove, 3 = Promote, 4 = Demote, 5 = Join, 6 = Leave (da DB)
        val actionType: Int,
        // JID di chi ha eseguito l'azione (se applicabile)
        val adminJid: String? = null,
    ) : GroupEvent()

    data class NameChange(
        override val timestamp: Long,
        val oldName: String,
        val newName: String,
        // JID di chi ha cambiato il nome
        val userJid: String,
    ) : GroupEvent()

    data class DescriptionChange(
        override val timestamp: Long,
        val userJid: String,
        val newDescription: String,
    ) : GroupEvent()
}

data class GroupEventUiModel(
    val event: GroupEvent,
    val userJid: String,
    val userName: String,
    val isMe: Boolean,
    val isLeftAligned: Boolean,
    val primaryText: String,
    val secondaryText: String?,
    val avatarPath: String?,
    val formattedDate: String,
    val formattedTime: String,
    val isDescriptionChange: Boolean = false,
)
