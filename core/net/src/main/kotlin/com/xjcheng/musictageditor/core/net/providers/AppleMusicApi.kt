/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.net.providers

import com.xjcheng.musictageditor.core.net.ProviderId
import com.xjcheng.musictageditor.core.net.SearchResult
import com.xjcheng.musictageditor.core.net.TagCandidate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import java.net.URLEncoder

/**
 * Apple Music catalog endpoints.
 *
 * Only behaviour that is observable from the public web player is used here: the site's
 * index script carries a short-lived developer JWT, and the catalog API at
 * `amp-api.music.apple.com` accepts that JWT for search and lookup. No code was copied from
 * any third-party plugin; see docs/SOURCES.md for the protocol notes.
 */
object AppleMusicApi {

    const val WEB_BASE = "https://music.apple.com"
    const val API_BASE = "https://amp-api.music.apple.com/v1"

    const val WEB_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** Default artwork edge in pixels; the API template accepts any size. */
    const val DEFAULT_COVER_SIZE = 3000

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val INDEX_SCRIPT = Regex("""/assets/index[~\-][^"']+\.js""")
    private val JWT_TOKEN = Regex("""eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}""")

    fun webHeaders(): Map<String, String> = mapOf(
        "User-Agent" to WEB_USER_AGENT,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    )

    fun apiHeaders(token: String): Map<String, String> = mapOf(
        "Authorization" to "Bearer $token",
        "Origin" to WEB_BASE,
        "Referer" to "$WEB_BASE/",
        "User-Agent" to WEB_USER_AGENT,
        "Accept" to "application/json",
    )

    /** Apple storefront ids the app exposes; unknown regions fall back to their first two letters. */
    fun storefront(region: String): String {
        val value = region.trim().lowercase()
        return when (value) {
            "", "cn", "zh-cn", "zh-hans", "zh-hans-cn", "zh_cn" -> "cn"
            "us", "en-us", "en" -> "us"
            "jp", "ja-jp", "ja" -> "jp"
            "kr", "ko-kr", "ko" -> "kr"
            "hk", "zh-hk" -> "hk"
            "tw", "zh-tw", "zh-hant", "zh-hant-tw" -> "tw"
            "sg" -> "sg"
            "gb", "uk", "en-gb" -> "gb"
            else -> value.take(2).ifBlank { "cn" }
        }
    }

    /** Catalog language; `zh-Hans` is what makes Chinese releases carry Chinese titles. */
    fun language(region: String): String = when (storefront(region)) {
        "cn", "sg" -> "zh-Hans"
        "hk", "tw" -> "zh-Hant"
        "jp" -> "ja"
        "kr" -> "ko"
        else -> "en"
    }

    fun searchUrl(
        storefront: String,
        language: String,
        term: String,
        limit: Int,
        offset: Int = 0,
    ): String {
        val encoded = URLEncoder.encode(term, "UTF-8")
        return "$API_BASE/catalog/$storefront/search?term=$encoded&types=songs" +
            "&limit=${limit.coerceIn(1, 25)}&offset=${offset.coerceAtLeast(0)}" +
            "&l=$language&platform=web&format[resources]=map"
    }

    fun songUrl(storefront: String, id: String, language: String): String =
        "$API_BASE/catalog/$storefront/songs/$id?l=$language&platform=web&format[resources]=map"

    /** Path of the script bundle that carries the developer token, e.g. `/assets/index~abc.js`. */
    fun findIndexScript(html: String): String? = INDEX_SCRIPT.find(html)?.value

    /**
     * Picks a developer token that is still valid and belongs to the web player.
     *
     * Apple ships several JWTs in the bundle (media user tokens, storefront tokens); the one
     * that matters is signed for `music.apple.com` and carries `root_https_origin`.
     */
    fun findDeveloperToken(script: String, nowSeconds: Long = System.currentTimeMillis() / 1000): String? {
        for (match in JWT_TOKEN.findAll(script)) {
            val token = match.value
            if (!isUsable(token, nowSeconds)) continue
            return token
        }
        return null
    }

    fun isUsable(token: String, nowSeconds: Long): Boolean {
        val payload = payload(token) ?: return false
        val exp = payload.string("exp")?.toLongOrNull() ?: return false
        if (exp <= nowSeconds + 60) return false
        val origins = payload["root_https_origin"]?.let { element ->
            runCatching { element.jsonArray.mapNotNull { it.toString().trim('"') } }.getOrNull()
        }.orEmpty()
        if (origins.isEmpty()) return true
        return origins.any { it.contains("music.apple.com") }
    }

