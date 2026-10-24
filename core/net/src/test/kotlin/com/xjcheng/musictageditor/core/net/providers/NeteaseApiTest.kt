/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.net.providers

import com.xjcheng.musictageditor.core.net.Provider
import com.xjcheng.musictageditor.core.net.ProviderId
import com.xjcheng.musictageditor.core.net.SearchQuery
import com.xjcheng.musictageditor.core.net.SearchResult
import com.xjcheng.musictageditor.core.net.http.HttpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseApiTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/providers/netease/$name")) { "missing fixture $name" }
            .readText()

    @Test
    fun parsesSearchResults() {
        val results = NeteaseApi.parseSearch(fixture("search.json"))
        assertEquals(2, results.size)
        val first = results[0]
        assertEquals(ProviderId.NETEASE, first.provider)
        assertEquals("186016", first.id)
        assertEquals("晴天", first.title)
        assertEquals("周杰伦", first.artist)
        assertEquals("叶惠美", first.album)
        assertEquals(269000, first.durationMs)
        assertEquals("https://p1.music.126.net/cover-a.jpg", first.coverUrl)
        assertEquals("陈奕迅", results[1].artist)
    }

    @Test
    fun honoursTheResultLimit() {
        assertEquals(1, NeteaseApi.parseSearch(fixture("search.json"), limit = 1).size)
    }

    @Test
    fun toleratesEmptyAndBrokenPayloads() {
        assertTrue(NeteaseApi.parseSearch("""{"result":{"songs":[]}}""").isEmpty())
        assertTrue(NeteaseApi.parseSearch("not json").isEmpty())
        assertTrue(NeteaseApi.parseSearch("""{"code":200}""").isEmpty())
    }

    @Test
    fun parsesDetailIntoACandidate() {
        val candidate = NeteaseApi.parseDetail(fixture("detail.json"))
        assertEquals("晴天", candidate.title)
        assertEquals("周杰伦", candidate.artist)
        assertEquals("叶惠美", candidate.album)
        assertEquals("周杰伦", candidate.albumArtist)
        assertEquals("2003", candidate.year)
        assertEquals("1", candidate.track)
        assertEquals("https://p1.music.126.net/cover-a.jpg", candidate.coverUrl)
    }

    @Test
    fun parsesLyricsWithTranslation() {
        val (lyric, translation) = NeteaseApi.parseLyric(fixture("lyric.json"))
        assertNotNull(lyric)
        assertTrue(lyric!!.contains("[00:05.20] 故事的小黄花"))
        assertNotNull(translation)
        assertTrue(translation!!.contains("Sunny Day"))
    }

    @Test
    fun missingTranslationIsNull() {
        val (lyric, translation) = NeteaseApi.parseLyric("""{"lrc":{"lyric":"[00:01.00] only here"}}""")
        assertEquals("[00:01.00] only here", lyric)
        assertNull(translation)
    }

    @Test
    fun requestBodiesCarryTheQueryAndId() {
        val body = NeteaseApi.searchBody(SearchQuery(title = "晴天", artist = "周杰伦"), limit = 5)
        assertTrue(body, body.contains(""""s":"晴天 周杰伦""""))
        assertTrue(body, body.contains(""""limit":5"""))
        assertTrue(NeteaseApi.detailBody("186016").contains("186016"))
        assertTrue(NeteaseApi.lyricBody("186016").contains("186016"))
    }

    @Test
    fun escapesQuotesInTheQuery() {
        val body = NeteaseApi.searchBody(SearchQuery(title = """say "hi" \\ bye"""), limit = 1)
        assertTrue(body, body.contains("""\"hi\""""))
        assertTrue(body, body.contains("""\\\\"""))
    }

    /** Records requests and answers with recorded fixtures, so the provider is testable. */
    private class FakeTransport(
        private val search: String,
        private val detail: String,
        private val lyric: String,
    ) : HttpTransport {
        val calls = mutableListOf<Pair<String, String>>()

        override fun get(url: String, headers: Map<String, String>): String {
            calls += url to ""
            return "{}"
        }

        override fun postForm(url: String, body: String, headers: Map<String, String>): String {
            calls += url to body
            return when {
                url.endsWith("cloudsearch/pc") -> search
                url.endsWith("song/detail") -> detail
                url.endsWith("song/lyric") -> lyric
                else -> "{}"
            }
        }
    }

    @Test
    fun providerSignsEveryRequestAndMergesLyrics() {
        val transport = FakeTransport(
            search = fixture("search.json"),
            detail = fixture("detail.json"),
            lyric = fixture("lyric.json"),
        )
        val provider = NeteaseProvider(transport) { "0123456789abcdef" }

        assertEquals(
            setOf(Provider.Capability.TAGS, Provider.Capability.LYRICS, Provider.Capability.COVER),
            provider.capabilities,
        )

        val results = provider.search(SearchQuery(title = "晴天"), limit = 2)
        assertEquals(2, results.size)

        val candidate = provider.detail(results[0])
        assertEquals("晴天", candidate.title)
        assertNotNull(candidate.lyrics)
        assertNotNull(candidate.lyricsTranslation)

        // Three signed calls: search, detail, lyric. Each body must be a weapi payload.
        assertEquals(3, transport.calls.size)
        for ((url, body) in transport.calls) {
            assertTrue(url, url.startsWith("https://music.163.com/weapi/"))
            assertTrue(body, body.contains("params=") && body.contains("encSecKey="))
            assertTrue(body, !body.contains("晴天"))
        }
    }

    @Test
    fun providerParsesAnEmptySearchGracefully() {
        val empty = """{"result":{"songs":[]}}"""
        val provider = NeteaseProvider(FakeTransport(empty, "{}", "{}")) { "0123456789abcdef" }
        assertTrue(provider.search(SearchQuery(title = "nothing"), limit = 5).isEmpty())
    }

    @Test
    fun searchResultDefaultsAreSane() {
        val result = SearchResult(ProviderId.NETEASE, "1", "t", "a", "b")
        assertEquals(0, result.durationMs)
        assertNull(result.coverUrl)
    }
}
