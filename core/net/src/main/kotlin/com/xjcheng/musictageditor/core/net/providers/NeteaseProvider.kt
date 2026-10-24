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
import com.xjcheng.musictageditor.core.net.crypto.Weapi
import com.xjcheng.musictageditor.core.net.http.HttpTransport

/**
 * NetEase Cloud Music provider.
 *
 * Requests are signed with `weapi` exactly like the original native code; the body is
 * `params=…&encSecKey=…` form encoded. Search, song detail and lyrics are three separate
 * calls, mirroring `a()` / `d()` / `a(Context,J)` in 1.2.5.2.
 */
class NeteaseProvider(
    private val transport: HttpTransport,
    private val secretProvider: () -> String = { Weapi.randomSecret() },
) : Provider {

    override val id: ProviderId = ProviderId.NETEASE

    override val capabilities: Set<Provider.Capability> = setOf(
        Provider.Capability.TAGS,
        Provider.Capability.LYRICS,
        Provider.Capability.COVER,
    )

    override fun search(query: SearchQuery, limit: Int): List<SearchResult> {
        val payload = Weapi.encrypt(NeteaseApi.searchBody(query, limit), secretProvider())
        val response = transport.postForm(NeteaseApi.SEARCH_URL, payload.asFormBody(), NeteaseApi.HEADERS)
        return NeteaseApi.parseSearch(response, limit)
    }

    override fun detail(result: SearchResult): TagCandidate {
        val detailPayload = Weapi.encrypt(NeteaseApi.detailBody(result.id), secretProvider())
        val detail = transport.postForm(NeteaseApi.DETAIL_URL, detailPayload.asFormBody(), NeteaseApi.HEADERS)
            .let(NeteaseApi::parseDetail)

        val lyricPayload = Weapi.encrypt(NeteaseApi.lyricBody(result.id), secretProvider())
        val lyricResponse = transport.postForm(NeteaseApi.LYRIC_URL, lyricPayload.asFormBody(), NeteaseApi.HEADERS)
        val (lyric, translation) = NeteaseApi.parseLyric(lyricResponse)

        return detail.copy(lyrics = lyric, lyricsTranslation = translation)
    }
}
