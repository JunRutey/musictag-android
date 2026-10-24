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
 * QQ Music.
 *
 * Search already returns the fields the editor needs (title, singers, album, interval), so
 * `detail` only adds the lyric pair from the legacy fcgi endpoint.
 */
class QqProvider(private val transport: HttpTransport) : Provider {

    override val id: ProviderId = ProviderId.QQ

    override val capabilities: Set<Provider.Capability> = setOf(
        Provider.Capability.TAGS,
        Provider.Capability.LYRICS,
        Provider.Capability.COVER,
    )

    override fun search(query: SearchQuery, limit: Int): List<SearchResult> {
        val body = QqApi.searchBody(query, limit)
        return QqApi.parseSearch(transport.postForm(QqApi.SEARCH_URL, body, QqApi.HEADERS), limit)
    }

    override fun detail(result: SearchResult): TagCandidate {
        val (lyric, translation) = QqApi.parseLyric(transport.get(QqApi.lyricUrl(result.id), QqApi.HEADERS))
        return TagCandidate(
            title = result.title.takeIf { it.isNotBlank() },
            artist = result.artist.takeIf { it.isNotBlank() },
            album = result.album.takeIf { it.isNotBlank() },
            coverUrl = result.coverUrl,
            lyrics = lyric,
            lyricsTranslation = translation,
        )
    }
}
