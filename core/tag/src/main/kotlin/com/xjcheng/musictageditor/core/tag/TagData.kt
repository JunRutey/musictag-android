/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.tag

/**
 * The tag model.
 *
 * Field names follow the original application's twelve editable fields, in the same
 * order, so behaviour can be compared one-to-one while the implementation is new:
 * title, artist, album, albumArtist, genre, composer, track, trackStr, disc, discStr,
 * year, comment. Lyrics and pictures travel with the tag but are edited separately.
 */
data class TagData(
    val container: String,
    val tagFormat: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val composer: String = "",
    val track: Int = 0,
    val trackStr: String = "",
    val disc: Int = 0,
    val discStr: String = "",
    val year: String = "",
    val comment: String = "",
    val lyrics: String = "",
    val audio: AudioProperties = AudioProperties.EMPTY,
    val pictures: List<TagPicture> = emptyList(),
) {
    /** Human readable summary, used by the M1 diagnostics screen. */
    fun summary(): String = buildString {
        append(container)
        if (tagFormat != "unknown") append(" / ").append(tagFormat)
        if (audio.durationMs > 0) append(" · ").append(audio.durationMs / 1000).append("s")
        if (audio.sampleRateHz > 0) append(" · ").append(audio.sampleRateHz).append("Hz")
        if (audio.channels > 0) append(" · ").append(audio.channels).append("ch")
        if (pictures.isNotEmpty()) append(" · ").append(pictures.size).append(" cover(s)")
    }
}

data class AudioProperties(
    val durationMs: Int = 0,
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 0,
    val channels: Int = 0,
    val bitsPerSample: Int = 0,
) {
    companion object {
        val EMPTY = AudioProperties()
    }
}

class TagPicture(
    val mimeType: String,
    val data: ByteArray,
    /** ID3v2 picture type: 3 front cover, 4 back cover, 8 artist, 0 other. */
    val type: Int = FRONT_COVER,
) {
    val size: Int get() = data.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TagPicture) return false
        return mimeType == other.mimeType && type == other.type && data.contentEquals(other.data)
    }

    override fun hashCode(): Int =
        31 * (31 * mimeType.hashCode() + type) + data.contentHashCode()

    override fun toString(): String = "TagPicture($mimeType, type=$type, ${data.size} bytes)"

    companion object {
        const val OTHER = 0
        const val FRONT_COVER = 3
        const val BACK_COVER = 4
        const val ARTIST = 8
    }
}

/** Reads and writes tags of one file. */
interface TagEngine {
    /** Returns null when the file is missing, unreadable, or not a recognised audio container. */
    fun read(path: String): TagData?

    /** Applies [edit] to the file in place. */
    fun write(path: String, edit: TagEdit): WriteResult

    /** Upstream TagLib version, useful as a smoke test and in bug reports. */
    fun version(): String
}
