/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.net

/**
 * The online sources the app queries.
 *
 * Four Chinese services carry the catalogue the app is built for; Apple Music, iTunes and
 * MusicBrainz are the fallback for non-Chinese repertoire. VGMdb and Last.fm were dropped
 * in 1.1.0 (see docs/SOURCES.md).
 *
 * [key] is the stable identifier used by the `tags_sources_*` settings strings, so the
 * stored order survives enum renames.
 */
enum class ProviderId(val key: String, val displayName: String) {
    NETEASE("netease", "网易云音乐"),
    QQ("qq", "QQ 音乐"),
    KUWO("kuwo", "酷我音乐"),
    KUGOU("kugou", "酷狗音乐"),
    APPLE("apple", "Apple Music"),
    ITUNES("itunes", "iTunes"),
    MUSICBRAINZ("musicbrainz", "MusicBrainz"),
    COVER_ART_ARCHIVE("coverartarchive", "Cover Art Archive"),
    ;

    companion object {
        /** Tolerant lookup: unknown or legacy keys return null instead of throwing. */
        fun fromKey(key: String): ProviderId? =
            entries.firstOrNull { it.key == key.trim().lowercase() }
    }
}

/** What the user is searching for. */
data class SearchQuery(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
) {
    /** The free-text form most providers want. */
    fun text(): String = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
}

/** One row of a provider's result list. */
data class SearchResult(
    val provider: ProviderId,
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Int = 0,
    val coverUrl: String? = null,
)

/** The tags a provider can supply for one result. Null fields mean "provider has none". */
data class TagCandidate(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val composer: String? = null,
    val track: String? = null,
    val disc: String? = null,
    val year: String? = null,
    val comment: String? = null,
    val lyrics: String? = null,
    val lyricsTranslation: String? = null,
    val coverUrl: String? = null,
) {
    fun isEmpty(): Boolean = this == TagCandidate()
}

/**
 * A source of tags.
 *
 * Implementations are synchronous on purpose: callers run them on `Dispatchers.IO`, which
 * keeps the providers free of coroutine plumbing and trivially testable against recorded
 * responses.
 */
interface Provider {
    val id: ProviderId

    val displayName: String get() = id.displayName

    /** Whether the provider can supply lyrics, tags, artwork, or a combination. */
    val capabilities: Set<Capability>

    fun search(query: SearchQuery, limit: Int = 20): List<SearchResult>

    fun detail(result: SearchResult): TagCandidate

    enum class Capability { TAGS, LYRICS, COVER }
}
