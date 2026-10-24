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
 * QQ Music endpoints.
 *
 * Search posts a small JSON envelope to `musicu.fcg` (the original built the same template
 * with `%s` placeholders). Lyrics come from the legacy fcgi endpoint, which answers with a
 * JSONP wrapper around base64-encoded, XML-escaped text.
 */
object QqApi {

    const val SEARCH_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"
    const val LYRIC_URL = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg"

    val HEADERS: Map<String, String> = mapOf(
        "Referer" to "https://y.qq.com/",
        "User-Agent" to
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0 Safari/537.36",
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun searchBody(query: SearchQuery, limit: Int): String {
        val keyword = escape(query.text())
        return """{"req_0":{"method":"DoSearchForQQMusicDesktop","module":"music.search.SearchCgiService",""" +
            """"param":{"search_type":0,"query":"$keyword","page_num":1,"num_per_page":$limit}}}"""
    }

    fun lyricUrl(songMid: String): String =
        "$LYRIC_URL?songmid=$songMid&g_tk=5381&format=jsonp&nobase64=0"

    fun parseSearch(text: String, limit: Int = 20): List<SearchResult> {
        val list = runCatching {
            json.parseToJsonElement(text).obj()
                ?.get("req_0")?.obj()
                ?.get("data")?.obj()
                ?.get("body")?.obj()
                ?.get("song")?.obj()
                ?.get("list")?.jsonArray
        }.getOrNull() ?: return emptyList()

        return list.take(limit).mapNotNull { element ->
            val song = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val mid = song.string("mid") ?: return@mapNotNull null
            SearchResult(
                provider = ProviderId.QQ,
                id = mid,
                title = song.string("title").orEmpty(),
                artist = song["singer"]?.jsonArray
                    ?.mapNotNull { runCatching { it.jsonObject.string("name") }.getOrNull() }
                    ?.joinToString(" / ")
                    .orEmpty(),
                album = song.obj("album")?.string("name").orEmpty(),
                durationMs = (song.int("interval") ?: 0) * 1000,
                coverUrl = albumCover(song.obj("album")?.string("mid")),
            )
        }
    }

    fun parseDetail(text: String): TagCandidate {
        val song = runCatching {
            json.parseToJsonElement(text).obj()
                ?.get("req_0")?.obj()
                ?.get("data")?.obj()
                ?.get("body")?.obj()
                ?.get("song")?.obj()
                ?.get("list")?.jsonArray?.firstOrNull()?.jsonObject
        }.getOrNull() ?: return TagCandidate()

        val album = song.obj("album")
        return TagCandidate(
            title = song.string("title"),
            artist = song["singer"]?.jsonArray
                ?.mapNotNull { runCatching { it.jsonObject.string("name") }.getOrNull() }
                ?.joinToString(" / ")
                ?.takeIf { it.isNotBlank() },
            album = album?.string("name"),
            albumArtist = album?.string("singername"),
            year = album?.string("publish_date")?.take(4)?.takeIf { it.length == 4 },
            coverUrl = albumCover(album?.string("mid")),
        )
    }

    /** QQ wraps lyrics in `MusicJsonCallback({...})`; the payload is base64 + XML escapes. */
    fun parseLyric(text: String): Pair<String?, String?> {
        val payload = stripJsonp(text)
        val root = runCatching { json.parseToJsonElement(payload).obj() }.getOrNull() ?: return null to null
        val lyric = decodeText(root.string("lyric"))
        val translation = decodeText(root.string("trans"))
        return lyric to translation
    }

    fun stripJsonp(text: String): String {
        val trimmed = text.trim()
        val brace = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (brace < 0 || end <= brace) return trimmed
        return trimmed.substring(brace, end + 1)
    }

    fun decodeText(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val decoded = runCatching { java.util.Base64.getDecoder().decode(value) }.getOrNull()
            ?: return null
        return unescapeXml(String(decoded, Charsets.UTF_8)).takeIf { it.isNotBlank() }
    }

    fun unescapeXml(value: String): String = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")

    /** QQ cover art is addressed by album mid, in three sizes. */
    fun albumCover(albumMid: String?): String? =
        albumMid?.takeIf { it.isNotBlank() }?.let { "https://y.qq.com/music/photo_new/T002R800x800M000$it.jpg" }

    private fun escape(value: String): String = buildString(value.length + 8) {
        for (char in value) {
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}
