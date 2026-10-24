/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.io

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Downloads cover art for a candidate found by an online provider.
 *
 * Bounded on purpose: a provider that points at a huge image must not exhaust the heap of
 * a tag editor, so the body is read with a hard byte limit and rejected if exceeded.
 */
class CoverDownloader(
    private val client: OkHttpClient = defaultClient(),
    private val maxBytes: Long = 8L * 1024 * 1024,
) {
    fun fetch(url: String): ByteArray? = runCatching {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body ?: return@use null
            if (body.contentLength() > maxBytes) return@use null
            val bytes = body.bytes()
            if (bytes.size.toLong() > maxBytes) null else bytes
        }
    }.getOrNull()

    private companion object {
        const val USER_AGENT = "MusicTag/1.0 (https://github.com/JunRutey/musictag-android)"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
