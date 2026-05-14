package com.whatstat.reborn.ui.component

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import okio.buffer
import okio.source
import java.io.File

/**
 * Coil Fetcher che usa SuFileInputStream (root) per leggere file
 * in directory protette da Scoped Storage (es. /sdcard/Android/media/com.whatsapp/).
 * Interviene SOLO quando il file non è accessibile normalmente ma esiste via root.
 * Tenta anche la variante /Sent/ per i media inviati dall'utente.
 */
class RootFileFetcher(
    private val file: File,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val stream = SuFileInputStream.open(SuFile(file.absolutePath))
        return SourceResult(
            source = ImageSource(stream.source().buffer(), options.context),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<File> {
        override fun create(
            data: File,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            // Il file esiste normalmente — lascia gestire a Coil
            if (data.exists()) return null

            // Candidati: path originale + variante /Sent/
            val candidates =
                buildList {
                    add(data)
                    // Es. .../WhatsApp Images/IMG-123.jpg → .../WhatsApp Images/Sent/IMG-123.jpg
                    val parent = data.parentFile
                    if (parent != null && !parent.name.equals("Sent", ignoreCase = true)) {
                        add(File(parent, "Sent/${data.name}"))
                    }
                }

            val found = candidates.firstOrNull { SuFile(it.absolutePath).exists() }
            return if (found != null) RootFileFetcher(found, options) else null
        }
    }
}
