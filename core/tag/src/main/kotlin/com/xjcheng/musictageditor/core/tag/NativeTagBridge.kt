/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.tag

/**
 * JNI entry points implemented in native/taglib/jni_bridge.cpp.
 *
 * Kept intentionally tiny: one call returns the whole file so the container is parsed
 * once, and the Kotlin side owns all interpretation.
 */
class NativeTagBridge private constructor() {

    companion object {
        /**
         * Flat array layout:
         *   0 container, 1 tagFormat,
         *   2 title, 3 artist, 4 album, 5 albumArtist, 6 genre, 7 composer,
         *   8 track, 9 trackStr, 10 disc, 11 discStr, 12 year, 13 comment,
         *   14 lyrics,
         *   15 durationMs, 16 bitrateKbps, 17 sampleRateHz, 18 channels, 19 bitsPerSample,
         *   20 pictureCount, 21.. mime/base64 pairs.
         */
        @JvmStatic
        external fun readAll(path: String): Array<String>?

        /**
         * Writes the twelve fields plus lyrics and an optional cover edit.
         *
         * cover is null to leave artwork alone, empty to remove it, otherwise the new
         * bytes. options bit 0 writes ID3v1 too, bit 1 writes ID3v2.3 instead of
         * ID3v2.4. Returns null on success, otherwise a reason.
         */
        @JvmStatic
        external fun writeAll(
            path: String,
            fields: Array<String>,
            cover: ByteArray?,
            coverMime: String?,
            options: Int,
        ): String?

        @JvmStatic
        external fun taglibVersion(): String
    }
}

/**
 * Loads the native library.
 *
 * On Android the library is packaged by the app module's CMake build. On the host JVM
 * (unit tests) the CI-built copy is pointed at by `MUSICTAG_HOST_LIB`, which is how the
 * tag engine gets real read/write coverage without a device.
 */
internal object NativeLibrary {
    private const val LIBRARY_NAME = "musictag_jni"
    private const val HOST_LIBRARY_ENV = "MUSICTAG_HOST_LIB"

    @Volatile
    private var loaded = false

    @Volatile
    private var failure: Throwable? = null

    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            failure?.let { throw IllegalStateException("native tag library is unavailable", it) }
            try {
                val hostLibrary = System.getenv(HOST_LIBRARY_ENV)
                if (!hostLibrary.isNullOrBlank()) {
                    System.load(hostLibrary)
                } else {
                    System.loadLibrary(LIBRARY_NAME)
                }
                loaded = true
            } catch (t: Throwable) {
                failure = t
                throw IllegalStateException("native tag library failed to load", t)
            }
        }
    }

    /** True when the native library can be loaded on this JVM. */
    fun isAvailable(): Boolean {
        if (loaded) return true
        return try {
            ensureLoaded()
            true
        } catch (t: Throwable) {
            false
        }
    }

    fun failureMessage(): String? = failure?.toString()
}