    fun expirySeconds(token: String): Long? = payload(token)?.string("exp")?.toLongOrNull()

    /** Base64url JSON payload of a JWT, or null when it does not decode. */
    fun payload(token: String): JsonObject? {
        val parts = token.split('.')
        if (parts.size < 2) return null
        val decoded = runCatching { Base64Url.decode(parts[1]) }.getOrNull() ?: return null
        return runCatching { json.parseToJsonElement(decoded).obj() }.getOrNull()
    }

    fun parseSearch(text: String, coverSize: Int = DEFAULT_COVER_SIZE, limit: Int = 20): List<SearchResult> {
        val data = runCatching {
            json.parseToJsonElement(text).obj()
                ?.get("results")?.obj()
                ?.get("songs")?.obj()
                ?.get("data")?.jsonArray
        }.getOrNull() ?: return emptyList()

        return data.take(limit).mapNotNull { element ->
            val song = element.obj() ?: return@mapNotNull null
            val attributes = song.obj("attributes") ?: return@mapNotNull null
            val id = song.string("id")
                ?: attributes.obj("playParams")?.string("id")
                ?: return@mapNotNull null
            val candidate = candidate(attributes, coverSize)
            SearchResult(
                provider = ProviderId.APPLE,
                id = id,
                title = candidate.title.orEmpty(),
                artist = candidate.artist.orEmpty(),
                album = candidate.album.orEmpty(),
                durationMs = attributes.long("durationInMillis")?.toInt() ?: 0,
                coverUrl = candidate.coverUrl,
            )
        }
    }

    fun parseSong(text: String, coverSize: Int = DEFAULT_COVER_SIZE): TagCandidate {
        val attributes = runCatching {
            json.parseToJsonElement(text).obj()
                ?.get("data")?.jsonArray
                ?.firstOrNull()?.obj()
                ?.get("attributes")?.obj()
        }.getOrNull() ?: return TagCandidate()
        return candidate(attributes, coverSize)
    }

    private fun candidate(attributes: JsonObject, coverSize: Int): TagCandidate {
        val genres = runCatching {
            attributes["genreNames"]?.jsonArray
                ?.mapNotNull { it.toString().trim('"').takeIf(String::isNotBlank) }
                ?.joinToString(" / ")
        }.getOrNull()

        return TagCandidate(
            title = attributes.string("name"),
            artist = attributes.string("artistName"),
            album = attributes.string("albumName"),
            genre = genres?.takeIf { it.isNotBlank() },
            composer = attributes.string("composerName"),
            track = attributes.int("trackNumber")?.toString(),
            disc = attributes.int("discNumber")?.toString(),
            year = attributes.string("releaseDate")?.take(4)?.takeIf { it.length == 4 },
            coverUrl = artworkUrl(attributes.obj("artwork")?.string("url"), coverSize),
        )
    }

    /** The template looks like `.../{w}x{h}bb.jpg`; the extension is a `{f}` placeholder. */
    fun artworkUrl(template: String?, size: Int = DEFAULT_COVER_SIZE): String? {
        val value = template?.takeIf { it.isNotBlank() } ?: return null
        val edge = size.coerceIn(120, 5000).toString()
        return value.replace("{w}", edge).replace("{h}", edge).replace("{f}", "jpg")
    }
}

/**
 * Minimal base64url decoder.
 *
 * `java.util.Base64` is API 26 on Android while the app supports 24, so the two-line
 * alphabet translation lives here instead of a platform call.
 */
internal object Base64Url {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private val VALUES = IntArray(128) { -1 }.apply {
        ALPHABET.forEachIndexed { index, char -> this[char.code] = index }
        // Accept standard base64 as well, in case a token is not url-safe.
        this['+'.code] = 62
        this['/'.code] = 63
    }

    fun decode(text: String): String {
        val out = java.io.ByteArrayOutputStream(text.length * 3 / 4 + 3)
        var buffer = 0
        var bits = 0
        for (char in text) {
            if (char == '=') break
            val value = if (char.code < 128) VALUES[char.code] else -1
            if (value < 0) continue
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }
}
