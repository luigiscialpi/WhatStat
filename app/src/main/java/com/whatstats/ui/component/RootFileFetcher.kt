package com.whatstats.ui.component

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.topjohnwu.superuser.Shell
import okio.buffer
import okio.source
import java.io.File

/**
 * Coil Fetcher per leggere file WhatsApp tramite root, senza MANAGE_EXTERNAL_STORAGE.
 * SuFileInputStream non usato: i path WhatsApp hanno spazi e il shell interna splitterebbe.
 * Strategia: copia il file in cacheDir con nome hash (senza spazi) via `cp`, poi legge
 * normalmente. La copia è permanente: aperture successive non eseguono root call.
 * Tenta anche la variante /Sent/ per i media inviati dall'utente.
 */
class RootFileFetcher(
    private val file: File,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        // PROBLEMA: SuFileInputStream usa `cat <path>` internamente.
        // I path WhatsApp hanno spazi (es. "WhatsApp Images") → lo shell splitta → FileNotFound.
        //
        // SOLUZIONE: copia il file in cacheDir con nome hash (senza spazi) via root,
        // poi leggi il file copiato normalmente (FileInputStream). La copia è permanente:
        // successive aperture usano il file già copiato senza nuova root call.
        val tmpFile = getTempFile(file)
        if (tmpFile != null) {
            return SourceResult(
                source = ImageSource(tmpFile.source().buffer(), options.context),
                mimeType = mimeTypeOf(file),
                dataSource = DataSource.DISK,
            )
        }

        // Fallback: variante /Sent/ per media inviati
        val parent = file.parentFile
        if (parent != null && !parent.name.equals("Sent", ignoreCase = true)) {
            val sentFile = File(parent, "Sent/${file.name}")
            val tmpSent = getTempFile(sentFile)
            if (tmpSent != null) {
                return SourceResult(
                    source = ImageSource(tmpSent.source().buffer(), options.context),
                    mimeType = mimeTypeOf(sentFile),
                    dataSource = DataSource.DISK,
                )
            }
        }

        throw java.io.FileNotFoundException("Not found via root: ${file.absolutePath}")
    }

    /**
     * Deduce il mimeType dall'estensione del file originale.
     * Necessario perche il file temp e salvato senza estensione (.tmp): senza
     * mimeType esplicito VideoFrameDecoder non si attiva per i video e Coil
     * mostra solo il placeholder.
     */
    private fun mimeTypeOf(source: File): String? {
        val ext = source.extension.lowercase()
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "3gp", "3gpp" -> "video/3gpp"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "bmp" -> "image/bmp"
            else -> null
        }
    }

    /**
     * Se il file temporaneo esiste già (copia precedente), lo restituisce direttamente.
     * Altrimenti chiede a root di copiarlo in cacheDir con un nome senza spazi.
     * Ritorna null se la copia fallisce.
     */
    private fun getTempFile(source: File): File? = copyToCache(options.context, source.absolutePath)

    class Factory : Fetcher.Factory<File> {
        override fun create(
            data: File,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            val path = data.absolutePath
            val ctx = options.context

            // Le directory private dell'app sono sempre leggibili direttamente da Coil.
            // Per questi path lasciamo gestire al fetcher di default (nessun root).
            val ownDirs =
                listOfNotNull(
                    ctx.cacheDir?.absolutePath,
                    ctx.filesDir?.absolutePath,
                    ctx.getExternalFilesDir(null)?.absolutePath,
                    ctx.externalCacheDir?.absolutePath,
                )
            if (ownDirs.any { path.startsWith(it) }) return null

            // Path esterno (Scoped Storage WhatsApp).
            // ATTENZIONE: data.exists() NON e affidabile qui. Su Android con FUSE
            // /sdcard/Android/media/com.whatsapp/... risulta "esistente" (il daemon
            // FUSE vede il file) ma la lettura fallisce con MediaProvider
            // "Permission denied" senza MANAGE_EXTERNAL_STORAGE. Quindi non possiamo
            // delegare a Coil: usiamo sempre root per questi path.
            // RootFileFetcher copia il file in cacheDir via `cp` e poi lo legge.
            // Se il file non esiste davvero, fetch() lancia FileNotFoundException
            // che Coil gestisce mostrando il placeholder di errore, senza crash.
            return RootFileFetcher(data, options)
        }
    }

    companion object {
        /** Tetto massimo per i file copiati in cacheDir (media_*.tmp). */
        private const val MAX_CACHE_BYTES = 250L * 1024 * 1024 // 250 MB

        /** Prefisso/suffisso dei file di cache gestiti da questa classe. */
        private const val CACHE_PREFIX = "media_"
        private const val CACHE_SUFFIX = ".tmp"

        /** Serializza il trimming per evitare race tra copie concorrenti (Coil IO). */
        private val cacheTrimLock = Any()

        /**
         * Copia un file da un path assoluto (anche Scoped Storage WhatsApp) in
         * cacheDir tramite root `cp`, restituendo il File locale leggibile.
         * Riutilizzabile fuori da Coil (es. durata media, condivisione/riproduzione)
         * dove il path WhatsApp non e accessibile senza MANAGE_EXTERNAL_STORAGE.
         * Se la copia esiste gia (stesso path), la restituisce senza nuova root call.
         * Ritorna null se la copia fallisce.
         */
        fun copyToCache(
            context: android.content.Context,
            absolutePath: String,
        ): File? {
            val safeName = "$CACHE_PREFIX${(absolutePath.hashCode().toLong() and 0xFFFFFFFFL)}$CACHE_SUFFIX"
            val dest = File(context.cacheDir, safeName)

            if (dest.exists() && dest.length() > 0L) {
                // Touch: aggiorna l'ultimo accesso per un LRU corretto.
                dest.setLastModified(System.currentTimeMillis())
                return dest
            }

            // Copia via root usando cp con path escaped. Gli spazi vengono quotati
            // racchiudendo l'intero path tra virgolette singole e facendo escape di
            // eventuali virgolette singole presenti nel path stesso.
            val escaped = absolutePath.replace("'", "'\\''")
            val destEscaped = dest.absolutePath // cacheDir non ha mai spazi
            val result = Shell.cmd("cp '$escaped' '$destEscaped'").exec()

            return if (result.isSuccess && dest.exists() && dest.length() > 0L) {
                // Dopo ogni nuova copia, sfoltisce la cache se supera il tetto.
                // Il file appena copiato e il piu recente: viene eliminato per ultimo.
                trimCacheIfNeeded(context.cacheDir)
                dest
            } else {
                android.util.Log.e("RootFetcher", "cp failed for: $absolutePath — ${result.err}")
                null
            }
        }

        /**
         * Elimina i file di cache (media_*.tmp) meno usati di recente finche il
         * totale rientra sotto MAX_CACHE_BYTES. Necessario perche i video vengono
         * copiati per intero (anche solo per la thumbnail) e senza pulizia la cache
         * crescerebbe a GB. LRU basato su lastModified, aggiornato a ogni accesso.
         */
        private fun trimCacheIfNeeded(cacheDir: File) {
            synchronized(cacheTrimLock) {
                val files =
                    cacheDir.listFiles { f ->
                        f.name.startsWith(CACHE_PREFIX) && f.name.endsWith(CACHE_SUFFIX)
                    } ?: return
                var total = files.sumOf { it.length() }
                if (total <= MAX_CACHE_BYTES) return
                // Dai meno recenti ai piu recenti: elimina finche si rientra.
                files.sortedBy { it.lastModified() }.forEach { f ->
                    if (total <= MAX_CACHE_BYTES) return
                    val len = f.length()
                    if (f.delete()) total -= len
                }
            }
        }
    }
}
