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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import java.time.ZoneOffset

/**
 * NetEase Cloud Music endpoints and response parsing.
 *
 * Request bodies are built here and signed with [com.xjcheng.musictageditor.core.net.crypto.Weapi]
 * by the provider; parsing is pure so it can be tested against recorded fixtures.
 *
 * Response shapes follow the endpoints the original application used
 * (`weapi/cloudsearch/pc`, `weapi/v3/song/detail`, `weapi/song/lyric`).
 */
object NeteaseApi {

    const val BASE = "https://music.163.com"
    const val SEARCH_URL = "$BASE/weapi/cloudsearch/pc"
    const val DETAIL_URL = "$BASE/weapi/v3/song/detail"
    const val LYRIC_URL = "$BASE/weapi/song/lyric"

    val HEADERS: Map<String, String> = mapOf(
        "Referer" to "https://music.163.com",
        "User-Agent" to
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0 Safari/537.36",
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ------------------------------------------------------------------ requests

    fun searchBody(query: SearchQuery, limit: Int): String {
        val keyword = escape(query.text())
        return """{"s":"$keyword","type":1,"limit":$limit,"offset":0,"total":true}"""
    }

    fun detailBody(songId: String): String = """{"c":"[{\"id\":$songId,\"v\":0}]","os":"pc","lv":-1,"kv":-1,"tv":-1}"""

    fun lyricBody(songId: String): String = """{"id":$songId,"os":"pc","lv":-1,"kv":-1,"tv":-1}"""

    // ------------------------------------------------------------------- parsing

    fun parseSearch(text: String, limit: Int = 20): List<SearchResult> {
        val songs = runCatching {
            json.parseToJsonElement(text).obj()?.get("result")?.obj()?.get("songs")?.jsonArray
        }.getOrNull() ?: return emptyList()

        return songs.take(limit).mapNotNull { element ->
            val song = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            SearchResult(
                provider = ProviderId.NETEASE,
                id = song.string("id") ?: return@mapNotNull null,
                title = song.string("name").orEmpty(),
                artist = artists(song),
                album = song.obj("al")?.string("name").orEmpty(),
                durationMs = song.int("dt") ?: 0,
                coverUrl = song.obj("al")?.string("picUrl")?.takeIf { it.isNotBlank() },
            )
        }
    }

    /** The detail endpoint returns the same song objects as search, sometimes wrapped. */
    fun parseDetail(text: String): TagCandidate {
        val root = runCatching { json.parseToJsonElement(text).obj() }.getOrNull() ?: return TagCandidate()
        val song = (root["songs"]?.jsonArray?.firstOrNull() ?: root["song"])?.jsonObject ?: return TagCandidate()
        val album = song.obj("al")

        return TagCandidate(
            title = song.string("name"),
            artist = artists(song).takeIf { it.isNotBlank() },
            album = album?.string("name"),
            albumArtist = artists(song).takeIf { it.isNotBlank() },
            year = album?.long("publishTime")?.let(::yearOf),
            track = song.int("no")?.takeIf { it > 0 }?.toString(),
            coverUrl = album?.string("picUrl")?.takeIf { it.isNotBlank() },
            comment = null,
            lyrics = null,
        )
    }

    /** Returns `lyric` and `translation`; either may be null. */
    fun parseLyric(text: String): Pair<String?, String?> {
        val root = runCatching { json.parseToJsonElement(text).obj() }.getOrNull() ?: return null to null
        val lyric = root["lrc"]?.obj()?.string("lyric")?.takeIf { it.isNotBlank() }
        val translation = root["tlyric"]?.obj()?.string("lyric")?.takeIf { it.isNotBlank() }
        return lyric to translation
    }

    // ------------------------------------------------------------------- helpers

    private fun artists(song: JsonObject): String =
        song["ar"]?.jsonArray
            ?.mapNotNull { runCatching { it.jsonObject.string("name") }.getOrNull() }
            ?.filter { it.isNotBlank() }
            ?.joinToString(" / ")
            .orEmpty()

    private fun yearOf(epochMillis: Long): String? {
        if (epochMillis <= 0) return null
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).year.toString()
    }

    private fun escape(value: String): String = buildString(value.length + 8) {
        for (char in value) {
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') append(String.format("\\u%04x", char.code)) else append(char)
            }
        }
    }
}

internal fun JsonElement.obj(): JsonObject? = runCatching { jsonObject }.getOrNull()

internal fun JsonObject.obj(key: String): JsonObject? = (this[key] as? JsonObject)

internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() && it != "null" }

internal fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()

internal fun JsonObject.long(key: String): Long? = string(key)?.toLongOrNull()
