/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.tag

/**
 * TagLib-backed implementation of [TagEngine].
 *
 * The native side returns one flat `Array<String>`; everything below is pure Kotlin so
 * the array contract can be tested on its own.
 */
class TagLibEngine : TagEngine {

    override fun version(): String {
        NativeLibrary.ensureLoaded()
        return NativeTagBridge.taglibVersion()
    }

    override fun read(path: String): TagData? {
        if (path.isEmpty()) return null
        NativeLibrary.ensureLoaded()
        val raw = NativeTagBridge.readAll(path) ?: return null
        return parse(raw)
    }

    override fun write(path: String, edit: TagEdit): WriteResult {
        if (path.isEmpty()) return WriteResult.Failure("empty path")
        NativeLibrary.ensureLoaded()

        val coverBytes: ByteArray?
        val coverMime: String?
        when (val cover = edit.cover) {
            is CoverEdit.Unchanged -> { coverBytes = null; coverMime = null }
            is CoverEdit.Remove -> { coverBytes = ByteArray(0); coverMime = "" }
            is CoverEdit.Replace -> { coverBytes = cover.data; coverMime = cover.mimeType }
        }

        var options = 0
        if (edit.writeId3v1) options = options or OPTION_ID3V1
        if (edit.id3v23) options = options or OPTION_ID3V23

        val pictures = edit.pictures
        val error = if (pictures != null) {
            NativeTagBridge.writeAllWithPictures(
                path,
                edit.fields(),
                pictures.map { it.mimeType }.toTypedArray(),
                pictures.map { it.data }.toTypedArray(),
                pictures.map { it.type }.toIntArray(),
                options,
            )
        } else {
            NativeTagBridge.writeAll(path, edit.fields(), coverBytes, coverMime, options)
        }
        return if (error == null) WriteResult.Success else WriteResult.Failure(error)
    }

    /** Exposed for tests: turns the JNI array into the model. */
    internal fun parse(raw: Array<String>): TagData? {
        if (raw.size < FIELD_COUNT) return null

        fun text(index: Int): String = raw.getOrNull(index) ?: ""

        // Tolerates a combined "3/12" in case a future engine revision reports it.
        fun number(index: Int): Int = text(index).takeWhile { it.isDigit() }.toIntOrNull() ?: 0

        val pictureCount = number(PICTURE_COUNT)
        val pictures = ArrayList<TagPicture>(pictureCount.coerceAtLeast(0))
        for (index in 0 until pictureCount) {
            val base = PICTURES + index * PICTURE_STRIDE
            val mime = text(base)
            val encoded = text(base + 1)
            val type = number(base + 2)
            if (encoded.isEmpty()) continue
            val bytes = Base64Codec.decode(encoded)
            if (bytes.isNotEmpty()) {
                pictures.add(
                    TagPicture(
                        mimeType = mime,
                        data = bytes,
                        type = if (type > 0) type else TagPicture.FRONT_COVER,
                    ),
                )
            }
        }

        return TagData(
            container = text(CONTAINER),
            tagFormat = text(TAG_FORMAT),
            title = text(TITLE),
            artist = text(ARTIST),
            album = text(ALBUM),
            albumArtist = text(ALBUM_ARTIST),
            genre = text(GENRE),
            composer = text(COMPOSER),
            track = number(TRACK),
            trackStr = text(TRACK_STR),
            disc = number(DISC),
            discStr = text(DISC_STR),
            year = text(YEAR),
            comment = text(COMMENT),
            lyrics = text(LYRICS),
            audio = AudioProperties(
                durationMs = number(DURATION_MS),
                bitrateKbps = number(BITRATE),
                sampleRateHz = number(SAMPLE_RATE),
                channels = number(CHANNELS),
                bitsPerSample = number(BITS_PER_SAMPLE),
            ),
            pictures = pictures,
        )
    }

    private companion object {
        const val CONTAINER = 0
        const val TAG_FORMAT = 1
        const val TITLE = 2
        const val ARTIST = 3
        const val ALBUM = 4
        const val ALBUM_ARTIST = 5
        const val GENRE = 6
        const val COMPOSER = 7
        const val TRACK = 8
        const val TRACK_STR = 9
        const val DISC = 10
        const val DISC_STR = 11
        const val YEAR = 12
        const val COMMENT = 13
        const val LYRICS = 14
        const val DURATION_MS = 15
        const val BITRATE = 16
        const val SAMPLE_RATE = 17
        const val CHANNELS = 18
        const val BITS_PER_SAMPLE = 19
        const val PICTURE_COUNT = 20
        const val PICTURES = 21
        const val PICTURE_STRIDE = 3
        const val FIELD_COUNT = 21

        const val OPTION_ID3V1 = 1
        const val OPTION_ID3V23 = 2
    }
}

/**
 * Minimal base64 decoder.
 *
 * `java.util.Base64` is API 26+ on Android and `android.util.Base64` does not exist on
 * the JVM, so this module carries its own ten lines instead of choosing a platform.
 */
internal object Base64Codec {
    private val DECODE = IntArray(128) { -1 }.apply {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        alphabet.forEachIndexed { index, c -> this[c.code] = index }
    }

    fun decode(text: String): ByteArray {
        if (text.isEmpty()) return ByteArray(0)
        val out = java.io.ByteArrayOutputStream(text.length * 3 / 4 + 3)
        var buffer = 0
        var bits = 0
        for (c in text) {
            if (c == '=') break
            val value = if (c.code < 128) DECODE[c.code] else -1
            if (value < 0) continue
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }
}
