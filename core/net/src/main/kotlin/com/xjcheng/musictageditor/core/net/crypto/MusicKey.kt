/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.net.crypto


/**
 * The `163 key(Don't modify):` comment marker that the NetEase client writes into files.
 *
 * The original exposes it as native `g(String)` / `q(String)`: AES-128-ECB with a fixed
 * key, base64, prefixed with the marker. Decrypting it lets the search screen recover a
 * song id or album cover id from a file that was tagged by the NetEase client.
 */
object MusicKey {

    const val PREFIX = "163 key(Don't modify):"

    /**
     * AES key from `.rodata`. It contains a literal backslash and brackets, which is why
     * it is written as a byte array rather than a template string.
     */
    private val KEY: ByteArray = "#14ljk_!\\]&0U<('".toByteArray(Charsets.US_ASCII)

    fun encrypt(json: String): String {
        val encoded = java.util.Base64.getEncoder()
            .encodeToString(Aes.ecbEncrypt(json.toByteArray(Charsets.UTF_8), KEY))
        return PREFIX + encoded
    }

    /** Returns the decrypted JSON, or null when [value] is not a marker or cannot be read. */
    fun decrypt(value: String): String? {
        if (!value.startsWith(PREFIX)) return null
        val payload = value.removePrefix(PREFIX).trim()
        if (payload.isEmpty()) return null
        return runCatching {
            String(Aes.ecbDecrypt(java.util.Base64.getDecoder().decode(payload), KEY), Charsets.UTF_8)
        }.getOrNull()
    }

    fun isMusicKey(value: String): Boolean = value.startsWith(PREFIX)
}
