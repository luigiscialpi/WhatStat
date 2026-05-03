package com.whatstat.reborn.data.lite

import android.content.Context
import com.whatstat.reborn.domain.model.GlobalStats
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class LiteCacheData(
    val sourceFileName: String,
    val lastModified: Long,
    val totalLines: Int,
    val ownerName: String,
    val globalStats: GlobalStats,
    val messages: List<ParsedMessage> = emptyList(),
    val mediaStats: LiteMediaStats? = null
)

@Serializable
data class LiteMediaStats(
    val totalCount: Int,
    val images: Int,
    val videos: Int,
    val audioFiles: Int,
    val documents: Int,
    val mediaItems: List<LiteMediaItem>
)

@Serializable
data class LiteMediaItem(
    val fileName: String,
    val relativePath: String,
    val type: String, // IMAGE, VIDEO, AUDIO, DOCUMENT
    val timestamp: Long
)

@Singleton
class LiteCacheRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true
    }
    
    private val cacheFile: File
        get() = File(context.cacheDir, "lite_chat_cache.json")

    suspend fun saveCache(data: LiteCacheData) = withContext(Dispatchers.IO) {
        try {
            val content = json.encodeToString(data)
            cacheFile.writeText(content)
        } catch (e: Exception) {
            android.util.Log.e("LiteCache", "Errore nel salvataggio della cache", e)
        }
    }

    suspend fun loadCache(): LiteCacheData? = withContext(Dispatchers.IO) {
        if (!cacheFile.exists()) return@withContext null
        try {
            val content = cacheFile.readText()
            json.decodeFromString<LiteCacheData>(content)
        } catch (e: Exception) {
            android.util.Log.e("LiteCache", "Errore nel caricamento della cache", e)
            null
        }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
    }
}
