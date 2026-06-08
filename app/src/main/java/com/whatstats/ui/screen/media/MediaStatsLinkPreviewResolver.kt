package com.whatstats.ui.screen.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import kotlin.system.measureTimeMillis

internal data class LinkPreviewDomainMetrics(
    val successCount: Int,
    val failureCount: Int,
    val lastLatencyMs: Long,
)

private data class MutableLinkPreviewDomainMetrics(
    var successCount: Int = 0,
    var failureCount: Int = 0,
    var lastLatencyMs: Long = 0,
)

private val linkPreviewMetricsLock = Any()
private val linkPreviewMetrics = mutableMapOf<String, MutableLinkPreviewDomainMetrics>()

internal suspend fun loadLinkPreviewMetadata(
    url: String,
    cache: MutableMap<String, LinkPreviewCacheEntry>,
): LinkPreviewMetadata? =
    withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cacheEntry = cache[url]
        if (cacheEntry != null && cacheEntry.expiresAtMillis > now) {
            return@withContext cacheEntry.metadata
        }

        val metadata = fetchLinkPreviewMetadata(url)
        cache[url] =
            LinkPreviewCacheEntry(
                expiresAtMillis = now + if (metadata != null) LINK_PREVIEW_CACHE_TTL_MS else LINK_PREVIEW_FAILURE_TTL_MS,
                metadata = metadata,
            )
        metadata
    }

private fun fetchLinkPreviewMetadata(url: String): LinkPreviewMetadata? {
    val normalizedUrl = normalizePreviewUrl(url) ?: return null

    var result: LinkPreviewMetadata? = null
    val measuredLatencyMs =
        measureTimeMillis {
            result =
                if (isYouTubeHost(extractHost(normalizedUrl).orEmpty())) {
                    fetchYouTubeOEmbedMetadata(normalizedUrl) ?: fetchHtmlMetadataWithUserAgentRetry(normalizedUrl)
                } else {
                    fetchHtmlMetadataWithUserAgentRetry(normalizedUrl)
                }
        }

    recordLinkPreviewMetricsForUrl(
        url = normalizedUrl,
        success = result != null,
        latencyMs = measuredLatencyMs,
    )
    return result
}

private fun fetchHtmlMetadataWithUserAgentRetry(url: String): LinkPreviewMetadata? {
    LINK_PREVIEW_USER_AGENTS.forEach { userAgent ->
        val metadata = fetchHtmlMetadataOnce(url, userAgent)
        if (metadata != null) return metadata
    }
    return null
}

