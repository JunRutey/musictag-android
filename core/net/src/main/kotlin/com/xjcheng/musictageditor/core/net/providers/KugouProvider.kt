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

/** Kugou: search rows plus two-step lyric download. */
class KugouProvider(private val transport: HttpTransport) : Provider {

    override val id: ProviderId = ProviderId.KUGOU

    override val capabilities: Set<Provider.Capability> = setOf(
        Provider.Capability.TAGS,
        Provider.Capability.LYRICS,
    )

    override fun search(query: SearchQuery, limit: Int): List<SearchResult> =
        KugouApi.parseSearch(
            transport.get(KugouApi.searchUrl(query, limit), KugouApi.HEADERS),
            limit,
        )

    override fun detail(result: SearchResult): TagCandidate {
        val candidate = KugouApi.parseLyricCandidate(
            transport.get(KugouApi.lyricCandidateUrl(result.id), KugouApi.HEADERS),
        )
        val lyrics = candidate?.let { (id, accessKey) ->
            KugouApi.parseLyricDownload(
                transport.get(KugouApi.lyricDownloadUrl(id, accessKey), KugouApi.HEADERS),
            )
        }
        return TagCandidate(
            title = result.title.takeIf { it.isNotBlank() },
            artist = result.artist.takeIf { it.isNotBlank() },
            album = result.album.takeIf { it.isNotBlank() },
            lyrics = lyrics,
        )
    }
}
