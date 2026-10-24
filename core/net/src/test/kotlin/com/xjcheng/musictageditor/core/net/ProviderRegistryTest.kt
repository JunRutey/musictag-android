/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.net

import com.xjcheng.musictageditor.core.net.http.HttpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryTest {

    private class FakeProvider(
        override val id: ProviderId,
        override val capabilities: Set<Provider.Capability> = setOf(Provider.Capability.TAGS),
        private val rows: List<SearchResult> = emptyList(),
        private val candidate: TagCandidate = TagCandidate(),
        private val fail: Boolean = false,
    ) : Provider {
        override fun search(query: SearchQuery, limit: Int): List<SearchResult> {
            if (fail) error("boom from ${id.name}")
            return rows
        }

        override fun detail(result: SearchResult): TagCandidate {
            if (fail) error("boom from ${id.name}")
            return candidate
        }
    }

    private fun row(provider: ProviderId, id: String = "1", title: String = "t") =
        SearchResult(provider, id, title, "artist", "album")

    @Test
    fun searchesEveryTagProviderAndSkipsBrokenOnes() {
        val registry = ProviderRegistry(
            listOf(
                FakeProvider(ProviderId.NETEASE, rows = listOf(row(ProviderId.NETEASE))),
                FakeProvider(ProviderId.QQ, fail = true),
                FakeProvider(ProviderId.KUWO, rows = listOf(row(ProviderId.KUWO), row(ProviderId.KUWO, "2"))),
                FakeProvider(ProviderId.COVER_ART_ARCHIVE, capabilities = setOf(Provider.Capability.COVER)),
            ),
        )

        val results = registry.searchAll(SearchQuery(title = "x"), limitPerProvider = 5)
        assertEquals(3, results.size)
        assertTrue(results.none { it.provider == ProviderId.QQ })
        assertEquals(listOf("QQ"), registry.failures().map { it.provider.name })
        assertTrue(registry.failures().single().message.contains("boom"))
    }

    @Test
    fun honoursThePerProviderLimit() {
        val rows = (1..10).map { row(ProviderId.NETEASE, id = "$it") }
        val registry = ProviderRegistry(listOf(FakeProvider(ProviderId.NETEASE, rows = rows)))
        assertEquals(3, registry.searchAll(SearchQuery(title = "x"), limitPerProvider = 3).size)
    }

    @Test
    fun mergesFieldsByPriority() {
        val registry = ProviderRegistry(emptyList())
        val merged = registry.merge(
            mapOf(
                ProviderId.NETEASE to TagCandidate(artist = "网易歌手", lyrics = "[00:01.00] 网易歌词"),
                ProviderId.MUSICBRAINZ to TagCandidate(album = "MB Album", year = "1999", track = "4/10"),
                ProviderId.ITUNES to TagCandidate(album = "iTunes Album", genre = "Pop"),
                ProviderId.COVER_ART_ARCHIVE to TagCandidate(coverUrl = "https://caa/front"),
            ),
        )

        // Title/artist prefer the Chinese services; album-level fields prefer curated sources.
        assertEquals("网易歌手", merged.artist)
        assertEquals("MB Album", merged.album)
        assertEquals("1999", merged.year)
        assertEquals("4/10", merged.track)
        assertEquals("Pop", merged.genre)
        assertEquals("[00:01.00] 网易歌词", merged.lyrics)
        assertEquals("https://caa/front", merged.coverUrl)
        assertNull(merged.composer)
    }

    @Test
    fun blankValuesDoNotWinOverLaterProviders() {
        val registry = ProviderRegistry(emptyList())
        val merged = registry.merge(
            mapOf(
                ProviderId.MUSICBRAINZ to TagCandidate(album = "   "),
                ProviderId.ITUNES to TagCandidate(album = "Real Album"),
            ),
        )
        assertEquals("Real Album", merged.album)
    }

    @Test
    fun collectSkipsEmptyCandidatesAndRecordsFailures() {
        val registry = ProviderRegistry(
            listOf(
                FakeProvider(ProviderId.NETEASE, candidate = TagCandidate(title = "ok")),
                FakeProvider(ProviderId.QQ, candidate = TagCandidate()),
                FakeProvider(ProviderId.KUWO, fail = true),
            ),
        )
        val candidates = registry.collect(
            listOf(ProviderId.NETEASE, ProviderId.QQ, ProviderId.KUWO),
            row(ProviderId.NETEASE),
        )
        assertEquals(setOf(ProviderId.NETEASE), candidates.keys)
        assertEquals(listOf(ProviderId.KUWO), registry.failures().map { it.provider })
    }

    @Test
    fun customPolicyOverridesTheDefault() {
        val policy = MergePolicy(
            mapOf(MergeField.ARTIST to listOf(ProviderId.MUSICBRAINZ, ProviderId.NETEASE)),
        )
        val registry = ProviderRegistry(emptyList(), policy)
        val merged = registry.merge(
            mapOf(
                ProviderId.NETEASE to TagCandidate(artist = "netease"),
                ProviderId.MUSICBRAINZ to TagCandidate(artist = "musicbrainz"),
            ),
        )
        assertEquals("musicbrainz", merged.artist)
    }

    @Test
    fun registryExposesProvidersAndClearsFailures() {
        val provider = FakeProvider(ProviderId.NETEASE, fail = true)
        val registry = ProviderRegistry(listOf(provider))
        registry.searchAll(SearchQuery(title = "x"), 1)
        assertEquals(1, registry.failures().size)
        registry.clearFailures()
        assertTrue(registry.failures().isEmpty())
        assertTrue(registry.provider(ProviderId.QQ) == null)
        assertEquals(ProviderId.NETEASE, registry.available.single().id)
    }

}
