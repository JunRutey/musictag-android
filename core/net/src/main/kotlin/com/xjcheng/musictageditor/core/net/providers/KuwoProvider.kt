/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.net.providers

import com.xjcheng.musictageditor.core.net.Provider
import com.xjcheng.musictageditor.core.net.ProviderId
import com.xjcheng.musictageditor.core.net.SearchQuery
import com.xjcheng.musictageditor.core.net.SearchResult
import com.xjcheng.musictageditor.core.net.TagCandidate
import com.xjcheng.musictageditor.core.net.http.HttpTransport

/**
 * Kuwo.
 *
 * Search rows carry the tag fields; lyrics arrive as structured lines and are rebuilt into
 * LRC text so callers only handle one lyric format.
 */
class KuwoProvider(private val transport: HttpTransport) : Provider {

    override val id: ProviderId = ProviderId.KUWO

    override val capabilities: Set<Provider.Capability> = setOf(
        Provider.Capability.TAGS,
        Provider.Capability.LYRICS,
        Provider.Capability.COVER,
    )

    override fun search(query: SearchQuery, limit: Int): List<SearchResult> {
        val url = KuwoApi.searchUrl(query, limit)
        return KuwoApi.parseSearch(transport.get(url, KuwoApi.HEADERS), limit)
    }

    override fun detail(result: SearchResult): TagCandidate {
        val (lyric, translation) = KuwoApi.parseLyric(
            transport.get(KuwoApi.lyricUrl(result.id), KuwoApi.HEADERS),
        )
        return TagCandidate(
            title = result.title.takeIf { it.isNotBlank() },
            artist = result.artist.takeIf { it.isNotBlank() },
            album = result.album.takeIf { it.isNotBlank() },
            albumArtist = result.artist.takeIf { it.isNotBlank() },
            coverUrl = result.coverUrl,
            lyrics = lyric,
            lyricsTranslation = translation,
        )
    }
}
