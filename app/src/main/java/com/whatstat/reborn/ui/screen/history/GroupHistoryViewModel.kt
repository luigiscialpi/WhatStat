package com.whatstat.reborn.ui.screen.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.reborn.data.repository.AnalyticsRepository
import com.whatstat.reborn.data.root.FileExtractor
import com.whatstat.reborn.domain.model.GroupEvent
import com.whatstat.reborn.domain.model.GroupEventUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class GroupHistoryViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val _chatContextName = MutableStateFlow<String?>(null)
    val chatContextName: StateFlow<String?> = _chatContextName.asStateFlow()

    private val _chatContextAvatarPath = MutableStateFlow<String?>(null)
    val chatContextAvatarPath: StateFlow<String?> = _chatContextAvatarPath.asStateFlow()

    private val avatarDir: File by lazy { File(context.filesDir, FileExtractor.AVATAR_DIR_DEST) }

    private val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun loadHistory(chatId: Long) {
        viewModelScope.launch {
            _uiState.value = HistoryUiState.Loading

            try {
                val chatInfo = analyticsRepository.getChatContextInfo(chatId)
                _chatContextName.value = chatInfo.displayName
                _chatContextAvatarPath.value = getAvatarPath(chatInfo.jid)

                val events = analyticsRepository.getGroupHistory(chatId)

                // Ricava il JID dell'utente corrente (auto-discovery)
                val myJid = analyticsRepository.getMyJid()

                // Raccogli tutti i JID unici per risolverli in batch
                val jidsToResolve = mutableSetOf<String>()
                events.forEach { event ->
                    when (event) {
                        is GroupEvent.ParticipantAction -> {
                            jidsToResolve.add(event.userJid)
                            event.adminJid?.let { jidsToResolve.add(it) }
                        }
                        is GroupEvent.NameChange -> {
                            jidsToResolve.add(event.userJid)
                        }
                    }
                }

                val resolvedNames = analyticsRepository.resolveContactNames(jidsToResolve.toList())

                val uiModels = events.map { event ->
                    val date = Date(event.timestamp)
                    val formattedDate = dateFormatter.format(date)
                    val formattedTime = timeFormatter.format(date)

                    when (event) {
                        is GroupEvent.ParticipantAction -> {
                            val isMe = event.userJid == "me@s.whatsapp.net" || event.userJid == myJid
                            val rawName = resolvedNames[event.userJid]
                            val userName = when {
                                isMe -> "Tu"
                                rawName != null -> rawName
                                else -> "Sconosciuto"
                            }
                            
                            val primaryText = when (event.actionType) {
                                1 -> if (isMe) "Sei stato aggiunto" else "$userName è stato aggiunto"
                                2 -> if (isMe) "Sei stato rimosso" else "$userName è stato rimosso"
                                3 -> if (isMe) "Ora sei un amministratore" else "$userName è ora un amministratore"
                                4 -> if (isMe) "Non sei più un amministratore" else "$userName non è più un amministratore"
                                5 -> if (isMe) "Sei entrato nel gruppo" else "$userName è entrato nel gruppo"
                                6 -> if (isMe) "Hai abbandonato il gruppo" else "$userName ha abbandonato il gruppo"
                                else -> "Azione sconosciuta per $userName"
                            }
                            GroupEventUiModel(
                                event = event,
                                userName = userName,
                                isMe = isMe,
                                primaryText = primaryText,
                                secondaryText = null, // Potremmo aggiungere info su chi ha fatto l'azione se adminJid fosse disponibile
                                avatarPath = if (isMe) getAvatarPath("me@s.whatsapp.net") else getAvatarPath(event.userJid),
                                formattedDate = formattedDate,
                                formattedTime = formattedTime
                            )
                        }
                        is GroupEvent.NameChange -> {
                            val isMe = event.userJid == "me@s.whatsapp.net" || event.userJid == myJid
                            val rawName = resolvedNames[event.userJid]
                            val userName = when {
                                isMe -> "Tu"
                                rawName != null -> rawName
                                else -> "Sconosciuto"
                            }
                            // primaryText vuoto, secondaryText contiene solo il nuovo nome
                            GroupEventUiModel(
                                event = event,
                                userName = userName,
                                isMe = isMe,
                                primaryText = "", // sarà ignorato se showHeader && isNameChange
                                secondaryText = "${event.newName}",
                                avatarPath = if (isMe) getAvatarPath("me@s.whatsapp.net") else getAvatarPath(event.userJid),
                                formattedDate = formattedDate,
                                formattedTime = formattedTime
                            )
                        }
                    }
                }

                _uiState.value = HistoryUiState.Success(uiModels)

            } catch (e: Exception) {
                _uiState.value = HistoryUiState.Error(e.message ?: "Errore sconosciuto")
            }
        }
    }

    private fun getAvatarPath(jid: String): String? {
        if (!avatarDir.exists()) return null
        if (jid == "me@s.whatsapp.net") {
            val me = File(avatarDir, "me.jpg")
            return if (me.exists()) me.absolutePath else null
        }
        val f = File(avatarDir, "$jid.jpg")
        return if (f.exists()) f.absolutePath else null
    }

    sealed class HistoryUiState {
        object Loading : HistoryUiState()
        data class Success(val events: List<GroupEventUiModel>) : HistoryUiState()
        data class Error(val message: String) : HistoryUiState()
    }
}
