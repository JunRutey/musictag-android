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
 * Kuwo endpoints.
 *
 * The 2013-era `search.kuwo.cn/r.s` endpoint answers with JavaScript-like single-quoted
 * objects, so this uses the JSON endpoints that `www.kuwo.cn` and the mobile site expose
 * instead; both return ordinary JSON.
 */
object KuwoApi {

    const val SEARCH_URL = "https://www.kuwo.cn/api/www/search/searchMusicBykeyWord"
    const val LYRIC_URL = "https://m.kuwo.cn/newh5/singles/songinfoandlrc"

    val HEADERS: Map<String, String> = mapOf(
        "Referer" to "https://www.kuwo.cn/",
        "User-Agent" to
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0 Safari/537.36",
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun searchUrl(query: SearchQuery, limit: Int): String =
        "$SEARCH_URL?key=${encode(query.text())}&pn=1&rn=$limit&httpsStatus=1"

    fun lyricUrl(musicId: String): String = "$LYRIC_URL?musicId=$musicId"

    fun parseSearch(text: String, limit: Int = 20): List<SearchResult> {
        val list = runCatching {
            json.parseToJsonElement(text).obj()
                ?.get("data")?.obj()
                ?.get("list")?.jsonArray
        }.getOrNull() ?: return emptyList()

        return list.take(limit).mapNotNull { element ->
            val song = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val rid = song.string("rid") ?: song.string("musicrid") ?: return@mapNotNull null
            SearchResult(
                provider = ProviderId.KUWO,
                id = rid.removePrefix("MUSIC_"),
                title = song.string("name").orEmpty(),
                artist = song.string("artist").orEmpty(),
                album = song.string("album").orEmpty(),
                durationMs = (song.int("duration") ?: 0) * 1000,
                coverUrl = song.string("pic")?.takeIf { it.isNotBlank() },
            )
        }
    }

    fun parseDetail(text: String): TagCandidate {
        val root = runCatching { json.parseToJsonElement(text).obj() }.getOrNull() ?: return TagCandidate()
        val song = root["data"]?.obj()?.get("info")?.obj() ?: root["data"]?.obj() ?: return TagCandidate()
        return TagCandidate(
            title = song.string("name"),
            artist = song.string("artist"),
            album = song.string("album"),
            albumArtist = song.string("artist"),
            coverUrl = song.string("pic")?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * The mobile endpoint returns structured lyric lines; they are rebuilt into LRC so the
     * rest of the app only ever deals with one text format.
     */
    fun parseLyric(text: String): Pair<String?, String?> {
        val lines = runCatching {
            json.parseToJsonElement(text).obj()
                ?.get("data")?.obj()
                ?.get("lrclist")?.jsonArray
        }.getOrNull() ?: return null to null

        val builder = StringBuilder()
        for (element in lines) {
            val line = runCatching { element.jsonObject }.getOrNull() ?: continue
            val time = line.string("time")?.toDoubleOrNull() ?: continue
            val content = line.string("lineLyric") ?: continue
            builder.append('[').append(formatTime(time)).append(']').append(content).append('\n')
        }
        return builder.toString().takeIf { it.isNotBlank() } to null
    }

    /** `[mm:ss.xx]`, the LRC convention. */
    fun formatTime(seconds: Double): String {
        val safe = if (seconds < 0) 0.0 else seconds
        val minutes = (safe / 60).toInt()
        val remainder = safe - minutes * 60
        return "%02d:%05.2f".format(minutes, remainder)
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}
