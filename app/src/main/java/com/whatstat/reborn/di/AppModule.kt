package com.whatstat.reborn.di

import android.content.Context
import com.whatstat.reborn.data.cache.CacheManager
import com.whatstat.reborn.data.crypto.Crypt14Decryptor
import com.whatstat.reborn.data.crypto.KeyReader
import com.whatstat.reborn.data.db.ActiveDatabaseProvider
import com.whatstat.reborn.data.db.DatabaseProvider
import com.whatstat.reborn.data.db.WhatsAppDatabase
import com.whatstat.reborn.data.db.dao.ChatDao
import com.whatstat.reborn.data.db.dao.ContactDao
import com.whatstat.reborn.data.db.dao.MessageDao
import com.whatstat.reborn.data.repository.AnalyticsRepository
import com.whatstat.reborn.data.repository.WhatsAppRepository
import com.whatstat.reborn.data.root.FileExtractor
import com.whatstat.reborn.data.root.RootManager
import com.whatstat.reborn.data.lite.LiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideWhatsAppDatabase(
        @ApplicationContext context: Context
    ): WhatsAppDatabase = WhatsAppDatabase(context)

    @Provides
    @Singleton
    fun provideActiveDatabaseProvider(
        whatsAppDatabase: WhatsAppDatabase,
        liteDatabase: LiteDatabase
    ): ActiveDatabaseProvider = ActiveDatabaseProvider(whatsAppDatabase, liteDatabase)

    @Provides
    @Singleton
    fun provideMessageDao(
        database: ActiveDatabaseProvider
    ): MessageDao = MessageDao(database)

    @Provides
    @Singleton
    fun provideChatDao(
        database: ActiveDatabaseProvider
    ): ChatDao = ChatDao(database)

    @Provides
    @Singleton
    fun provideContactDao(
        database: ActiveDatabaseProvider
    ): ContactDao = ContactDao(database)

    @Provides
    @Singleton
    fun provideAnalyticsRepository(
        messageDao: MessageDao,
        chatDao: ChatDao,
        contactDao: ContactDao,
        cacheManager: CacheManager,
        liteModeManager: com.whatstat.reborn.data.lite.LiteModeManager,
        activeDatabaseProvider: ActiveDatabaseProvider
    ): AnalyticsRepository = AnalyticsRepository(messageDao, chatDao, contactDao, cacheManager, liteModeManager, activeDatabaseProvider)
}
