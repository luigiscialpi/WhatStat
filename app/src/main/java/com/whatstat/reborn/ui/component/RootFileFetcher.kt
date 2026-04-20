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
 */
class RootFileFetcher(
    private val file: File,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val stream = SuFileInputStream.open(SuFile(file.absolutePath))
        return SourceResult(
            source = ImageSource(stream.source().buffer(), options.context),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class Factory : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!data.exists()) {
                val suFile = SuFile(data.absolutePath)
                if (suFile.exists()) {
                    return RootFileFetcher(data, options)
                }
            }
            return null
        }
    }
}
