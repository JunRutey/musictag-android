/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.net.providers

import com.xjcheng.musictageditor.core.net.ProviderId
import com.xjcheng.musictageditor.core.net.SearchQuery
import com.xjcheng.musictageditor.core.net.SearchResult
import com.xjcheng.musictageditor.core.net.http.HttpException
import com.xjcheng.musictageditor.core.net.http.HttpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun appleFixture(path: String): String =
    checkNotNull(object {}.javaClass.getResource(path)) { "missing fixture $path" }.readText()

private class RecordingTransport(private val respond: (String) -> String) : HttpTransport {
    val urls = mutableListOf<String>()
    val headers = mutableListOf<Map<String, String>>()

    override fun get(url: String, headers: Map<String, String>): String {
        urls += url
        this.headers += headers
        return respond(url)
    }

    override fun postForm(url: String, body: String, headers: Map<String, String>): String = "{}"
}

class AppleMusicApiTest {

    private val now = 1_700_000_000L

    @Test
    fun parsesSearchResults() {
        val results = AppleMusicApi.parseSearch(appleFixture("/providers/apple/search.json"), coverSize = 3000)
        assertEquals(2, results.size)

        val first = results[0]
        assertEquals(ProviderId.APPLE, first.provider)
        assertEquals("1440833098", first.id)
        assertEquals("晴天", first.title)
        assertEquals("周杰伦", first.artist)
        assertEquals("叶惠美", first.album)
        assertEquals(269_000, first.durationMs)
        assertTrue(first.coverUrl!!.endsWith("{w}x{h}bb.jpg".replace("{w}", "3000").replace("{h}", "3000")))
    }

    @Test
    fun parsesDetailWithGenreComposerAndTrack() {
        val candidate = AppleMusicApi.parseSong(appleFixture("/providers/apple/song.json"), coverSize = 600)
        assertEquals("晴天", candidate.title)
        assertEquals("周杰伦", candidate.artist)
        assertEquals("叶惠美", candidate.album)
        assertEquals("周杰伦", candidate.composer)
        assertEquals("国语流行 / 流行", candidate.genre)
        assertEquals("3", candidate.track)
        assertEquals("1", candidate.disc)
        assertEquals("2003", candidate.year)
        assertTrue(candidate.coverUrl!!.contains("600x600bb.jpg"))
    }

    @Test
    fun rewritesTheArtworkTemplate() {
        assertEquals(
            "https://x/{w}x{h}bb.jpg".replace("{w}", "1200").replace("{h}", "1200"),
            AppleMusicApi.artworkUrl("https://x/{w}x{h}bb.jpg", 1200),
        )
        // Out-of-range requests are clamped instead of producing a broken URL.
        assertTrue(AppleMusicApi.artworkUrl("https://x/{w}x{h}bb.jpg", 99)!!.contains("120x120"))
        assertNull(AppleMusicApi.artworkUrl(null))
        assertNull(AppleMusicApi.artworkUrl("  "))
    }

    @Test
    fun mapsRegionsToStorefrontsAndLanguages() {
        assertEquals("cn", AppleMusicApi.storefront("zh-CN"))
        assertEquals("us", AppleMusicApi.storefront("en-US"))
        assertEquals("tw", AppleMusicApi.storefront("zh-TW"))
        assertEquals("cn", AppleMusicApi.storefront(""))
        assertEquals("de", AppleMusicApi.storefront("de-DE"))
        assertEquals("zh-Hans", AppleMusicApi.language("cn"))
        assertEquals("zh-Hant", AppleMusicApi.language("zh-TW"))
        assertEquals("ja", AppleMusicApi.language("jp"))
        assertEquals("en", AppleMusicApi.language("us"))
    }

