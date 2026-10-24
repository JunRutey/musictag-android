/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.net.providers

import com.xjcheng.musictageditor.core.net.ProviderId
import com.xjcheng.musictageditor.core.net.SearchQuery
import com.xjcheng.musictageditor.core.net.SearchResult
import com.xjcheng.musictageditor.core.net.TagCandidate

/**
 * VGMdb: album database for game/anime soundtracks.
 *
 * VGMdb has no JSON API, so the original scraped its HTML and so does this - with a small,
 * explicitly fragile extractor instead of pulling in a full HTML parser. Every method
 * tolerates missing markup and returns nothing rather than throwing.
 */
object VgmdbApi {

    const val BASE = "https://vgmdb.net"

    fun searchUrl(query: SearchQuery, limit: Int): String {
        val term = java.net.URLEncoder.encode(query.text(), "UTF-8")
        return "$BASE/search?q=$term&type=album"
    }

    fun albumUrl(albumId: String): String = "$BASE/album/$albumId"

    /** Search results are `href="/album/<id>"` links inside the results table. */
    fun parseSearch(html: String, limit: Int = 20): List<SearchResult> {
        val link = Regex("""<a[^>]+href="(/album/(\d+))"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val seen = LinkedHashSet<String>()
        val results = mutableListOf<SearchResult>()

        for (match in link.findAll(html)) {
            val albumId = match.groupValues[2]
            if (!seen.add(albumId)) continue
            val title = clean(match.groupValues[3])
            if (title.isBlank()) continue
            results += SearchResult(
                provider = ProviderId.VGMDB,
                id = albumId,
                title = title,
                artist = "",
                album = title,
            )
            if (results.size >= limit) break
        }
        return results
    }

    /** Album pages expose the title in `albumtitle`, the artist and the release date. */
    fun parseAlbum(html: String): TagCandidate {
        val title = extract(html, """<h1[^>]*class="albumtitle"[^>]*>(.*?)</h1>""")
            ?: extract(html, """<title>(.*?)</title>""")?.substringBefore(" - VGMdb")
        val artist = extract(html, """<span[^>]*class="albumartist"[^>]*>(.*?)</span>""")
        val date = extract(html, """Release Date</span>\s*<span[^>]*>(.*?)</span>""")
            ?: extract(html, """(\d{4}-\d{2}-\d{2})""")

        return TagCandidate(
            title = title?.takeIf { it.isNotBlank() },
            artist = artist?.takeIf { it.isNotBlank() },
            album = title?.takeIf { it.isNotBlank() },
            albumArtist = artist?.takeIf { it.isNotBlank() },
            year = date?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) },
        )
    }

    private fun extract(html: String, pattern: String): String? =
        Regex(pattern, setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(html)?.groupValues?.get(1)?.let(::clean)

    private fun clean(value: String): String = value
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&nbsp;", " ")
        .trim()
}
