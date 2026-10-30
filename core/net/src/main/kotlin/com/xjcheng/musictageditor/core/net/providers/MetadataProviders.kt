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

/** iTunes Search API: no key, good artwork, weak on track/disc numbers outside albums. */
class ItunesProvider(
    private val transport: HttpTransport,
    private val country: String = "US",
) : Provider {

    override val id: ProviderId = ProviderId.ITUNES

    override val capabilities: Set<Provider.Capability> = setOf(
        Provider.Capability.TAGS,
        Provider.Capability.COVER,
    )

    override fun search(query: SearchQuery, limit: Int): List<SearchResult> =
        ItunesApi.parseSearch(transport.get(ItunesApi.searchUrl(query, limit, country)), limit)

    override fun detail(result: SearchResult): TagCandidate =
        ItunesApi.parseDetail(transport.get(ItunesApi.lookupUrl(result.id, country)))
}

/** MusicBrainz plus its Cover Art Archive artwork. */
class MusicBrainzProvider(private val transport: HttpTransport) : Provider {

    override val id: ProviderId = ProviderId.MUSICBRAINZ

    override val capabilities: Set<Provider.Capability> = setOf(
        Provider.Capability.TAGS,
        Provider.Capability.COVER,
    )

    override fun search(query: SearchQuery, limit: Int): List<SearchResult> =
        MusicBrainzApi.parseSearch(
            transport.get(MusicBrainzApi.searchUrl(query, limit), MusicBrainzApi.HEADERS),
            limit,
        )

    override fun detail(result: SearchResult): TagCandidate =
        MusicBrainzApi.parseRecording(
            transport.get(MusicBrainzApi.recordingUrl(result.id), MusicBrainzApi.HEADERS),
        )
}