    @Test
    fun picksOnlyAUsableDeveloperToken() {
        val token = AppleMusicApi.findDeveloperToken(appleFixture("/providers/apple/index.js"), now)
        assertNotNull(token)
        assertTrue(AppleMusicApi.isUsable(token!!, now))
        // The expired and the foreign tokens are rejected individually.
        val tokens = Regex("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
            .findAll(appleFixture("/providers/apple/index.js"))
            .map { it.value }
            .toList()
        assertEquals(3, tokens.size)
        assertFalse(AppleMusicApi.isUsable(tokens[0], now))
        assertFalse(AppleMusicApi.isUsable(tokens[1], now))
        assertTrue(AppleMusicApi.isUsable(tokens[2], now))
        assertNull(AppleMusicApi.findDeveloperToken("var x = 1;", now))
    }

    @Test
    fun providerScrapesTheTokenOnceThenSearches() {
        val transport = RecordingTransport { url ->
            when {
                url.endsWith(".js") -> appleFixture("/providers/apple/index.js")
                url.startsWith(AppleMusicApi.WEB_BASE) -> appleFixture("/providers/apple/home.html")
                url.contains("/songs/") -> appleFixture("/providers/apple/song.json")
                else -> appleFixture("/providers/apple/search.json")
            }
        }
        val provider = AppleMusicProvider(transport, region = "cn")

        val results = provider.search(SearchQuery(title = "晴天"), limit = 5)
        assertEquals(2, results.size)
        assertEquals(3, transport.urls.size)
        assertTrue(transport.urls[0].startsWith(AppleMusicApi.WEB_BASE))
        assertTrue(transport.urls[1].endsWith("/assets/index~48ab12cd34ef.js"))
        assertTrue(transport.urls[2].startsWith("https://amp-api.music.apple.com/v1/catalog/cn/search"))
        assertTrue(transport.urls[2].contains("types=songs"))
        assertTrue(transport.urls[2].contains("l=zh-Hans"))
        assertEquals("Bearer ${AppleMusicApi.findDeveloperToken(appleFixture("/providers/apple/index.js"), System.currentTimeMillis() / 1000)}",
            transport.headers[2]["Authorization"])

        // The second call reuses the cached token: no further scrape requests.
        provider.search(SearchQuery(title = "晴天"), limit = 5)
        assertEquals(4, transport.urls.size)
    }

    @Test
    fun a401RefreshesTheTokenExactlyOnce() {
        var searchCalls = 0
        val transport = RecordingTransport { url ->
            when {
                url.endsWith(".js") -> appleFixture("/providers/apple/index.js")
                url.startsWith(AppleMusicApi.WEB_BASE) -> appleFixture("/providers/apple/home.html")
                else -> {
                    searchCalls += 1
                    if (searchCalls == 1) throw HttpException(401, url)
                    appleFixture("/providers/apple/search.json")
                }
            }
        }
        val provider = AppleMusicProvider(transport, region = "cn")

        assertEquals(2, provider.search(SearchQuery(title = "晴天"), limit = 5).size)
        assertEquals(2, searchCalls)
        // home + index twice (initial scrape and refresh), search twice.
        assertEquals(6, transport.urls.size)
    }

    @Test
    fun emptyQueriesDoNotTouchTheNetwork() {
        val transport = RecordingTransport { "{}" }
        assertTrue(AppleMusicProvider(transport).search(SearchQuery(title = "  "), 5).isEmpty())
        assertTrue(transport.urls.isEmpty())
    }

    @Test
    fun detailUsesTheCatalogSongEndpoint() {
        val transport = RecordingTransport { url ->
            when {
                url.endsWith(".js") -> appleFixture("/providers/apple/index.js")
                url.startsWith(AppleMusicApi.WEB_BASE) -> appleFixture("/providers/apple/home.html")
                else -> appleFixture("/providers/apple/song.json")
            }
        }
        val provider = AppleMusicProvider(transport, region = "jp", language = "ja")
        val candidate = provider.detail(SearchResult(ProviderId.APPLE, "1440833098", "晴天", "周杰伦", "叶惠美"))

        assertEquals("晴天", candidate.title)
        assertTrue(transport.urls.last().contains("/catalog/jp/songs/1440833098"))
        assertTrue(transport.urls.last().contains("l=ja"))
    }

    @Test
    fun toleratesBrokenPayloads() {
        assertTrue(AppleMusicApi.parseSearch("junk").isEmpty())
        assertTrue(AppleMusicApi.parseSong("{}").isEmpty())
        assertNull(AppleMusicApi.findIndexScript("<html></html>"))
        assertNull(AppleMusicApi.payload("not-a-token"))
        assertNull(AppleMusicApi.expirySeconds("also-not-a-token"))
    }
}
