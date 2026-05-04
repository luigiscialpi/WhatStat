package com.whatstat.reborn.ui.screen.chatlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.reborn.data.db.dao.ChatDao
import com.whatstat.reborn.data.db.dao.ContactDao
import com.whatstat.reborn.data.db.model.ChatListItem
import com.whatstat.reborn.data.lite.LiteDbWriter
import com.whatstat.reborn.data.repository.AnalyticsRepository
import com.whatstat.reborn.data.root.FileExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatDao: ChatDao,
    private val contactDao: ContactDao,
    private val liteDbWriter: LiteDbWriter,
    private val analyticsRepository: AnalyticsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val isLiteMode: StateFlow<Boolean> = analyticsRepository.isLiteMode

    private val _allChats = MutableStateFlow<List<ChatListItem>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Directory dove sono salvati gli avatar. */
    private val avatarDir: File by lazy {
        File(context.filesDir, FileExtractor.AVATAR_DIR_DEST)
    }

    /** Estrae il percorso dell'avatar per un JID. */
    private fun getAvatarPath(jid: String): String? {
        if (!avatarDir.exists()) return null
        if (jid == "me@s.whatsapp.net") {
            val me = File(avatarDir, "me.jpg")
            return if (me.exists()) me.absolutePath else null
        }
        val avatarFile = File(avatarDir, jid + ".jpg")
        return if (avatarFile.exists()) avatarFile.absolutePath else null
    }

    /** Lista filtrata per la search bar. */
    val filteredChats: StateFlow<List<ChatListItem>> = combine(
        _allChats,
        _searchQuery
    ) { chats, query ->
        if (query.isBlank()) chats
        else chats.filter {
            it.displayName.contains(query, ignoreCase = true) ||
            it.jid.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        loadChats()
    }

    private fun loadChats() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val chats = chatDao.getChatList()

                // Arricchisci i nomi con wa.db
                val jids = chats.map { it.jid }
                val contactNames = contactDao.resolveContactNames(jids)

                val enrichedChats = chats.map { chat ->
                    val resolvedName = contactNames[chat.jid]
                    val displayName = when {
                        resolvedName != null && !chat.isGroup -> resolvedName
                        resolvedName != null && chat.displayName.contains("@") -> resolvedName
                        else -> chat.displayName
                    }
                    val avatarPath = getAvatarPath(chat.jid)
                    chat.copy(
                        displayName = displayName,
                        avatarPath = avatarPath
                    )
                }

                _allChats.value = enrichedChats
            } catch (e: Exception) {
                _allChats.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun reloadChats() {
        loadChats()
    }

    fun deleteChat(chatId: Long) {
        viewModelScope.launch {
            liteDbWriter.deleteChat(chatId)
            loadChats()
        }
    }
}
