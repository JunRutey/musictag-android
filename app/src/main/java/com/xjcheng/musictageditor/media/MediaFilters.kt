/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.media

/** One row of the device's audio library, independent of the Android APIs that produced it. */
data class MediaTrack(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    /** `DATA` on older releases, `RELATIVE_PATH` from Android 10 on. */
    val path: String,
    val mimeType: String,
)

/**
 * The library filters the settings describe.
 *
 * Pure functions over [MediaTrack] so the rules are unit-tested instead of being buried in
 * a `MediaStore` query; the query stays simple and the filtering happens here.
 */
object MediaFilters {

    /**
     * @param minDurationSeconds 0 disables the rule
     * @param ignoredDirectories newline or semicolon separated paths
     */
    fun shouldInclude(
        track: MediaTrack,
        minDurationSeconds: Int = 0,
        ignoredDirectories: String = "",
        ignoreMp4Videos: Boolean = true,
    ): Boolean {
        if (minDurationSeconds > 0 && track.durationMs < minDurationSeconds * 1_000L) return false
        if (isIgnoredDirectory(track, ignoredDirectories)) return false
        if (ignoreMp4Videos && isVideo(track)) return false
        return true
    }

    fun isIgnoredDirectory(track: MediaTrack, ignoredDirectories: String): Boolean {
        if (ignoredDirectories.isBlank()) return false
        val directories = ignoredDirectories
            .split('\n', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val path = track.path.replace('\\', '/')
        return directories.any { directory ->
            val normalised = directory.replace('\\', '/').trimEnd('/')
            path == normalised || path.startsWith("$normalised/")
        }
    }

    /**
     * MP4 files are usually videos with an audio track, which is why the original app let
     * users hide them. The extension wins over the MIME type: MediaStore reports
     * udio/mp4 for m4a *and* for the audio track of an mp4 video, while .m4a is always
     * an audio file.
     */
    fun isVideo(track: MediaTrack): Boolean {
        val mime = track.mimeType.lowercase()
        if (mime.startsWith("video/")) return true
        val path = track.path.lowercase()
        if (path.endsWith(".m4a")) return false
        return path.endsWith(".mp4") || path.endsWith(".m4v")
    }

    /** Case-insensitive match over title, artist and album. */
    fun matchesQuery(track: MediaTrack, query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        val haystack = listOf(track.title, track.artist, track.album).joinToString(" ")
        return haystack.contains(needle, ignoreCase = true)
    }

    fun apply(
        tracks: List<MediaTrack>,
        minDurationSeconds: Int,
        ignoredDirectories: String,
        ignoreMp4Videos: Boolean,
        query: String = "",
    ): List<MediaTrack> = tracks.filter { track ->
        shouldInclude(track, minDurationSeconds, ignoredDirectories, ignoreMp4Videos) &&
            matchesQuery(track, query)
    }
}
