package com.whatstat.reborn.ui.screen.media

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.reborn.data.cache.CacheManager
import com.whatstat.reborn.data.db.model.DateRange
import com.whatstat.reborn.data.db.model.DomainStat
import com.whatstat.reborn.data.db.model.LinkStat
import com.whatstat.reborn.data.db.model.MediaStat
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.data.repository.AnalyticsRepository
import com.whatstat.reborn.data.root.FileExtractor
import com.whatstat.reborn.ui.state.FilterStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MediaStatsViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    private val cacheManager: CacheManager,
    private val filterState: FilterStateHolder,
    @ApplicationContext private val context: Context
) : ViewModel() {

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

    private val _chatId = MutableStateFlow<Long?>(null)
    val chatId: StateFlow<Long?> = _chatId.asStateFlow()

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

    fun initialize(chatId: Long?) {
        if (!initialized) {
            initialized = true
            _chatId.value = chatId
            // Ripristina il filtro dal contesto corretto
            if (chatId != null) {
                _selectedTimeRange.value = filterState.chatTimeRange
                _customDateRange.value = filterState.chatCustomRange
            } else {
                _selectedTimeRange.value = filterState.globalTimeRange
                _customDateRange.value = filterState.globalCustomRange
            }
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
                // Error handling
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