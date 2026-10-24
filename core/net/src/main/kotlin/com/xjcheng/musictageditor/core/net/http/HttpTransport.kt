/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.net.http

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * The network surface providers need, kept behind an interface so provider tests can feed
 * recorded responses instead of hitting live services.
 */
interface HttpTransport {
    fun get(url: String, headers: Map<String, String> = emptyMap()): String

    fun postForm(url: String, body: String, headers: Map<String, String> = emptyMap()): String
}

class HttpException(val status: Int, val url: String) :
    RuntimeException("HTTP $status for $url")

/** Default transport: OkHttp with conservative timeouts and no automatic retries. */
class OkHttpTransport(
    private val client: OkHttpClient = defaultClient(),
) : HttpTransport {

    override fun get(url: String, headers: Map<String, String>): String {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()
        return execute(request)
    }

    override fun postForm(url: String, body: String, headers: Map<String, String>): String {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(FORM))
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        return execute(request)
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpException(response.code, request.url.toString())
            return response.body?.string().orEmpty()
        }
    }

    private companion object {
        val FORM = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
