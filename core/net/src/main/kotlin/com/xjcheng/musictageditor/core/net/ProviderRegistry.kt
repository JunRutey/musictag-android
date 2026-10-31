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
 * The order is user-editable through the `tags_sources_combination` / `tags_sources_picture`
 * / `tags_sources_lyric` settings; [fromSettings] turns those comma separated keys into a
 * policy and silently ignores unknown or retired provider names.
 */
data class MergePolicy(
    val order: Map<MergeField, List<ProviderId>> = DEFAULT_ORDER,
) {
    fun priorityFor(field: MergeField): List<ProviderId> =
        order[field] ?: DEFAULT_ORDER.getValue(field)

    companion object {
        /**
         * Three Chinese services first, then the international fallbacks. Kugou and Apple
         * Music left this list in 1.1.6: Kugou's endpoint kept answering with nothing useful
         * and the Apple Music token scrape failed on every search, so both showed up as
         * "no response" sources.
         */
        val TAG_ORDER: List<ProviderId> = listOf(
            ProviderId.NETEASE,
            ProviderId.QQ,
            ProviderId.KUWO,
            ProviderId.ITUNES,
            ProviderId.MUSICBRAINZ,
        )

        val COVER_ORDER: List<ProviderId> = listOf(
            ProviderId.NETEASE,
            ProviderId.QQ,
            ProviderId.ITUNES,
            ProviderId.COVER_ART_ARCHIVE,
            ProviderId.KUWO,
            ProviderId.MUSICBRAINZ,
        )

        val LYRIC_ORDER: List<ProviderId> = listOf(
            ProviderId.NETEASE,
            ProviderId.QQ,
            ProviderId.KUWO,
        )

        val DEFAULT_ORDER: Map<MergeField, List<ProviderId>> =
            MergeField.entries.associateWith { field ->
                when (field) {
                    MergeField.LYRICS, MergeField.LYRICS_TRANSLATION -> LYRIC_ORDER
                    MergeField.COVER -> COVER_ORDER
                    else -> TAG_ORDER
                }
            }

        /**
         * Parses a settings value such as `netease,qq,apple`.
         *
         * Separators are tolerated (`;` and whitespace too) and duplicates are removed so a
         * hand-edited preference cannot make one provider win twice.
         */
        fun parseOrder(text: String): List<ProviderId> =
            text.split(',', ';', ' ', '\n')
                .asSequence()
                .mapNotNull { ProviderId.fromKey(it) }
                .distinct()
                .toList()

        /** Builds a policy from the three stored source orders, falling back per field. */
        fun fromSettings(
            tagSources: String = "",
            coverSources: String = "",
            lyricSources: String = "",
        ): MergePolicy {
            val tags = parseOrder(tagSources).ifEmpty { TAG_ORDER }
            val covers = parseOrder(coverSources).ifEmpty { COVER_ORDER }
            val lyrics = parseOrder(lyricSources).ifEmpty { LYRIC_ORDER }
            return MergePolicy(
                MergeField.entries.associateWith { field ->
                    when (field) {
                        MergeField.LYRICS, MergeField.LYRICS_TRANSLATION -> lyrics
                        MergeField.COVER -> covers
                        else -> tags
                    }
                },
            )
        }

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
     * Runs [query] against every provider that offers tags, in the configured priority
     * order, keeping at most [limitPerProvider] rows from each.
     */
    fun searchAll(query: SearchQuery, limitPerProvider: Int = 10): List<SearchResult> {
        val ordered = (policy.priorityFor(MergeField.TITLE) + byId.keys)
            .distinct()
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
