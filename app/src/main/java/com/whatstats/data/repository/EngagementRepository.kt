package com.whatstats.data.repository

import com.whatstats.data.cache.CacheManager
import com.whatstats.data.db.dao.ChatDao
import com.whatstats.data.db.dao.ContactDao
import com.whatstats.data.db.dao.MessageDao
import com.whatstats.domain.engagement.MyActivity
import com.whatstats.domain.engagement.ReplyStatsAccumulator
import com.whatstats.domain.model.ChatReplyStats
import com.whatstats.domain.model.NamedReplyStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EngagementRepository
    @Inject
    constructor(
        private val messageDao: MessageDao,
        private val chatDao: ChatDao,
        private val contactDao: ContactDao,
        private val cacheManager: CacheManager,
    ) {
        private val mutex = Mutex()
        private var cachedMyJid: String? = null

        suspend fun getReplyStats(): List<ChatReplyStats> =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    cacheManager.getReplyStats() ?: computeReplyStats().also { cacheManager.saveReplyStats(it) }
                }
            }

        suspend fun getOneToOneReplyStats(): List<NamedReplyStats> =
            withContext(Dispatchers.IO) {
                // La chat "Note to Self" ha come jid il numero reale dell'utente, non il
                // sentinel MyActivity.ME_JID: va escluso confrontando col jid vero (guessUserJid).
                val myJid = getMyJid()
                val chats =
                    (cacheManager.getChatList() ?: chatDao.getChatList())
                        .filter {
                            !it.isGroup &&
                                it.jid != MyActivity.ME_JID &&
                                it.jid != myJid &&
                                !it.jid.endsWith("@broadcast") &&
                                !it.jid.endsWith("@newsletter")
                        }
                val chatsById = chats.associateBy { it.chatId }
                val names = contactDao.resolveContactNames(chats.map { it.jid })
                getReplyStats().mapNotNull { stats ->
                    val chat = chatsById[stats.chatId] ?: return@mapNotNull null
                    val name = names[chat.jid] ?: chat.displayName
                    NamedReplyStats(name = name, stats = stats, totalMessages = chat.totalMessages)
                }
            }

        private fun getMyJid(): String? {
            if (cachedMyJid == null) {
                cachedMyJid = messageDao.guessUserJid()
            }
            return cachedMyJid
        }

        private fun computeReplyStats(): List<ChatReplyStats> {
            val accumulator = ReplyStatsAccumulator()
            messageDao.forEachReplyStatsRow { chatId, fromMe, timestamp ->
                accumulator.add(chatId, fromMe, timestamp)
            }
            return accumulator.finish()
        }
    }
