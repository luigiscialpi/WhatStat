package com.whatstat.reborn.ui.screen.media

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.whatstat.reborn.data.cache.CacheManager
import com.whatstat.reborn.data.db.model.DateRange
import com.whatstat.reborn.data.db.model.DomainStat
import com.whatstat.reborn.data.db.model.LinkStat
import com.whatstat.reborn.data.db.model.MediaStat
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.data.db.dao.ContactDao
import com.whatstat.reborn.data.repository.AnalyticsRepository
import com.whatstat.reborn.data.root.FileExtractor
import com.whatstat.reborn.ui.state.FilterStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MediaStatsViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    private val contactDao: ContactDao,
    private val cacheManager: CacheManager,
    private val filterState: FilterStateHolder,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val isLiteMode = analyticsRepository.isLiteMode

    private val _linkStats = MutableStateFlow<List<LinkStat>>(emptyList())
    val linkStats: StateFlow<List<LinkStat>> = _linkStats.asStateFlow()

    private val avatarDir: File by lazy { File(context.filesDir, FileExtractor.AVATAR_DIR_DEST) }
    private fun getAvatarPath(jid: String): String? {
        if (!avatarDir.exists()) return null
        if (jid == "me@s.whatsapp.net") {
            val me = File(avatarDir, "me.jpg")
            return if (me.exists()) me.absolutePath else null
        }
        val f = File(avatarDir, "$jid.jpg")
        return if (f.exists()) f.absolutePath else null
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _linkStatsBySender = MutableStateFlow<List<LinkStat>>(emptyList())
    val linkStatsBySender: StateFlow<List<LinkStat>> = _linkStatsBySender.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(TimeRange.ALL_TIME)
    val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()

    private val _customDateRange = MutableStateFlow<DateRange?>(null)
    val customDateRange: StateFlow<DateRange?> = _customDateRange.asStateFlow()

    private val _firstMessageTimestamp = MutableStateFlow<Long?>(null)
    val firstMessageTimestamp: StateFlow<Long?> = _firstMessageTimestamp.asStateFlow()

    private val _chatId = MutableStateFlow<Long?>(null)
    val chatId: StateFlow<Long?> = _chatId.asStateFlow()

    private val _chatContextName = MutableStateFlow<String?>(null)
    val chatContextName: StateFlow<String?> = _chatContextName.asStateFlow()

    private val _chatContextAvatarPath = MutableStateFlow<String?>(null)
    val chatContextAvatarPath: StateFlow<String?> = _chatContextAvatarPath.asStateFlow()

    private val _selectedChatJid = MutableStateFlow<String?>(null)
    val selectedChatJid: StateFlow<String?> = _selectedChatJid.asStateFlow()

    // ChatId del gruppo selezionato nel breakdown (dalla vista globale)
    private val _breakdownChatId = MutableStateFlow<Long?>(null)

    private val _topDomains = MutableStateFlow<List<DomainStat>>(emptyList())
    val topDomains: StateFlow<List<DomainStat>> = _topDomains.asStateFlow()

    // Triple: (displayName, avatarPath, domains)
    private val _contactDomainStats = MutableStateFlow<Triple<String, String?, List<DomainStat>>?>(null)
    val contactDomainStats: StateFlow<Triple<String, String?, List<DomainStat>>?> = _contactDomainStats.asStateFlow()

    private val _isDomainLoading = MutableStateFlow(false)
    val isDomainLoading: StateFlow<Boolean> = _isDomainLoading.asStateFlow()

    private val _loadingProgress = MutableStateFlow<Int?>(null)
    val loadingProgress: StateFlow<Int?> = _loadingProgress.asStateFlow()

    private val _loadingProgressDomains = MutableStateFlow<Int?>(null)
    val loadingProgressDomains: StateFlow<Int?> = _loadingProgressDomains.asStateFlow()

    // Media Gallery state
    private val _mediaGalleryState = MutableStateFlow<Triple<String, String?, List<MediaStat>>?>(null)
    val mediaGalleryState: StateFlow<Triple<String, String?, List<MediaStat>>?> = _mediaGalleryState.asStateFlow()

    private val _isMediaGalleryLoading = MutableStateFlow(false)
    val isMediaGalleryLoading: StateFlow<Boolean> = _isMediaGalleryLoading.asStateFlow()

    private val _loadingProgressMediaGallery = MutableStateFlow<Int?>(null)
    val loadingProgressMediaGallery: StateFlow<Int?> = _loadingProgressMediaGallery.asStateFlow()

    fun setChatId(id: Long) {
        _chatId.value = id
        loadStats()
    }

    fun setSelectedChatJid(jid: String?) {
        _selectedChatJid.value = jid
    }

    fun clearSelection() {
        _selectedChatJid.value = null
        _breakdownChatId.value = null
        _linkStatsBySender.value = emptyList()
        _contactDomainStats.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private var initialized = false
    private var lastKnownCacheVersion = 0L

    init {
        // In LiteMode, ricarica i dati quando viene importato un nuovo ZIP
        if (analyticsRepository.isLiteMode.value) {
            viewModelScope.launch {
                analyticsRepository.liteCacheVersion.collect { version ->
                    if (version > 0L && version != lastKnownCacheVersion) {
                        lastKnownCacheVersion = version
                        initialized = false
                        // Ricarica immediatamente con il chatId corrente
                        _linkStats.value = emptyList()
                        _linkStatsBySender.value = emptyList()
                        _topDomains.value = emptyList()
                        initialize(_chatId.value, force = true)
                    }
                }
            }
            // Osserva i rename di chat Lite: aggiorna solo il nome nel titolo
            // senza re-inizializzare le statistiche media.
            viewModelScope.launch {
                analyticsRepository.liteRenameVersion.collect { _ ->
                    refreshContextName()
                    refreshGlobalMediaNames()
                }
            }
        }
        // Carica il timestamp del primo messaggio
        viewModelScope.launch(Dispatchers.IO) {
            _firstMessageTimestamp.value = analyticsRepository.getFirstMessageTimestamp()
        }
    }

    /**
     * Rilegge dal DB solo il nome della chat corrente e aggiorna [_chatContextName].
     * Chiamato su [analyticsRepository.liteRenameVersion] senza toccare le statistiche media.
     */
    private fun refreshContextName() {
        val chatId = _chatId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                analyticsRepository.getChatContextInfo(chatId)
            }.onSuccess { info ->
                _chatContextName.value = info.displayName
            }.onFailure { e ->
                android.util.Log.w("WhatStat", "refreshContextName (Media): ${e.message}")
            }
        }
    }

    /**
     * Ricarica solo i nomi dei contatti nella lista globale dei link
     * senza rieseguire il conteggio dei messaggi o dei media.
     */
    private fun refreshGlobalMediaNames() {
        if (_chatId.value != null) return // Solo per la vista globale
        val currentLinks = _linkStats.value
        val currentSenders = _linkStatsBySender.value
        if (currentLinks.isEmpty() && currentSenders.isEmpty()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Refresh Link Stats (chats)
            if (currentLinks.isNotEmpty()) {
                val jids = currentLinks.map { it.jid }
                val contactNames = contactDao.resolveContactNames(jids)
                _linkStats.value = currentLinks.map { stat ->
                    stat.copy(displayName = contactNames[stat.jid] ?: stat.displayName)
                }
            }

            // 2. Refresh Sender Stats
            if (currentSenders.isNotEmpty()) {
                val jids = currentSenders.map { it.jid }
                val contactNames = contactDao.resolveContactNames(jids)
                _linkStatsBySender.value = currentSenders.map { stat ->
                    stat.copy(displayName = contactNames[stat.jid] ?: stat.displayName)
                }
            }
        }
    }

    fun initialize(chatId: Long?, force: Boolean = false) {
        if (!initialized || force) {
            initialized = true
            _chatId.value = chatId
            // Ripristina il filtro dal contesto corretto
            if (chatId != null) {
                _selectedTimeRange.value = filterState.chatTimeRange
                _customDateRange.value = filterState.chatCustomRange
                viewModelScope.launch {
                    runCatching {
                        analyticsRepository.getChatContextInfo(chatId)
                    }.onSuccess { info ->
                        _chatContextName.value = info.displayName
                        _chatContextAvatarPath.value = getAvatarPath(info.jid)
                    }
                }
            } else {
                _selectedTimeRange.value = filterState.globalTimeRange
                _customDateRange.value = filterState.globalCustomRange
                _chatContextName.value = null
                _chatContextAvatarPath.value = null
            }
            loadStats()
        }
    }

    fun syncFilterState() {
        val (newRange, newCustom) = if (_chatId.value != null) {
            filterState.chatTimeRange to filterState.chatCustomRange
        } else {
            filterState.globalTimeRange to filterState.globalCustomRange
        }
        if (newRange != _selectedTimeRange.value || newCustom != _customDateRange.value) {
            _selectedTimeRange.value = newRange
            _customDateRange.value = newCustom
            loadStats()
        }
    }

    fun setTimeRange(range: TimeRange) {
        _selectedTimeRange.value = range
        if (range != TimeRange.CUSTOM) {
            _customDateRange.value = null
        }
        if (_chatId.value != null) filterState.setChat(range, _customDateRange.value)
        else filterState.setGlobal(range, _customDateRange.value)
        loadStats()
    }

    fun setCustomDateRange(dateRange: DateRange?) {
        val wasCustom = _selectedTimeRange.value == TimeRange.CUSTOM
        _customDateRange.value = dateRange
        if (dateRange != null) {
            _selectedTimeRange.value = TimeRange.CUSTOM
            if (_chatId.value != null) filterState.setChat(TimeRange.CUSTOM, dateRange)
            else filterState.setGlobal(TimeRange.CUSTOM, dateRange)
        } else {
            if (wasCustom) {
                _selectedTimeRange.value = TimeRange.ALL_TIME
                if (_chatId.value != null) filterState.setChat(TimeRange.ALL_TIME)
                else filterState.setGlobal(TimeRange.ALL_TIME)
                loadStats()
            }
        }
    }

    fun applyCustomDateRange() {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val isAllTime = _selectedTimeRange.value == TimeRange.ALL_TIME
                val (startTime, endTime) = if (_selectedTimeRange.value == TimeRange.CUSTOM) {
                    _customDateRange.value?.let {
                        analyticsRepository.getDateRangeBounds(it)
                    } ?: (null to null)
                } else {
                    analyticsRepository.getTimeRangeBounds(_selectedTimeRange.value, _customDateRange.value)
                }
                val cid = _chatId.value

                // Prova cache per ALL_TIME
                if (isAllTime) {
                    val cached = if (cid != null) cacheManager.getChatMediaStats(cid)
                                 else cacheManager.getGlobalMediaStats()
                    if (cached != null) {
                        _linkStats.value = cached.first.map { it.copy(avatarPath = getAvatarPath(it.jid)) }
                        _topDomains.value = cached.second
                        _isLoading.value = false
                        _loadingProgress.value = null
                        return@launch
                    }
                }

                if (cid != null) {
                    // Per una chat specifica: mostra direttamente lo split per mittente
                    _linkStats.value = analyticsRepository.getLinkStatsBySender(cid, startTime, endTime)
                        .map { it.copy(avatarPath = getAvatarPath(it.jid)) }
                } else {
                    _linkStats.value = analyticsRepository.getLinkStats(null, startTime, endTime)
                        .map { it.copy(avatarPath = getAvatarPath(it.jid)) }
                }
                
                _loadingProgress.value = 0
                // Carica anche i domini top globali/chat
                _topDomains.value = analyticsRepository.getTopDomains(
                    chatId = cid,
                    jid = null,
                    startTime = startTime,
                    endTime = endTime,
                    onProgress = { progress -> _loadingProgress.value = progress }
                )

                // Salva in cache per ALL_TIME
                if (isAllTime) {
                    val statsToCache = _linkStats.value.map { it.copy(avatarPath = null) }
                    if (cid != null) cacheManager.saveChatMediaStats(cid, statsToCache, _topDomains.value)
                    else cacheManager.saveGlobalMediaStats(statsToCache, _topDomains.value)
                }
                
            } catch (e: Exception) {
                _linkStats.value = emptyList()
            } finally {
                _isLoading.value = false
                _loadingProgress.value = null
            }
        }
    }

    fun loadLinkStatsBySenderForJid(jid: String) {
        viewModelScope.launch {
            try {
                val (startTime, endTime) = if (_selectedTimeRange.value == TimeRange.CUSTOM) {
                    _customDateRange.value?.let {
                        analyticsRepository.getDateRangeBounds(it)
                    } ?: (null to null)
                } else {
                    analyticsRepository.getTimeRangeBounds(_selectedTimeRange.value, _customDateRange.value)
                }
                // Salva il chatId del gruppo selezionato per il breakdown
                _breakdownChatId.value = analyticsRepository.getChatIdByJid(jid)
                _linkStatsBySender.value = analyticsRepository.getLinkStatsByJid(jid, startTime, endTime)
                    .map { it.copy(avatarPath = getAvatarPath(it.jid)) }
            } catch (e: Exception) {
                _linkStatsBySender.value = emptyList()
            }
        }
    }

    fun loadLinkStatsBySender(chatId: Long) {
        viewModelScope.launch {
            try {
                val (startTime, endTime) = if (_selectedTimeRange.value == TimeRange.CUSTOM) {
                    _customDateRange.value?.let {
                        analyticsRepository.getDateRangeBounds(it)
                    } ?: (null to null)
                } else {
                    analyticsRepository.getTimeRangeBounds(_selectedTimeRange.value, _customDateRange.value)
                }
                val result = analyticsRepository.getLinkStatsBySender(chatId, startTime, endTime)
                _linkStatsBySender.value = result.map { it.copy(avatarPath = getAvatarPath(it.jid)) }
            } catch (e: Exception) {
                _linkStatsBySender.value = emptyList()
            }
        }
    }

    fun loadDomainStatsForContact(jid: String, displayName: String?) {
        viewModelScope.launch {
            _isDomainLoading.value = true
            val name = displayName ?: jid.substringBefore("@")
            val avatarPath = getAvatarPath(jid)
            _contactDomainStats.value = Triple(name, avatarPath, emptyList())
            try {
                val (startTime, endTime) = if (_selectedTimeRange.value == TimeRange.CUSTOM) {
                    _customDateRange.value?.let {
                        analyticsRepository.getDateRangeBounds(it)
                    } ?: (null to null)
                } else {
                    analyticsRepository.getTimeRangeBounds(_selectedTimeRange.value, _customDateRange.value)
                }
                _loadingProgressDomains.value = 0
                val currentChatContext = _chatId.value ?: _breakdownChatId.value
                val isGlobalList = currentChatContext == null

                // In Lite Mode, ActiveDatabaseProvider instrada la query al DB Lite.
                // Il chatId e' risolto direttamente: null se lista globale, altrimenti il contesto corrente.
                val targetChatId = if (isGlobalList) {
                    analyticsRepository.getChatIdByJid(jid)
                } else {
                    currentChatContext
                }
                val targetJid = if (isGlobalList) null else jid

                val domains = targetChatId?.let { cid ->
                    analyticsRepository.getTopDomains(
                        chatId = cid,
                        jid = targetJid,
                        startTime = startTime,
                        endTime = endTime,
                        onProgress = { progress -> _loadingProgressDomains.value = progress }
                    )
                } ?: emptyList()
                _contactDomainStats.value = Triple(name, avatarPath, domains)
            } catch (e: Exception) {
            } finally {
                _isDomainLoading.value = false
                _loadingProgressDomains.value = null
            }
        }
    }

    fun dismissContactDomains() {
        _contactDomainStats.value = null
    }

    fun loadMediaGalleryForContact(jid: String, displayName: String?) {
        viewModelScope.launch {
            _isMediaGalleryLoading.value = true
            val avatarPath = getAvatarPath(jid)
            val name = displayName ?: jid.substringBefore("@")
            // Imposta subito stato vuoto per mostrare la sheet immediatamente
            _mediaGalleryState.value = Triple(name, avatarPath, emptyList())
            try {
                _loadingProgressMediaGallery.value = 0
                val (startTime, endTime) = if (_selectedTimeRange.value == TimeRange.CUSTOM) {
                    _customDateRange.value?.let {
                        analyticsRepository.getDateRangeBounds(it)
                    } ?: (null to null)
                } else {
                    analyticsRepository.getTimeRangeBounds(_selectedTimeRange.value, _customDateRange.value)
                }

                val currentChatContext = _chatId.value ?: _breakdownChatId.value
                val isGlobalList = currentChatContext == null

                // In Lite Mode, ActiveDatabaseProvider instrada la query al DB Lite.
                // Il chatId e' risolto direttamente: null se lista globale, altrimenti il contesto corrente.
                val targetChatId = if (isGlobalList) {
                    analyticsRepository.getChatIdByJid(jid)
                } else {
                    currentChatContext
                }
                val targetJid = if (isGlobalList) null else jid

                val mediaFiles = targetChatId?.let { cid ->
                    analyticsRepository.getMediaFiles(
                        chatId = cid,
                        jid = targetJid,
                        startTime = startTime,
                        endTime = endTime,
                        onProgress = { progress -> _loadingProgressMediaGallery.value = progress }
                    )
                } ?: emptyList()

                // AnalyticsRepository.getMediaFiles ha già filtrato i file esistenti
                _mediaGalleryState.value = Triple(name, avatarPath, mediaFiles)
            } catch (e: Exception) {
                _mediaGalleryState.value = null
            } finally {
                _isMediaGalleryLoading.value = false
                _loadingProgressMediaGallery.value = null
            }
        }
    }

    fun dismissMediaGallery() {
        _mediaGalleryState.value = null
    }
}