private fun fetchHtmlMetadataOnce(
    normalizedUrl: String,
    userAgent: String,
): LinkPreviewMetadata? {
    val connection =
        (URL(normalizedUrl).openConnection() as? HttpURLConnection)
            ?: return null

    return try {
        connection.instanceFollowRedirects = true
        connection.connectTimeout = LINK_PREVIEW_CONNECT_TIMEOUT_MS
        connection.readTimeout = LINK_PREVIEW_READ_TIMEOUT_MS
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", userAgent)
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate")

        val responseCode = connection.responseCode
        if (responseCode !in 200..399) return null

        val contentType = connection.contentType.orEmpty()
        if (!contentType.contains("text/html", ignoreCase = true)) return null

        val charset = extractCharsetFromContentType(contentType)
        val html = readHtmlSample(connection, charset)
        if (html.isBlank()) return null

        val metaValues = extractMetaValues(html)
        val title =
            firstNotBlank(
                metaValues["og:title"],
                metaValues["twitter:title"],
                metaValues["title"],
                extractTagContent(html, "title"),
            ) ?: return null

        val image = resolveRelativeUrl(normalizedUrl, extractPreferredImage(metaValues))
        val siteName = firstNotBlank(metaValues["og:site_name"], metaValues["application-name"], metaValues["twitter:site"])

        LinkPreviewMetadata(
            title = title,
            imageUrl = image,
            siteName = siteName,
        )
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

private fun fetchYouTubeOEmbedMetadata(url: String): LinkPreviewMetadata? {
    val endpoint = "$YOUTUBE_OEMBED_ENDPOINT?url=${urlEncode(url)}&format=json"
    val connection =
        (URL(endpoint).openConnection() as? HttpURLConnection)
            ?: return null

    return try {
        connection.instanceFollowRedirects = true
        connection.connectTimeout = LINK_PREVIEW_CONNECT_TIMEOUT_MS
        connection.readTimeout = LINK_PREVIEW_READ_TIMEOUT_MS
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", LINK_PREVIEW_USER_AGENTS.first())
        connection.setRequestProperty("Accept", "application/json")

        if (connection.responseCode !in 200..399) return null

        val body = readHtmlSample(connection, Charsets.UTF_8)
        if (body.isBlank()) return null

        val title = extractJsonStringField(body, "title") ?: return null
        val thumbnail = extractJsonStringField(body, "thumbnail_url")
        val provider = extractJsonStringField(body, "author_name") ?: extractJsonStringField(body, "provider_name")

        LinkPreviewMetadata(
            title = title,
            imageUrl = thumbnail,
            siteName = provider,
        )
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

internal fun recordLinkPreviewMetricsForUrl(
    url: String,
    success: Boolean,
    latencyMs: Long,
) {
    val domain = extractHost(url) ?: LINK_PREVIEW_UNKNOWN_DOMAIN
    synchronized(linkPreviewMetricsLock) {
        val current = linkPreviewMetrics.getOrPut(domain) { MutableLinkPreviewDomainMetrics() }
        if (success) {
            current.successCount += 1
        } else {
            current.failureCount += 1
        }
        current.lastLatencyMs = latencyMs.coerceAtLeast(0)
    }
}

internal fun getLinkPreviewMetricsSnapshot(): Map<String, LinkPreviewDomainMetrics> =
    synchronized(linkPreviewMetricsLock) {
        linkPreviewMetrics.mapValues { (_, m) ->
            LinkPreviewDomainMetrics(
                successCount = m.successCount,
                failureCount = m.failureCount,
                lastLatencyMs = m.lastLatencyMs,
            )
        }
    }

internal fun clearLinkPreviewMetrics() {
    synchronized(linkPreviewMetricsLock) {
        linkPreviewMetrics.clear()
    }
}

internal fun extractTagContent(
    html: String,
    tagName: String,
): String? {
    val pattern =
        Regex(
            """<$tagName[^>]*>(.*?)</$tagName>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    return pattern.find(html)?.groupValues?.getOrNull(1)?.replace("\\s+".toRegex(), " ")?.let(::decodeHtmlEntities)
}

internal fun readHtmlSample(
    connection: HttpURLConnection,
    charset: Charset,
): String =
    BufferedInputStream(decodeInputStream(connection)).use { input ->
        val buffer = ByteArray(4096)
        val out = ByteArray(LINK_PREVIEW_MAX_BYTES)
        var totalRead = 0

        while (totalRead < LINK_PREVIEW_MAX_BYTES) {
            val toRead = minOf(buffer.size, LINK_PREVIEW_MAX_BYTES - totalRead)
            val read = input.read(buffer, 0, toRead)
            if (read <= 0) break
            System.arraycopy(buffer, 0, out, totalRead, read)
            totalRead += read
        }

        if (totalRead <= 0) {
            ""
        } else {
            String(out, 0, totalRead, charset)
        }
    }

internal fun extractMetaValues(html: String): Map<String, String> {
    val metaTagPattern = Regex("""<meta\s+[^>]*>""", RegexOption.IGNORE_CASE)
    val attrPattern = Regex("""([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*=\s*(\"([^\"]*)\"|'([^']*)'|([^\s\"'=<>`]+))""")

    val values = mutableMapOf<String, String>()
    metaTagPattern.findAll(html).forEach { metaTag ->
        val attributes = mutableMapOf<String, String>()
        attrPattern.findAll(metaTag.value).forEach { attrMatch ->
            val key = attrMatch.groupValues[1].lowercase(Locale.ROOT)
            val value =
                attrMatch.groupValues[3]
                    .ifBlank { attrMatch.groupValues[4] }
                    .ifBlank { attrMatch.groupValues[5] }
                    .trim()
            if (value.isNotEmpty()) {
                attributes[key] = decodeHtmlEntities(value)
            }
        }

        val property = attributes["property"]?.lowercase(Locale.ROOT)
        val name = attributes["name"]?.lowercase(Locale.ROOT)
        val content = attributes["content"]
        if (!content.isNullOrBlank()) {
            if (!property.isNullOrBlank()) {
                values[property] = content
            }
            if (!name.isNullOrBlank()) {
                values[name] = content
            }
        }
    }

    return values
}

internal fun decodeHtmlEntities(raw: String): String {
    val named =
        raw
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")

    val numericEntity = Regex("&#(x?[0-9A-Fa-f]+);")
    return numericEntity.replace(named) { match ->
        val token = match.groupValues[1]
        val codePoint =
            try {
                if (token.startsWith("x", ignoreCase = true)) token.substring(1).toInt(16) else token.toInt()
            } catch (_: Exception) {
                return@replace match.value
            }
        try {
            String(Character.toChars(codePoint))
        } catch (_: Exception) {
            match.value
        }
    }
}

internal fun extractPreferredImage(metaValues: Map<String, String>): String? =
    firstNotBlank(
        metaValues["og:image:secure_url"],
        metaValues["og:image:url"],
        metaValues["og:image"],
        metaValues["twitter:image:src"],
        metaValues["twitter:image"],
    )

private fun decodeInputStream(connection: HttpURLConnection) =
    when (connection.contentEncoding.orEmpty().lowercase(Locale.ROOT)) {
        "gzip" -> GZIPInputStream(connection.inputStream)
        "deflate" -> InflaterInputStream(connection.inputStream)
        else -> connection.inputStream
    }

private fun extractJsonStringField(
    json: String,
    field: String,
): String? {
    val pattern = Regex("\"$field\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
    val raw = pattern.find(json)?.groupValues?.getOrNull(1) ?: return null
    return raw
        .replace("\\/", "/")
        .replace("\\\"", "\"")
        .replace("\\n", " ")
        .replace("\\r", " ")
        .replace("\\t", " ")
        .trim()
        .ifBlank { null }
}

internal fun resolveRelativeUrl(
    baseUrl: String,
    maybeRelativeUrl: String?,
): String? {
    if (maybeRelativeUrl.isNullOrBlank()) return null
    return try {
        URI(baseUrl).resolve(maybeRelativeUrl).toString()
    } catch (_: Exception) {
        maybeRelativeUrl
    }
}

internal fun extractCharsetFromContentType(contentType: String): Charset {
    val charsetName =
        Regex("""charset=([a-zA-Z0-9_-]+)""", RegexOption.IGNORE_CASE)
            .find(contentType)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
    return try {
        if (charsetName.isNullOrBlank()) Charsets.UTF_8 else Charset.forName(charsetName)
    } catch (_: Exception) {
        Charsets.UTF_8
    }
}

internal fun normalizePreviewUrl(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return null
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    return if (trimmed.startsWith("www.")) "https://$trimmed" else null
}

internal fun extractHost(url: String): String? =
    try {
        URI(url).host?.removePrefix("www.")
    } catch (_: Exception) {
        null
    }

internal fun isYouTubeHost(host: String): Boolean {
    val lower = host.lowercase(Locale.ROOT)
    return lower == "youtu.be" || lower.endsWith(".youtu.be") || lower == "youtube.com" || lower.endsWith(".youtube.com")
}

private fun firstNotBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }?.trim()

private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

private const val LINK_PREVIEW_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
private const val LINK_PREVIEW_FAILURE_TTL_MS = 10L * 60 * 1000
private const val LINK_PREVIEW_CONNECT_TIMEOUT_MS = 3000
private const val LINK_PREVIEW_READ_TIMEOUT_MS = 4000
private const val LINK_PREVIEW_MAX_BYTES = 64 * 1024
private const val LINK_PREVIEW_UNKNOWN_DOMAIN = "unknown"
private const val YOUTUBE_OEMBED_ENDPOINT = "https://www.youtube.com/oembed"
private val LINK_PREVIEW_USER_AGENTS =
    listOf(
        "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 WhatStat/1.0",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
        "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)",
    )
