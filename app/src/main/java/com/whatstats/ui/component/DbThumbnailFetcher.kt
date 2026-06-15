package com.whatstats.ui.component

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.whatstats.data.repository.AnalyticsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import okio.Buffer
import java.io.FileNotFoundException

/**
 * Chiave per Coil che identifica la thumbnail di un video salvata nel DB di
 * WhatsApp (tabella message_thumbnail), indicizzata per message_row_id.
 * Permette di mostrare l'anteprima del video in galleria SENZA copiare l'intero
 * file (decine di MB) solo per ricavare un frame.
 */
data class DbVideoThumbnail(
    val messageRowId: Long,
)

/**
 * Coil Fetcher che legge la thumbnail JPEG dal database (message_thumbnail).
 * Il blob e gia un JPEG pronto: nessuna decodifica video, nessuna copia file.
 * Se la thumbnail non esiste (WhatsApp non sempre la salva, ~30% di copertura),
 * lancia FileNotFoundException e Coil mostra il contenuto di errore (placeholder).
 */
class DbThumbnailFetcher(
    private val key: DbVideoThumbnail,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val repo =
            EntryPointAccessors
                .fromApplication(
                    options.context.applicationContext,
                    DbThumbnailFetcherEntryPoint::class.java,
                ).analyticsRepository()

        val bytes =
            repo.getVideoThumbnailBlob(key.messageRowId)
                ?: throw FileNotFoundException("Nessuna thumbnail DB per message_row_id=${key.messageRowId}")

        val buffer = Buffer().apply { write(bytes) }
        return SourceResult(
            source = ImageSource(buffer, options.context),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<DbVideoThumbnail> {
        override fun create(
            data: DbVideoThumbnail,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = DbThumbnailFetcher(data, options)
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DbThumbnailFetcherEntryPoint {
        fun analyticsRepository(): AnalyticsRepository
    }
}
