/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.net.providers

import com.xjcheng.musictageditor.core.net.ProviderId
import com.xjcheng.musictageditor.core.net.SearchQuery
import com.xjcheng.musictageditor.core.net.SearchResult
import com.xjcheng.musictageditor.core.net.TagCandidate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * iTunes Search API.
 *
 * Public and key-less, which makes it the most reliable fallback for album/track metadata
 * and artwork. Artwork URLs embed the requested size (`100x100bb.jpg`), so the same URL can
 * be asked for a larger image by rewriting that segment.
 */
object ItunesApi {

    const val BASE = "https://itunes.apple.com"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun searchUrl(query: SearchQuery, limit: Int, country: String = "US"): String {
        val term = java.net.URLEncoder.encode(query.text(), "UTF-8")
        return "$BASE/search?term=$term&entity=song&limit=$limit&country=$country"
    }

    /** Lookup by track id returns the same shape as search, for one exact result. */
    fun lookupUrl(trackId: String, country: String = "US"): String =
        "$BASE/lookup?id=$trackId&entity=song&country=$country"

    fun parseSearch(text: String, limit: Int = 20): List<SearchResult> {
        val results = runCatching {
            json.parseToJsonElement(text).obj()?.get("results")?.jsonArray
        }.getOrNull() ?: return emptyList()

        return results.take(limit).mapNotNull { element ->
            val track = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val id = track.string("trackId") ?: return@mapNotNull null
            SearchResult(
                provider = ProviderId.ITUNES,
                id = id,
                title = track.string("trackName").orEmpty(),
                artist = track.string("artistName").orEmpty(),
                album = track.string("collectionName").orEmpty(),
                durationMs = track.int("trackTimeMillis") ?: 0,
                coverUrl = artwork(track.string("artworkUrl100"), 600),
            )
        }
    }

    fun parseDetail(text: String): TagCandidate {
        val track = runCatching {
            json.parseToJsonElement(text).obj()?.get("results")?.jsonArray?.firstOrNull()?.jsonObject
        }.getOrNull() ?: return TagCandidate()

        return TagCandidate(
            title = track.string("trackName"),
            artist = track.string("artistName"),
            album = track.string("collectionName"),
            albumArtist = track.string("artistName"),
            genre = track.string("primaryGenreName"),
            year = track.string("releaseDate")?.take(4)?.takeIf { it.length == 4 },
            track = track.int("trackNumber")?.let { number ->
                val total = track.int("trackCount")
                if (total != null && total > 0) "$number/$total" else number.toString()
            },
            coverUrl = artwork(track.string("artworkUrl100"), 600),
        )
    }

    /**
     * Rewrites the `NNNxNNNbb` size segment of an mzstatic artwork URL.
     *
     * URLs without that segment are returned unchanged: they are still valid artwork, just
     * not size-selectable.
     */
    fun artwork(url: String?, size: Int): String? {
        if (url.isNullOrBlank()) return null
        return url.replace(Regex("""/\d+x\d+bb\.(jpg|png)$"""), "/${size}x${size}bb.jpg")
    }
}
