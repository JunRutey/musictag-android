/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.net.providers

import com.xjcheng.musictageditor.core.net.ProviderId
import com.xjcheng.musictageditor.core.net.SearchQuery
import com.xjcheng.musictageditor.core.net.http.HttpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun fixture(path: String): String =
    checkNotNull(object {}.javaClass.getResource(path)) { "missing fixture $path" }.readText()

class QqApiTest {

    @Test
    fun parsesSearchResults() {
        val results = QqApi.parseSearch(fixture("/providers/qq/search.json"))
        assertEquals(2, results.size)
        val first = results[0]
        assertEquals(ProviderId.QQ, first.provider)
        assertEquals("003OUlho2HcRHC", first.id)
        assertEquals("晴天", first.title)
        assertEquals("周杰伦", first.artist)
        assertEquals("叶惠美", first.album)
        assertEquals(269_000, first.durationMs)
        assertEquals(
            "https://y.qq.com/music/photo_new/T002R800x800M000000MkMni19ClKG.jpg",
            first.coverUrl,
        )
    }

    @Test
    fun joinsMultipleSingers() {
        val payload = """
            {"req_0":{"data":{"body":{"song":{"list":[
              {"mid":"m1","title":"t","singer":[{"name":"A"},{"name":"B"}],"album":{"name":"al"},"interval":1}
            ]}}}}}
        """.trimIndent()
        assertEquals("A / B", QqApi.parseSearch(payload).single().artist)
    }

    @Test
    fun parsesDetailWithYear() {
        val candidate = QqApi.parseDetail(fixture("/providers/qq/search.json"))
        assertEquals("晴天", candidate.title)
        assertEquals("周杰伦", candidate.albumArtist)
        assertEquals("2003", candidate.year)
        assertNotNull(candidate.coverUrl)
    }

    @Test
    fun stripsJsonpAndDecodesBase64Lyrics() {
        val raw = fixture("/providers/qq/lyric.json")
        val (lyric, translation) = QqApi.parseLyric(raw)
        assertNotNull(lyric)
        assertTrue(lyric!!.contains("[00:05.20] 故事的小黄花"))
        assertNotNull(translation)
        assertTrue(translation!!.contains("Sunny Day"))
        assertTrue(QqApi.stripJsonp(raw).startsWith("{"))
    }

    @Test
    fun unescapesXmlEntities() {
        assertEquals("a < b & c > d \" e ' f", QqApi.unescapeXml("a &lt; b &amp; c &gt; d &quot; e &apos; f"))
    }

    @Test
    fun toleratesBrokenPayloads() {
        assertTrue(QqApi.parseSearch("not json").isEmpty())
        assertTrue(QqApi.parseSearch("{}").isEmpty())
        assertNull(QqApi.parseLyric("garbage").first)
        assertNull(QqApi.decodeText(null))
        assertNull(QqApi.decodeText("not base64 !!"))
    }

    private class FakeTransport(private val responses: Map<String, String>) : HttpTransport {
        val urls = mutableListOf<String>()

        override fun get(url: String, headers: Map<String, String>): String {
            urls += url
            return responses.entries.firstOrNull { url.contains(it.key) }?.value ?: "{}"
        }

        override fun postForm(url: String, body: String, headers: Map<String, String>): String {
            urls += url
            return responses.entries.firstOrNull { url.contains(it.key) }?.value ?: "{}"
        }
    }

    @Test
    fun providerSearchesAndFetchesLyrics() {
        val transport = FakeTransport(
            mapOf(
                "musicu.fcg" to fixture("/providers/qq/search.json"),
                "fcg_query_lyric_new" to fixture("/providers/qq/lyric.json"),
            ),
        )
        val provider = QqProvider(transport)
        val results = provider.search(SearchQuery(title = "晴天"), limit = 2)
        assertEquals(2, results.size)

        val candidate = provider.detail(results[0])
        assertNotNull(candidate.lyrics)
        assertEquals("晴天", candidate.title)
        assertTrue(transport.urls.any { it.contains("songmid=003OUlho2HcRHC") })
    }
}

class KuwoApiTest {

    @Test
    fun parsesSearchResults() {
        val results = KuwoApi.parseSearch(fixture("/providers/kuwo/search.json"))
        assertEquals(1, results.size)
        val song = results.single()
        assertEquals(ProviderId.KUWO, song.provider)
        assertEquals("1234567", song.id)
        assertEquals("晴天", song.title)
        assertEquals("周杰伦", song.artist)
        assertEquals(269_000, song.durationMs)
    }

    @Test
    fun stripsTheMusicIdPrefix() {
        val payload = """{"data":{"list":[{"rid":"MUSIC_42","name":"t","artist":"a","album":"b"}]}}"""
        assertEquals("42", KuwoApi.parseSearch(payload).single().id)
    }

    @Test
    fun rebuildsLrcFromStructuredLines() {
        val (lyric, translation) = KuwoApi.parseLyric(fixture("/providers/kuwo/lyric.json"))
        assertNotNull(lyric)
        assertEquals("[00:00.00]晴天\n[00:05.20]故事的小黄花\n", lyric)
        assertNull(translation)
    }

    @Test
    fun formatsLrcTimestamps() {
        assertEquals("00:00.00", KuwoApi.formatTime(0.0))
        assertEquals("01:02.50", KuwoApi.formatTime(62.5))
        assertEquals("00:00.00", KuwoApi.formatTime(-3.0))
    }

    @Test
    fun buildsUrlsWithTheQueryEscaped() {
        val url = KuwoApi.searchUrl(SearchQuery(title = "晴天 周杰伦"), limit = 10)
        assertTrue(url.contains("rn=10"))
        assertTrue(url.contains("%E6%99%B4%E5%A4%A9"))
        assertTrue(KuwoApi.lyricUrl("42").endsWith("musicId=42"))
    }

    @Test
    fun toleratesBrokenPayloads() {
        assertTrue(KuwoApi.parseSearch("nope").isEmpty())
        assertNull(KuwoApi.parseLyric("{}").first)
    }
}
