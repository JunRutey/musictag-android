/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.net

/** A field the merge step can fill from several providers. */
enum class MergeField {
    TITLE,
    ARTIST,
    ALBUM,
    ALBUM_ARTIST,
    GENRE,
    COMPOSER,
    TRACK,
    DISC,
    YEAR,
    COMMENT,
    LYRICS,
    LYRICS_TRANSLATION,
    COVER,
}

/**
 * Which provider wins for each field.
 *
 * The original app had a per-source priority list; this keeps the same idea but makes it
 * explicit and testable. Curated databases win for album-level fields, while the Chinese
 * services win for title/artist and lyrics, and Cover Art Archive wins for artwork.
 */
data class MergePolicy(
    val order: Map<MergeField, List<ProviderId>> = DEFAULT_ORDER,
) {
    fun priorityFor(field: MergeField): List<ProviderId> =
        order[field] ?: DEFAULT_ORDER.getValue(field)

    companion object {
        private val CHINESE_TAGS = listOf(
            ProviderId.NETEASE, ProviderId.QQ, ProviderId.KUWO, ProviderId.KUGOU,
            ProviderId.ITUNES, ProviderId.MUSICBRAINZ, ProviderId.VGMDB, ProviderId.LASTFM,
        )
        private val CURATED = listOf(
            ProviderId.MUSICBRAINZ, ProviderId.ITUNES, ProviderId.VGMDB,
            ProviderId.NETEASE, ProviderId.QQ, ProviderId.KUWO, ProviderId.KUGOU,
            ProviderId.LASTFM,
        )

        val DEFAULT_ORDER: Map<MergeField, List<ProviderId>> = mapOf(
            MergeField.TITLE to CHINESE_TAGS,
            MergeField.ARTIST to CHINESE_TAGS,
            MergeField.ALBUM to CURATED,
            MergeField.ALBUM_ARTIST to CURATED,
            MergeField.GENRE to CURATED,
            MergeField.COMPOSER to CURATED,
            MergeField.TRACK to CURATED,
            MergeField.DISC to CURATED,
            MergeField.YEAR to CURATED,
            MergeField.COMMENT to CURATED,
            MergeField.LYRICS to listOf(
                ProviderId.NETEASE, ProviderId.QQ, ProviderId.KUGOU, ProviderId.KUWO,
            ),
            MergeField.LYRICS_TRANSLATION to listOf(ProviderId.NETEASE, ProviderId.QQ),
            MergeField.COVER to listOf(
                ProviderId.COVER_ART_ARCHIVE, ProviderId.ITUNES, ProviderId.NETEASE,
                ProviderId.QQ, ProviderId.MUSICBRAINZ, ProviderId.KUWO,
            ),
        )

        val DEFAULT = MergePolicy()
    }
}

/** A provider that failed during a combined search, kept for the diagnostics screen. */
data class ProviderFailure(val provider: ProviderId, val message: String)

/**
 * The set of providers the app talks to.
 *
 * Every call is defensive: a provider that throws is recorded and skipped so one broken
 * service cannot take down a search across all of them.
 */
