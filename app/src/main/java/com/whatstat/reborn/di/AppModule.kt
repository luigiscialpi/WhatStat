package com.whatstat.reborn.di

import android.content.Context
import com.whatstat.reborn.data.cache.CacheManager
import com.whatstat.reborn.data.crypto.Crypt14Decryptor
import com.whatstat.reborn.data.crypto.KeyReader
import com.whatstat.reborn.data.db.WhatsAppDatabase
import com.whatstat.reborn.data.db.dao.ChatDao
import com.whatstat.reborn.data.db.dao.ContactDao
import com.whatstat.reborn.data.db.dao.MessageDao
import com.whatstat.reborn.data.repository.AnalyticsRepository
import com.whatstat.reborn.data.repository.WhatsAppRepository
import com.whatstat.reborn.data.root.FileExtractor
import com.whatstat.reborn.data.root.RootManager
import com.whatstat.reborn.ui.tutorial.data.TutorialDataStoreManager
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
    fun provideMessageDao(
        database: WhatsAppDatabase
    ): MessageDao = MessageDao(database)

    @Provides
    @Singleton
    fun provideChatDao(
        database: WhatsAppDatabase
    ): ChatDao = ChatDao(database)

    @Provides
    @Singleton
    fun provideContactDao(
        database: WhatsAppDatabase
    ): ContactDao = ContactDao(database)

    @Provides
    @Singleton
    fun provideAnalyticsRepository(
        messageDao: MessageDao,
        chatDao: ChatDao,
        contactDao: ContactDao,
        cacheManager: CacheManager
    ): AnalyticsRepository = AnalyticsRepository(messageDao, chatDao, contactDao, cacheManager)

    @Provides
    @Singleton
    fun provideTutorialDataStoreManager(
        @ApplicationContext context: Context
    ): TutorialDataStoreManager = TutorialDataStoreManager(context)
}
