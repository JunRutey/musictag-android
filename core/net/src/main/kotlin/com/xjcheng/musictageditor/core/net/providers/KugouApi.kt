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
 * Kugou.
 *
 * Tag search is plain JSON on `mobilecdn`. Lyrics need two calls: a candidate lookup keyed
 * by the song hash, then a download keyed by the candidate's id + access key. The
 * download's payload is base64 LRC text (the encrypted KRC variant is not used).
 */
object KugouApi {

    const val SEARCH_URL = "https://mobilecdn.kugou.com/api/v3/search/song"
    const val LYRIC_CANDIDATE_URL = "https://krcs.kugou.com/search"
    const val LYRIC_DOWNLOAD_URL = "https://lyrics.kugou.com/download"

    val HEADERS: Map<String, String> = mapOf(
        "User-Agent" to
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0 Safari/537.36",
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun searchUrl(query: SearchQuery, limit: Int): String {
        val keyword = java.net.URLEncoder.encode(query.text(), "UTF-8")
        return "$SEARCH_URL?keyword=$keyword&page=1&pagesize=$limit&format=json"
    }

    fun lyricCandidateUrl(hash: String): String =
        "$LYRIC_CANDIDATE_URL?ver=1&man=yes&client=mobi&keyword=&duration=&hash=$hash"

    fun lyricDownloadUrl(id: String, accessKey: String): String =
        "$LYRIC_DOWNLOAD_URL?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=lrc&charset=utf8"

    fun parseSearch(text: String, limit: Int = 20): List<SearchResult> {
        val info = runCatching {
            json.parseToJsonElement(text).obj()
                ?.get("data")?.obj()
                ?.get("info")?.jsonArray
        }.getOrNull() ?: return emptyList()

        return info.take(limit).mapNotNull { element ->
            val song = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val hash = song.string("hash") ?: return@mapNotNull null
            SearchResult(
                provider = ProviderId.KUGOU,
                id = hash,
                title = song.string("songname").orEmpty(),
                artist = song.string("singername").orEmpty(),
                album = song.string("album_name").orEmpty(),
                durationMs = (song.int("duration") ?: 0) * 1000,
            )
        }
    }

    /** Returns `(id, accessKey)` for the best lyric candidate, if any. */
    fun parseLyricCandidate(text: String): Pair<String, String>? {
        val candidate = runCatching {
            json.parseToJsonElement(text).obj()
                ?.get("candidates")?.jsonArray?.firstOrNull()?.jsonObject
        }.getOrNull() ?: return null
        val id = candidate.string("id") ?: return null
        val accessKey = candidate.string("accesskey") ?: return null
        return id to accessKey
    }

    /** The download payload is base64 in the `content` field. */
    fun parseLyricDownload(text: String): String? {
        val content = runCatching {
            json.parseToJsonElement(text).obj()?.string("content")
        }.getOrNull() ?: return null
        if (content.isBlank()) return null
        val decoded = runCatching { java.util.Base64.getDecoder().decode(content) }.getOrNull()
            ?: return null
        return String(decoded, Charsets.UTF_8).takeIf { it.isNotBlank() }
    }
}