class ProviderRegistry(
    providers: List<Provider>,
    private val policy: MergePolicy = MergePolicy.DEFAULT,
) {
    private val byId: Map<ProviderId, Provider> = providers.associateBy { it.id }
    private val failures = mutableListOf<ProviderFailure>()

    val available: List<Provider> get() = byId.values.toList()

    fun provider(id: ProviderId): Provider? = byId[id]

    /** Last failures, newest last. Cleared by [clearFailures]. */
    fun failures(): List<ProviderFailure> = failures.toList()

    fun clearFailures() {
        failures.clear()
    }

    /**
     * Runs [query] against every provider that offers tags, concatenating the results in
     * provider-priority order and keeping at most [limitPerProvider] rows from each.
     */
    fun searchAll(query: SearchQuery, limitPerProvider: Int = 10): List<SearchResult> {
        val ordered = MergePolicy.DEFAULT_ORDER.getValue(MergeField.TITLE)
            .mapNotNull { byId[it] }
        return ordered.filter { Provider.Capability.TAGS in it.capabilities }.flatMap { provider ->
            runCatching { provider.search(query, limitPerProvider) }
                .onFailure { failures += ProviderFailure(provider.id, it.message ?: it.toString()) }
                .getOrDefault(emptyList())
                .take(limitPerProvider)  // a provider may ignore the limit; enforce it here
        }
    }

    /** Fetches one candidate from every provider that was asked, again tolerating failures. */
    fun collect(ids: List<ProviderId>, result: SearchResult): Map<ProviderId, TagCandidate> =
        ids.mapNotNull { byId[it] }
            .filter { Provider.Capability.TAGS in it.capabilities || Provider.Capability.COVER in it.capabilities }
            .mapNotNull { provider ->
                runCatching { provider.detail(result) }
                    .onFailure { failures += ProviderFailure(provider.id, it.message ?: it.toString()) }
                    .getOrNull()
                    ?.takeIf { !it.isEmpty() }
                    ?.let { provider.id to it }
            }
            .toMap()

    /**
     * Searches every tag provider, asks each one that answered for the details of its best
     * row, and merges the results with [merge]. This is the "fill everything automatically"
     * path used by the editor.
     */
    fun autoMatch(query: SearchQuery, limitPerProvider: Int = 5): TagCandidate {
        val rows = searchAll(query, limitPerProvider)
            .groupBy { it.provider }
            .mapValues { (_, providerRows) -> providerRows.first() }

        val candidates = rows.mapNotNull { (providerId, row) ->
            val provider = byId[providerId] ?: return@mapNotNull null
            runCatching { provider.detail(row) }
                .onFailure { failures += ProviderFailure(providerId, it.message ?: it.toString()) }
                .getOrNull()
                ?.takeIf { !it.isEmpty() }
                ?.let { providerId to it }
        }.toMap()

        // Even when no provider can fill a field, the row list still identifies the song.
        val merged = merge(candidates)
        return merged.copy(
            title = merged.title ?: rows.values.firstOrNull()?.title?.takeIf { it.isNotBlank() },
            artist = merged.artist ?: rows.values.firstOrNull()?.artist?.takeIf { it.isNotBlank() },
            album = merged.album ?: rows.values.firstOrNull()?.album?.takeIf { it.isNotBlank() },
        )
    }

    /** Fetches the details of one result from the provider that produced it. */
    fun detailFor(result: SearchResult): TagCandidate? {
        val provider = byId[result.provider] ?: return null
        return runCatching { provider.detail(result) }
            .onFailure { failures += ProviderFailure(provider.id, it.message ?: it.toString()) }
            .getOrNull()
    }

    /** Fills every field with the first non-blank value in priority order. */
    fun merge(candidates: Map<ProviderId, TagCandidate>): TagCandidate {
        fun pick(field: MergeField, read: (TagCandidate) -> String?): String? =
            policy.priorityFor(field).asSequence()
                .mapNotNull { candidates[it] }
                .mapNotNull { read(it) }
                .firstOrNull { it.isNotBlank() }

        return TagCandidate(
            title = pick(MergeField.TITLE) { it.title },
            artist = pick(MergeField.ARTIST) { it.artist },
            album = pick(MergeField.ALBUM) { it.album },
            albumArtist = pick(MergeField.ALBUM_ARTIST) { it.albumArtist },
            genre = pick(MergeField.GENRE) { it.genre },
            composer = pick(MergeField.COMPOSER) { it.composer },
            track = pick(MergeField.TRACK) { it.track },
            disc = pick(MergeField.DISC) { it.disc },
            year = pick(MergeField.YEAR) { it.year },
            comment = pick(MergeField.COMMENT) { it.comment },
            lyrics = pick(MergeField.LYRICS) { it.lyrics },
            lyricsTranslation = pick(MergeField.LYRICS_TRANSLATION) { it.lyricsTranslation },
            coverUrl = pick(MergeField.COVER) { it.coverUrl },
        )
    }
}
