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

private fun fixture(path: String): String =
    checkNotNull(object {}.javaClass.getResource(path)) { "missing fixture $path" }.readText()

private class StubTransport(private val response: (String) -> String) : HttpTransport {
    val urls = mutableListOf<String>()

    override fun get(url: String, headers: Map<String, String>): String {
        urls += url
        return response(url)
    }

    override fun postForm(url: String, body: String, headers: Map<String, String>): String {
        urls += url
        return response(url)
    }
}

class ItunesApiTest {

    @Test
    fun parsesSearchResults() {
        val results = ItunesApi.parseSearch(fixture("/providers/itunes/search.json"))
        assertEquals(2, results.size)
        val first = results[0]
        assertEquals(ProviderId.ITUNES, first.provider)
        assertEquals("255383726", first.id)
        assertEquals("晴天", first.title)
        assertEquals("周杰伦", first.artist)
        assertEquals("叶惠美", first.album)
        assertEquals(269_000, first.durationMs)
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music/aa/bb/cc/600x600bb.jpg",
            first.coverUrl,
        )
    }

    @Test
    fun parsesDetailWithTrackAndGenre() {
        val candidate = ItunesApi.parseDetail(fixture("/providers/itunes/lookup.json"))
        assertEquals("晴天", candidate.title)
        assertEquals("Pop", candidate.genre)
        assertEquals("2003", candidate.year)
        assertEquals("1/11", candidate.track)
        assertNotNull(candidate.coverUrl)
    }

    @Test
    fun rewritesArtworkSizeAndToleratesJunk() {
        assertEquals(
            "https://x/300x300bb.jpg",
            ItunesApi.artwork("https://x/100x100bb.jpg", 300),
        )
        assertNull(ItunesApi.artwork(null, 300))
        // No size segment: kept as-is, because the URL still points at usable artwork.
        assertEquals("https://x/no-size.jpg", ItunesApi.artwork("https://x/no-size.jpg", 300))
    }

    @Test
    fun buildsSearchAndLookupUrls() {
        val url = ItunesApi.searchUrl(SearchQuery(title = "晴天"), limit = 5, country = "TW")
        assertTrue(url.contains("limit=5"))
        assertTrue(url.contains("country=TW"))
        assertTrue(ItunesApi.lookupUrl("42").contains("id=42"))
    }

    @Test
    fun providerSearchesAndLooksUp() {
        val transport = StubTransport { url ->
            if (url.contains("/lookup")) fixture("/providers/itunes/lookup.json")
            else fixture("/providers/itunes/search.json")
        }
        val provider = ItunesProvider(transport, country = "TW")
        val results = provider.search(SearchQuery(title = "晴天"), limit = 2)
        assertEquals(2, results.size)
        assertEquals("晴天", provider.detail(results[0]).title)
        assertTrue(transport.urls.any { it.contains("country=TW") })
    }
}

class MusicBrainzApiTest {

    @Test
    fun parsesRecordings() {
        val results = MusicBrainzApi.parseSearch(fixture("/providers/musicbrainz/search.json"))
        assertEquals(1, results.size)
        val recording = results.single()
        assertEquals(ProviderId.MUSICBRAINZ, recording.provider)
        assertEquals("8f3471b5-7e6a-4a1f-9e29-0b2c1a6f0d11", recording.id)
        assertEquals("晴天", recording.title)
        assertEquals("周杰伦", recording.artist)
        assertEquals("叶惠美", recording.album)
        assertEquals(269_000, recording.durationMs)
        assertEquals(
            "https://coverartarchive.org/release/5b11a2c3-1111-2222-3333-444455556666/front-500",
            recording.coverUrl,
        )
    }

    @Test
    fun parsesRecordingDetail() {
        val candidate = MusicBrainzApi.parseRecording(fixture("/providers/musicbrainz/recording.json"))
        assertEquals("晴天", candidate.title)
        assertEquals("叶惠美", candidate.album)
        assertEquals("2003", candidate.year)
        assertNotNull(candidate.coverUrl)
    }

    @Test
    fun parsesReleaseDetailWithLabel() {
        val release = """
            {"id":"rel","title":"叶惠美","date":"2003-07-31","artist-credit":[{"name":"周杰伦"}],
             "label-info":[{"label":{"name":"Sony Music"}}]}
        """.trimIndent()
        val candidate = MusicBrainzApi.parseDetail(release)
        assertEquals("Label: Sony Music", candidate.comment)
        assertEquals("叶惠美", candidate.album)
    }

    @Test
    fun sendsItsUserAgentAndToleratesJunk() {
        var seen: Map<String, String> = emptyMap()
        val transport = object : HttpTransport {
            override fun get(url: String, headers: Map<String, String>): String {
                seen = headers
                return fixture("/providers/musicbrainz/search.json")
            }

            override fun postForm(url: String, body: String, headers: Map<String, String>): String = "{}"
        }
        MusicBrainzProvider(transport).search(SearchQuery(title = "x"), 1)
        assertTrue(seen.getValue("User-Agent").startsWith("MusicTag/"))

        assertTrue(MusicBrainzApi.parseSearch("junk").isEmpty())
        assertTrue(MusicBrainzApi.parseDetail("{}").isEmpty())
        assertTrue(MusicBrainzApi.parseRecording("junk").toString().contains("TagCandidate"))
    }
}

class CoverArtArchiveTest {

    @Test
    fun buildsFrontCoverUrls() {
        assertEquals(
            "https://coverartarchive.org/release/abc/front-500",
            CoverArtArchiveProvider.frontCoverUrl("abc"),
        )
        assertEquals(
            "https://coverartarchive.org/release/abc/front-250",
            CoverArtArchiveProvider.frontCoverUrl("abc", size = 250),
        )
    }

    @Test
    fun providerContributesOnlyArtwork() {
        val provider = CoverArtArchive(StubTransport { "png" })
        assertTrue(provider.search(SearchQuery(title = "x"), 5).isEmpty())
        assertEquals(
            "https://coverartarchive.org/release/rel/front-500",
            provider.detail(SearchResult(ProviderId.COVER_ART_ARCHIVE, "rel", "", "", "")).coverUrl,
        )
        assertTrue(provider.hasFrontCover("rel"))
        assertTrue(!CoverArtArchive(StubTransport { throw RuntimeException("404") }).hasFrontCover("rel"))
    }
}
