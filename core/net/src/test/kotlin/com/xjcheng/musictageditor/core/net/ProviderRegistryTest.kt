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

        // Every field now follows the configured tag order (NetEase first, then the
        // international fallbacks), so iTunes beats MusicBrainz for the album here.
        assertEquals("网易歌手", merged.artist)
        assertEquals("iTunes Album", merged.album)
        assertEquals("1999", merged.year)
        assertEquals("4/10", merged.track)
        assertEquals("Pop", merged.genre)
        assertEquals("[00:01.00] 网易歌词", merged.lyrics)
        assertEquals("https://caa/front", merged.coverUrl)
        assertNull(merged.composer)
    }

    @Test
    fun parseOrderIgnoresUnknownRetiredAndDuplicateKeys() {
        assertEquals(
            listOf(ProviderId.NETEASE, ProviderId.APPLE, ProviderId.QQ),
            MergePolicy.parseOrder(" qqmusic,netease,vgmdb,lastfm,apple,netease,qq "),
        )
        assertTrue(MergePolicy.parseOrder("").isEmpty())
    }

    @Test
    fun fromSettingsUsesTheConfiguredOrders() {
        val policy = MergePolicy.fromSettings(
            tagSources = "musicbrainz,netease",
            coverSources = "coverartarchive,apple",
            lyricSources = "kugou,netease",
        )
        assertEquals(listOf(ProviderId.MUSICBRAINZ, ProviderId.NETEASE), policy.priorityFor(MergeField.ARTIST))
        assertEquals(listOf(ProviderId.COVER_ART_ARCHIVE, ProviderId.APPLE), policy.priorityFor(MergeField.COVER))
        assertEquals(listOf(ProviderId.KUGOU, ProviderId.NETEASE), policy.priorityFor(MergeField.LYRICS))
    }

    @Test
    fun emptySettingsFallBackToTheBuiltInOrders() {
        val policy = MergePolicy.fromSettings("", "unknown-source", "")
        assertEquals(MergePolicy.TAG_ORDER, policy.priorityFor(MergeField.TITLE))
        assertEquals(MergePolicy.COVER_ORDER, policy.priorityFor(MergeField.COVER))
        assertEquals(MergePolicy.LYRIC_ORDER, policy.priorityFor(MergeField.LYRICS))
        // Apple's artwork is the highest resolution, so it leads the default cover order.
        assertEquals(ProviderId.APPLE, MergePolicy.COVER_ORDER.first())
        assertEquals(
            listOf(ProviderId.NETEASE, ProviderId.QQ, ProviderId.KUWO, ProviderId.KUGOU),
            MergePolicy.TAG_ORDER.take(4),
        )
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


    @Test
    fun autoMatchMergesTheBestRowFromEachProvider() {
        val netease = FakeProvider(
            ProviderId.NETEASE,
            rows = listOf(row(ProviderId.NETEASE, title = "晴天")),
            candidate = TagCandidate(title = "晴天", artist = "周杰伦", lyrics = "[00:01.00] 歌词"),
        )
        val musicbrainz = FakeProvider(
            ProviderId.MUSICBRAINZ,
            rows = listOf(SearchResult(ProviderId.MUSICBRAINZ, "mb", "晴天", "周杰伦", "叶惠美")),
            candidate = TagCandidate(album = "叶惠美", year = "2003", track = "1/11"),
        )
        val registry = ProviderRegistry(listOf(netease, musicbrainz))

        val merged = registry.autoMatch(SearchQuery(title = "晴天"), limitPerProvider = 3)
        assertEquals("晴天", merged.title)
        assertEquals("周杰伦", merged.artist)
        assertEquals("叶惠美", merged.album)
        assertEquals("2003", merged.year)
        assertEquals("1/11", merged.track)
        assertEquals("[00:01.00] 歌词", merged.lyrics)
    }

    @Test
    fun autoMatchFallsBackToRowsWhenNoCandidateArrives() {
        val provider = FakeProvider(
            ProviderId.QQ,
            rows = listOf(SearchResult(ProviderId.QQ, "q", "晴天", "周杰伦", "叶惠美")),
            candidate = TagCandidate(),
        )
        val merged = ProviderRegistry(listOf(provider)).autoMatch(SearchQuery(title = "晴天"))
        assertEquals("晴天", merged.title)
        assertEquals("周杰伦", merged.artist)
        assertEquals("叶惠美", merged.album)
    }

    @Test
    fun detailForUsesTheResultProvider() {
        val provider = FakeProvider(
            ProviderId.NETEASE,
            candidate = TagCandidate(title = "晴天", lyrics = "[00:01.00] x"),
        )
        val registry = ProviderRegistry(listOf(provider))
        assertEquals("晴天", registry.detailFor(row(ProviderId.NETEASE))?.title)
        // A result from a provider that is not registered yields nothing.
        assertNull(registry.detailFor(row(ProviderId.QQ)))
    }

    @Test
    fun detailForRecordsFailures() {
        val registry = ProviderRegistry(listOf(FakeProvider(ProviderId.NETEASE, fail = true)))
        assertNull(registry.detailFor(row(ProviderId.NETEASE)))
        assertEquals(listOf(ProviderId.NETEASE), registry.failures().map { it.provider })
    }
}
