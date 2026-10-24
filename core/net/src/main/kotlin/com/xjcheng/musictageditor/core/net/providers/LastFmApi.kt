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
 * Last.fm.
 *
 * The API needs a key, so it is injected instead of being baked in (the original shipped a
 * hard-coded one). Without a key the provider reports no results rather than failing.
 */
object LastFmApi {

    const val BASE = "https://ws.audioscrobbler.com/2.0/"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun searchUrl(query: SearchQuery, limit: Int, apiKey: String): String {
        val term = java.net.URLEncoder.encode(query.text(), "UTF-8")
        return "$BASE?method=track.search&track=$term&limit=$limit&format=json&api_key=${url(apiKey)}"
    }

    fun trackInfoUrl(artist: String, track: String, apiKey: String): String =
        "$BASE?method=track.getInfo&artist=${url(artist)}&track=${url(track)}&format=json&api_key=${url(apiKey)}"

    fun parseSearch(text: String, limit: Int = 20): List<SearchResult> {
        val tracks = runCatching {
            json.parseToJsonElement(text).obj()
                ?.get("results")?.obj()
                ?.get("trackmatches")?.obj()
                ?.get("track")?.jsonArray
        }.getOrNull() ?: return emptyList()

        return tracks.take(limit).mapNotNull { element ->
            val track = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val name = track.string("name") ?: return@mapNotNull null
            val artist = track.string("artist").orEmpty()
            SearchResult(
                provider = ProviderId.LASTFM,
                id = "$artist\u0000$name",
                title = name,
                artist = artist,
                album = track.string("album").orEmpty(),
            )
        }
    }

    fun parseTrackInfo(text: String): TagCandidate {
        val track = runCatching {
            json.parseToJsonElement(text).obj()?.get("track")?.jsonObject
        }.getOrNull() ?: return TagCandidate()
        val album = track.obj("album")
        val genre = track["toptags"]?.obj()?.get("tag")?.jsonArray
            ?.firstOrNull()?.jsonObject?.string("name")

        return TagCandidate(
            title = track.string("name"),
            artist = track.obj("artist")?.string("name") ?: track.string("artist"),
            album = album?.string("title"),
            albumArtist = track.obj("artist")?.string("name"),
            genre = genre,
            year = null,
            coverUrl = null,
        )
    }

    fun splitId(id: String): Pair<String, String> {
        val parts = id.split('\u0000')
        return if (parts.size == 2) parts[0] to parts[1] else "" to id
    }

    private fun url(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}
