package com.whatstats.di

import android.content.Context
import com.whatstats.data.cache.CacheManager
import com.whatstats.data.db.ActiveDatabaseProvider
import com.whatstats.data.db.WhatsAppDatabase
import com.whatstats.data.db.dao.ChatDao
import com.whatstats.data.db.dao.ContactDao
import com.whatstats.data.db.dao.MessageDao
import com.whatstats.data.lite.LiteDatabase
import com.whatstats.data.repository.AnalyticsRepository
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
        telemetryRepository: com.whatstats.telemetry.TelemetryRepository,
    ): WhatsAppDatabase = WhatsAppDatabase(context, telemetryRepository)

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
        liteDatabase: com.whatstats.data.lite.LiteDatabase,
        activeDatabaseProvider: ActiveDatabaseProvider,
        liteModeManager: com.whatstats.data.lite.LiteModeManager,
        filterStateHolder: com.whatstats.ui.state.FilterStateHolder,
        @ApplicationContext context: Context,
        telemetryRepository: com.whatstats.telemetry.TelemetryRepository,
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
            telemetryRepository,
        )
}
