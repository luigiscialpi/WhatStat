package com.whatstat.reborn.ui.screen.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.reborn.data.db.dao.ChatDao
import com.whatstat.reborn.data.db.dao.ContactDao
import com.whatstat.reborn.data.db.model.ChatListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatDao: ChatDao,
    private val contactDao: ContactDao
) : ViewModel() {

    private val _allChats = MutableStateFlow<List<ChatListItem>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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
                    if (resolvedName != null && !chat.isGroup) {
                        chat.copy(displayName = resolvedName)
                    } else if (resolvedName != null && chat.displayName.contains("@")) {
                        chat.copy(displayName = resolvedName)
                    } else {
                        chat
                    }
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
}
