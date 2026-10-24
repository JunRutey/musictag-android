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

/** VGMdb: album search and album detail scraped from its HTML pages. */
class VgmdbProvider(private val transport: HttpTransport) : Provider {

    override val id: ProviderId = ProviderId.VGMDB

    override val capabilities: Set<Provider.Capability> = setOf(Provider.Capability.TAGS)

    override fun search(query: SearchQuery, limit: Int): List<SearchResult> =
        VgmdbApi.parseSearch(transport.get(VgmdbApi.searchUrl(query, limit)), limit)

    override fun detail(result: SearchResult): TagCandidate =
        VgmdbApi.parseAlbum(transport.get(VgmdbApi.albumUrl(result.id)))
}
