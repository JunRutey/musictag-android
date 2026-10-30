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
import com.xjcheng.musictageditor.core.net.http.HttpException
import com.xjcheng.musictageditor.core.net.http.HttpTransport

/**
 * Apple Music: the international fallback.
 *
 * The catalog needs a short-lived developer token that the web player publishes in its own
 * JavaScript bundle, so the first call of a session costs two extra requests and the rest
 * reuse the cached token. A 401 means the token expired early; the provider re-scrapes once
 * instead of failing the whole search.
 */
class AppleMusicProvider(
    private val transport: HttpTransport,
    private val region: String = "cn",
    private val language: String = "",
    private val coverSize: Int = AppleMusicApi.DEFAULT_COVER_SIZE,
    private val clock: () -> Long = System::currentTimeMillis,
) : Provider {

    override val id: ProviderId = ProviderId.APPLE

    override val capabilities: Set<Provider.Capability> = setOf(
        Provider.Capability.TAGS,
        Provider.Capability.COVER,
    )

    private var cachedToken: String? = null
    private var cachedTokenValidUntilMillis: Long = 0L

    override fun search(query: SearchQuery, limit: Int): List<SearchResult> {
        val term = query.text().trim()
        if (term.isEmpty()) return emptyList()
        val url = AppleMusicApi.searchUrl(storefront(), catalogLanguage(), term, limit)
        return AppleMusicApi.parseSearch(call(url), coverSize, limit)
    }

    override fun detail(result: SearchResult): TagCandidate {
        val url = AppleMusicApi.songUrl(storefront(), result.id, catalogLanguage())
        return AppleMusicApi.parseSong(call(url), coverSize)
    }

    private fun storefront(): String = AppleMusicApi.storefront(region)

    private fun catalogLanguage(): String =
        language.takeIf { it.isNotBlank() } ?: AppleMusicApi.language(region)

    private fun call(url: String): String = try {
        transport.get(url, AppleMusicApi.apiHeaders(developerToken(forceRefresh = false)))
    } catch (error: HttpException) {
        if (error.status != 401) throw error
        transport.get(url, AppleMusicApi.apiHeaders(developerToken(forceRefresh = true)))
    }

    private fun developerToken(forceRefresh: Boolean): String {
        val now = clock()
        val cached = cachedToken
        if (!forceRefresh && cached != null && now < cachedTokenValidUntilMillis &&
            AppleMusicApi.isUsable(cached, now / 1000)
        ) {
            return cached
        }

        val home = transport.get(AppleMusicApi.WEB_BASE, AppleMusicApi.webHeaders())
        val indexScript = AppleMusicApi.findIndexScript(home)
            ?: error("Apple Music served no index script")
        val script = transport.get(AppleMusicApi.WEB_BASE + indexScript, AppleMusicApi.webHeaders())
        val token = AppleMusicApi.findDeveloperToken(script, now / 1000)
            ?: error("Apple Music served no usable developer token")

        val expiresAtSeconds = AppleMusicApi.expirySeconds(token) ?: (now / 1000 + 3600)
        cachedToken = token
        cachedTokenValidUntilMillis = expiresAtSeconds * 1000 - 60_000
        return token
    }
}
