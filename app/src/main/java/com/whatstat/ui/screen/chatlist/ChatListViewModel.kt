package com.whatstat.ui.screen.chatlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.data.cache.CacheManager
import com.whatstat.data.db.dao.ChatDao
import com.whatstat.data.db.dao.ContactDao
import com.whatstat.data.db.model.ChatListItem
import com.whatstat.data.lite.LiteDbWriter
import com.whatstat.data.repository.AnalyticsRepository
import com.whatstat.data.root.FileExtractor
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
class ChatListViewModel
    @Inject
    constructor(
        private val chatDao: ChatDao,
        private val contactDao: ContactDao,
        private val liteDbWriter: LiteDbWriter,
        private val analyticsRepository: AnalyticsRepository,
        private val cacheManager: CacheManager,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        val isLiteMode: StateFlow<Boolean> = analyticsRepository.isLiteMode

        private val allChatsFlow = MutableStateFlow<List<ChatListItem>>(emptyList())
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
        val filteredChats: StateFlow<List<ChatListItem>> =
            combine(
                allChatsFlow,
                _searchQuery,
            ) { chats, query ->
                if (query.isBlank()) {
                    chats
                } else {
                    chats.filter {
                        it.displayName.contains(query, ignoreCase = true) ||
                            it.jid.contains(query, ignoreCase = true)
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        init {
            val baselineVersion = analyticsRepository.liteCacheVersion.value
            loadChats()
            // Ricarica la lista solo in Lite mode quando viene completata una nuova importazione
            if (analyticsRepository.isLiteMode.value) {
                viewModelScope.launch {
                    analyticsRepository.liteCacheVersion.collect { version ->
                        if (version > baselineVersion) {
                            loadChats()
                        }
                    }
                }
            }
        }

        private fun loadChats() {
            viewModelScope.launch(Dispatchers.IO) {
                _isLoading.value = true
                try {
                    val isLite = analyticsRepository.isLiteMode.value

                    // In root mode, prova la cache prima di interrogare il DB
                    if (!isLite) {
                        val cached = cacheManager.getChatList()
                        if (cached != null) {
                            allChatsFlow.value = cached
                            _isLoading.value = false
                            return@launch
                        }
                    }

                    val chats = chatDao.getChatList()

                    // Arricchisci i nomi con wa.db
                    val jids = chats.map { it.jid }
                    val contactNames = contactDao.resolveContactNames(jids)

                    val enrichedChats =
                        chats.map { chat ->
                            val resolvedName = contactNames[chat.jid]
                            val displayName =
                                when {
                                    resolvedName != null && !chat.isGroup -> resolvedName
                                    resolvedName != null && chat.displayName.contains("@") -> resolvedName
                                    else -> chat.displayName
                                }
                            val avatarPath = getAvatarPath(chat.jid)
                            chat.copy(
                                displayName = displayName,
                                avatarPath = avatarPath,
                            )
                        }

                    allChatsFlow.value = enrichedChats

                    // Salva in cache solo in root mode
                    if (!analyticsRepository.isLiteMode.value) {
                        cacheManager.saveChatList(enrichedChats)
                    }
                } catch (e: Exception) {
                    allChatsFlow.value = emptyList()
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

        private val _shouldNavigateToSetup = MutableStateFlow(false)
        val shouldNavigateToSetup: StateFlow<Boolean> = _shouldNavigateToSetup.asStateFlow()

        fun deleteChat(chatId: Long) {
            viewModelScope.launch {
                liteDbWriter.deleteChat(chatId)
                // Dopo la cancellazione ricarichiamo la lista; se è vuota reset completo.
                val remaining =
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        chatDao.getChatList()
                    }
                if (remaining.isEmpty()) {
                    analyticsRepository.fullResetLiteMode()
                    _shouldNavigateToSetup.value = true
                } else {
                    analyticsRepository.notifyLiteChatDeleted()
                    allChatsFlow.value = remaining
                    _isLoading.value = false
                }
            }
        }

        /**
         * Rinomina una chat Lite aggiornando il DB e la lista in-memory.
         * Aggiorna la lista locale senza ricaricare tutto dal DB per feedback istantaneo.
         * Notifica [analyticsRepository] con un segnale leggero affinche' la ChatDetailScreen
         * aggiorni il nome nell'header senza rieseguire le query pesanti.
         */
        fun renameChat(
            chatId: Long,
            newName: String,
        ) {
            val trimmed = newName.trim()
            if (trimmed.isBlank()) return
            val chatToRename = allChatsFlow.value.find { it.chatId == chatId } ?: return
            val jid = chatToRename.jid

            viewModelScope.launch {
                liteDbWriter.renameChat(chatId, trimmed)
                // Aggiorna la lista in-memory per riflesso immediato nella UI
                allChatsFlow.value =
                    allChatsFlow.value.map { chat ->
                        if (chat.chatId == chatId) chat.copy(displayName = trimmed) else chat
                    }
                // Segnala agli altri ViewModel di aggiornare il nome e patcha la cache
                analyticsRepository.notifyLiteChatRenamed(chatId, jid, trimmed)
            }
        }

        fun consumeNavigateToSetup() {
            _shouldNavigateToSetup.value = false
        }
    }
