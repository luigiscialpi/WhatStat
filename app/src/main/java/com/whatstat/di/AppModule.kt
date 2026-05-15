package com.whatstat.di

import android.content.Context
import com.whatstat.data.cache.CacheManager
import com.whatstat.data.db.ActiveDatabaseProvider
import com.whatstat.data.db.WhatsAppDatabase
import com.whatstat.data.db.dao.ChatDao
import com.whatstat.data.db.dao.ContactDao
import com.whatstat.data.db.dao.MessageDao
import com.whatstat.data.lite.LiteDatabase
import com.whatstat.data.repository.AnalyticsRepository
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
        @ApplicationContext context: Context,
    ): WhatsAppDatabase = WhatsAppDatabase(context)

    @Provides
    @Singleton
    fun provideActiveDatabaseProvider(
        whatsAppDatabase: WhatsAppDatabase,
        liteDatabase: LiteDatabase,
        @ApplicationContext context: Context,
    ): ActiveDatabaseProvider = ActiveDatabaseProvider(whatsAppDatabase, liteDatabase, context)

    @Provides
    @Singleton
    fun provideMessageDao(database: ActiveDatabaseProvider): MessageDao = MessageDao(database)

    @Provides
    @Singleton
    fun provideChatDao(database: ActiveDatabaseProvider): ChatDao = ChatDao(database)

    @Provides
    @Singleton
    fun provideContactDao(database: ActiveDatabaseProvider): ContactDao = ContactDao(database)

    @Provides
    @Singleton
    fun provideAnalyticsRepository(
        messageDao: MessageDao,
        chatDao: ChatDao,
        contactDao: ContactDao,
        cacheManager: CacheManager,
        liteDatabase: com.whatstat.data.lite.LiteDatabase,
        activeDatabaseProvider: ActiveDatabaseProvider,
        liteModeManager: com.whatstat.data.lite.LiteModeManager,
        filterStateHolder: com.whatstat.ui.state.FilterStateHolder,
        @ApplicationContext context: Context,
    ): AnalyticsRepository =
        AnalyticsRepository(
            messageDao,
            chatDao,
            contactDao,
            cacheManager,
            liteDatabase,
            activeDatabaseProvider,
            liteModeManager,
            filterStateHolder,
            context,
        )
}
