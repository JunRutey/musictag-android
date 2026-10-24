/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.net

/** The online sources the original application queried. */
enum class ProviderId(val displayName: String) {
    NETEASE("NetEase Cloud Music"),
    QQ("QQ Music"),
    KUWO("Kuwo"),
    KUGOU("Kugou (lyrics)"),
    MUSICBRAINZ("MusicBrainz"),
    VGMDB("VGMdb"),
    ITUNES("iTunes Search"),
    COVER_ART_ARCHIVE("Cover Art Archive"),
    LASTFM("Last.fm"),
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
