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
 * MusicBrainz.
 *
 * Two-step by design: `recording` search finds candidates, and the chosen recording's
 * release can be looked up for the label, country and track list. Cover art comes from
 * [CoverArtArchiveProvider], keyed by the release id this parser exposes.
 */
object MusicBrainzApi {

    const val BASE = "https://musicbrainz.org/ws/2"

    /** MusicBrainz asks every client to identify itself. */
    val HEADERS: Map<String, String> = mapOf(
        "User-Agent" to "MusicTag/1.0 (https://github.com/JunRutey/musictag-android)",
        "Accept" to "application/json",
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun searchUrl(query: SearchQuery, limit: Int): String {
        val term = java.net.URLEncoder.encode(query.text(), "UTF-8")
        return "$BASE/recording?query=$term&fmt=json&limit=$limit"
    }

    fun releaseUrl(releaseId: String): String = "$BASE/release/$releaseId?fmt=json&inc=recordings+labels"

    /** A recording with its releases, so the provider can resolve album and cover. */
    fun recordingUrl(recordingId: String): String =
        "$BASE/recording/$recordingId?fmt=json&inc=releases+artist-credits"

    fun parseSearch(text: String, limit: Int = 20): List<SearchResult> {
        val recordings = runCatching {
            json.parseToJsonElement(text).obj()?.get("recordings")?.jsonArray
        }.getOrNull() ?: return emptyList()

        return recordings.take(limit).mapNotNull { element ->
            val recording = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val id = recording.string("id") ?: return@mapNotNull null
            val release = recording["releases"]?.jsonArray?.firstOrNull()?.jsonObject
            SearchResult(
                provider = ProviderId.MUSICBRAINZ,
                id = id,
                title = recording.string("title").orEmpty(),
                artist = artistCredit(recording),
                album = release?.string("title").orEmpty(),
                durationMs = recording.int("length") ?: 0,
                coverUrl = release?.string("id")?.let(CoverArtArchiveProvider::frontCoverUrl),
            )
        }
    }

    /** Detail for one recording: album/year from its first release, cover from its id. */
    fun parseRecording(text: String): TagCandidate {
        val recording = runCatching { json.parseToJsonElement(text).obj() }.getOrNull()
            ?: return TagCandidate()
        val release = recording["releases"]?.jsonArray?.firstOrNull()?.jsonObject
        return TagCandidate(
            title = recording.string("title"),
            artist = artistCredit(recording).takeIf { it.isNotBlank() },
            album = release?.string("title"),
            albumArtist = artistCredit(recording).takeIf { it.isNotBlank() },
            year = release?.string("date")?.take(4)?.takeIf { it.length == 4 },
            coverUrl = release?.string("id")?.let(CoverArtArchiveProvider::frontCoverUrl),
        )
    }

    fun parseDetail(text: String): TagCandidate {
        val release = runCatching { json.parseToJsonElement(text).obj() }.getOrNull() ?: return TagCandidate()
        val label = release["label-info"]?.jsonArray?.firstOrNull()?.jsonObject?.obj("label")?.string("name")
        return TagCandidate(
            title = null,
            artist = artistCredit(release).takeIf { it.isNotBlank() },
            album = release.string("title"),
            albumArtist = artistCredit(release).takeIf { it.isNotBlank() },
            year = release.string("date")?.take(4)?.takeIf { it.length == 4 },
            comment = label?.let { "Label: $it" },
            coverUrl = release.string("id")?.let(CoverArtArchiveProvider::frontCoverUrl),
        )
    }

    private fun artistCredit(element: kotlinx.serialization.json.JsonObject): String =
        element["artist-credit"]?.jsonArray
            ?.mapNotNull { runCatching { it.jsonObject.string("name") }.getOrNull() }
            ?.joinToString(" / ")
            .orEmpty()
}
