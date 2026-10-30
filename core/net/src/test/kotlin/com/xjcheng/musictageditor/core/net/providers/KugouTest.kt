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

private class Stub(private val respond: (String) -> String) : HttpTransport {
    val urls = mutableListOf<String>()

    override fun get(url: String, headers: Map<String, String>): String {
        urls += url
        return respond(url)
    }

    override fun postForm(url: String, body: String, headers: Map<String, String>): String = "{}"
}

class KugouApiTest {

    @Test
    fun parsesSearchResults() {
        val results = KugouApi.parseSearch(fixture("/providers/kugou/search.json"))
        assertEquals(1, results.size)
        val song = results.single()
        assertEquals(ProviderId.KUGOU, song.provider)
        assertEquals("A1B2C3D4E5F60718293A4B5C6D7E8F9012345678", song.id)
        assertEquals("晴天", song.title)
        assertEquals("周杰伦", song.artist)
        assertEquals("叶惠美", song.album)
        assertEquals(269_000, song.durationMs)
    }

    @Test
    fun parsesLyricCandidateAndDownload() {
        val candidate = KugouApi.parseLyricCandidate(fixture("/providers/kugou/candidate.json"))
        assertNotNull(candidate)
        assertEquals("123456789" to "ABCDEF0123456789", candidate!!)

        val lyric = KugouApi.parseLyricDownload(fixture("/providers/kugou/download.json"))
        assertNotNull(lyric)
        assertTrue(lyric!!.contains("[00:05.20] 故事的小黄花"))
    }

    @Test
    fun toleratesBrokenPayloads() {
        assertTrue(KugouApi.parseSearch("junk").isEmpty())
        assertNull(KugouApi.parseLyricCandidate("{}"))
        assertNull(KugouApi.parseLyricDownload("""{"content":""}"""))
        assertNull(KugouApi.parseLyricDownload("""{"content":"not base64 !!"}"""))
    }

    @Test
    fun providerWalksTheTwoStepLyricFlow() {
        val transport = Stub { url ->
            when {
                url.contains("mobilecdn") -> fixture("/providers/kugou/search.json")
                url.contains("krcs.kugou.com") -> fixture("/providers/kugou/candidate.json")
                url.contains("lyrics.kugou.com") -> fixture("/providers/kugou/download.json")
                else -> "{}"
            }
        }
        val provider = KugouProvider(transport)
        val results = provider.search(SearchQuery(title = "晴天"), limit = 5)
        assertEquals(1, results.size)

        val candidate = provider.detail(results[0])
        assertNotNull(candidate.lyrics)
        assertEquals("晴天", candidate.title)
        assertEquals(3, transport.urls.size)
        assertTrue(transport.urls[1].contains("hash=A1B2C3D4"))
        assertTrue(transport.urls[2].contains("accesskey=ABCDEF0123456789"))
    }
}
