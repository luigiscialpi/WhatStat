package com.whatstats.ui.component

import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.buffer
import okio.source
import java.io.File
import java.io.FileNotFoundException
import java.net.URLDecoder
import java.util.zip.ZipFile

/**
 * Coil Fetcher per caricare immagini e sticker direttamente da un archivio ZIP
 * senza estrarli fisicamente sul disco. Evita leak di descrittori di file
 * chiudendo il ZipFile all'atto della chiusura dello stream Okio.
 */
class ZipFileFetcher(
    private val uri: String,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        // Decodifica i caratteri speciali dell'URI (es. %7C -> | e %20 -> spazio)
        val decodedUri =
            try {
                URLDecoder.decode(uri, "UTF-8")
            } catch (e: Exception) {
                uri
            }

        // Formato URI: zip://lite_chat_123/source.zip|Media/WhatsApp Images/IMG.jpg
        val parts = decodedUri.removePrefix("zip://").split("|")
        if (parts.size < 2) throw FileNotFoundException("URI malformato o non decodificato: $decodedUri")

        val baseFolder = File(options.context.filesDir, "lite_media")
        val zipFileObj = File(baseFolder, parts[0])
        if (!zipFileObj.exists()) throw FileNotFoundException("ZIP non trovato: ${zipFileObj.absolutePath}")

        val zipFile = ZipFile(zipFileObj)
        val entry =
            zipFile.getEntry(parts[1]) ?: run {
                zipFile.close()
                throw FileNotFoundException("Entry non trovata nello ZIP: ${parts[1]}")
            }

        val zipStream = zipFile.getInputStream(entry)

        // Okio Source custom per assicurare la chiusura del ZipFile quando Coil ha finito la decodifica
        val customSource =
            object : okio.Source {
                private val delegate = zipStream.source()

                override fun read(
                    sink: okio.Buffer,
                    byteCount: Long,
                ): Long {
                    return delegate.read(sink, byteCount)
                }

                override fun timeout(): okio.Timeout {
                    return delegate.timeout()
                }

                override fun close() {
                    try {
                        delegate.close()
                    } finally {
                        zipFile.close()
                    }
                }
            }

        return SourceResult(
            source = ImageSource(customSource.buffer(), options.context),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            val uriString = data.toString()
            if (!uriString.startsWith("zip://")) return null
            return ZipFileFetcher(uriString, options)
        }
    }
}
