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
 * Cover Art Archive: artwork for a MusicBrainz release.
 *
 * There is no search of its own - the API is addressed by release id, which
 * [MusicBrainzProvider] exposes. Fetching the image is left to the caller; this provider
 * contributes the URL (redirecting endpoint `front-500`).
 */
object CoverArtArchiveProvider {

    const val BASE = "https://coverartarchive.org"

    fun frontCoverUrl(releaseId: String, size: Int = 500): String =
        "$BASE/release/$releaseId/front-$size"

    val id: ProviderId = ProviderId.COVER_ART_ARCHIVE
}

/** Cover-only provider so the merge step can treat artwork like any other field. */
class CoverArtArchive( private val transport: HttpTransport) : Provider {

    override val id: ProviderId = ProviderId.COVER_ART_ARCHIVE

    override val capabilities: Set<Provider.Capability> = setOf(Provider.Capability.COVER)

    override fun search(query: SearchQuery, limit: Int): List<SearchResult> = emptyList()

    override fun detail(result: SearchResult): TagCandidate = TagCandidate(
        coverUrl = CoverArtArchiveProvider.frontCoverUrl(result.id),
    )

    /** True when the release actually has front artwork (the endpoint redirects to it). */
    fun hasFrontCover(releaseId: String): Boolean = runCatching {
        transport.get(CoverArtArchiveProvider.frontCoverUrl(releaseId))
        true
    }.getOrElse { false }
}
