package com.whatstats.ui.screen.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStatsLinkPreviewResolverTest {
    @Test
    fun metrics_aggregateByDomain() {
        clearLinkPreviewMetrics()

        recordLinkPreviewMetricsForUrl(url = "https://www.example.com/a", success = true, latencyMs = 120)
        recordLinkPreviewMetricsForUrl(url = "https://example.com/b", success = false, latencyMs = 340)
        recordLinkPreviewMetricsForUrl(url = "invalid-url", success = false, latencyMs = 10)

        val metrics = getLinkPreviewMetricsSnapshot()

        val example = metrics["example.com"]
        requireNotNull(example)
        assertEquals(1, example.successCount)
        assertEquals(1, example.failureCount)
        assertEquals(340, example.lastLatencyMs)

        val unknown = metrics["unknown"]
        requireNotNull(unknown)
        assertEquals(0, unknown.successCount)
        assertEquals(1, unknown.failureCount)
    }

    @Test
    fun normalizePreviewUrl_handlesHttpHttpsAndWww() {
        assertEquals("https://example.com", normalizePreviewUrl("https://example.com"))
        assertEquals("http://example.com", normalizePreviewUrl("http://example.com"))
        assertEquals("https://www.example.com", normalizePreviewUrl("www.example.com"))
        assertNull(normalizePreviewUrl("example.com"))
    }

    @Test
    fun extractMetaValues_readsPropertyAndNameTags() {
        val html =
            """
            <html><head>
              <meta property="og:title" content="OG Title" />
              <meta name="twitter:image" content="https://cdn.example.com/img.png" />
              <meta name='og:site_name' content='Example Site'>
            </head></html>
            """.trimIndent()

        val values = extractMetaValues(html)

        assertEquals("OG Title", values["og:title"])
        assertEquals("https://cdn.example.com/img.png", values["twitter:image"])
        assertEquals("Example Site", values["og:site_name"])
    }

    @Test
    fun extractMetaValues_decodesHtmlEntities() {
        val html =
            """
            <meta property="og:title" content="Tom &amp; Jerry &quot;Movie&quot;" />
            """.trimIndent()

        val values = extractMetaValues(html)

        assertEquals("Tom & Jerry \"Movie\"", values["og:title"])
    }

    @Test
    fun extractPreferredImage_usesSecureAndTwitterFallbacks() {
        val secure = extractPreferredImage(mapOf("og:image:secure_url" to "https://img.secure/a.jpg"))
        assertEquals("https://img.secure/a.jpg", secure)

        val twitter = extractPreferredImage(mapOf("twitter:image:src" to "https://img.twitter/a.jpg"))
        assertEquals("https://img.twitter/a.jpg", twitter)
    }

    @Test
    fun extractTagContent_collapsesWhitespace() {
        val html = "<title>  Hello    World  </title>"

        val title = extractTagContent(html, "title")

        assertEquals(" Hello World ", title)
    }

    @Test
    fun resolveRelativeUrl_usesBase() {
        val resolved = resolveRelativeUrl("https://example.com/path/page", "/img/a.png")

        assertEquals("https://example.com/img/a.png", resolved)
    }

    @Test
    fun extractHost_removesWww() {
        assertEquals("example.com", extractHost("https://www.example.com/a"))
        assertTrue(extractHost("not-a-url").isNullOrBlank())
    }

    @Test
    fun isYouTubeHost_detectsShortAndCanonicalDomains() {
        assertTrue(isYouTubeHost("youtu.be"))
        assertTrue(isYouTubeHost("m.youtube.com"))
        assertTrue(!isYouTubeHost("youtube.example.com"))
    }
}
