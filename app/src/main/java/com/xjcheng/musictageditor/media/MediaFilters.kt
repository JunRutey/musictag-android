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
    /** File name (`DISPLAY_NAME`); the most reliable source of the container format. */
    val displayName: String = "",
    /** MediaStore album id, used to look up the cover artwork. */
    val albumId: Long = 0,
    val sizeBytes: Long = 0,
    val track: Int = 0,
    val year: Int = 0,
    /** Bits per second reported by MediaStore (API 30+); 0 when unknown. */
    val bitrateBps: Int = 0,
) {
    /**
     * Short uppercase container label for the cover badge, e.g. `FLAC`.
     *
     * The extension wins over the MIME type because MediaStore reports `audio/mp4` for both
     * `m4a` and the audio track of an `mp4` video.
     */
    val format: String
        get() {
            val extension = displayName.substringAfterLast('.', "").lowercase()
                .ifEmpty { path.substringAfterLast('.', "").lowercase().takeIf { it.all(Char::isLetterOrDigit) }.orEmpty() }
            FORMATS_BY_EXTENSION[extension]?.let { return it }
            val mime = mimeType.lowercase()
            FORMATS_BY_MIME.entries.firstOrNull { mime == it.key || mime.startsWith(it.key) }?.let { return it.value }
            return extension.uppercase().take(4)
        }

    /** Display bitrate in kbps, derived from the file size when MediaStore has none. */
    val bitrateKbps: Int
        get() {
            if (bitrateBps > 0) return bitrateBps / 1000
            if (sizeBytes > 0 && durationMs > 0) return (sizeBytes * 8 / durationMs).toInt()
            return 0
        }

    private companion object {
        val FORMATS_BY_EXTENSION: Map<String, String> = mapOf(
            "mp3" to "MP3",
            "mp2" to "MP2",
            "flac" to "FLAC",
            "m4a" to "M4A",
            "m4b" to "M4A",
            "aac" to "AAC",
            "ogg" to "OGG",
            "oga" to "OGG",
            "opus" to "OPUS",
            "ape" to "APE",
            "wav" to "WAV",
            "aif" to "AIFF",
            "aiff" to "AIFF",
            "wma" to "WMA",
            "dsf" to "DSF",
            "dff" to "DFF",
            "mpc" to "MPC",
            "wv" to "WV",
            "tta" to "TTA",
            "m4v" to "MP4",
            "mp4" to "MP4",
        )

        val FORMATS_BY_MIME: Map<String, String> = mapOf(
            "audio/mpeg" to "MP3",
            "audio/mp3" to "MP3",
            "audio/flac" to "FLAC",
            "audio/x-flac" to "FLAC",
            "audio/mp4" to "M4A",
            "audio/x-m4a" to "M4A",
            "audio/aac" to "AAC",
            "audio/ogg" to "OGG",
            "audio/opus" to "OPUS",
            "audio/x-ape" to "APE",
            "audio/wav" to "WAV",
            "audio/x-wav" to "WAV",
            "audio/aiff" to "AIFF",
            "audio/x-ms-wma" to "WMA",
            "audio/x-dsf" to "DSF",
            "audio/x-dff" to "DFF",
        )
    }
}

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
     * `audio/mp4` for m4a *and* for the audio track of an mp4 video, while .m4a is always
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
        return matchesNormalizedQuery(track, needle)
    }

    private fun matchesNormalizedQuery(track: MediaTrack, needle: String): Boolean {
        if (needle.isEmpty()) return true
        if (track.title.contains(needle, ignoreCase = true) ||
            track.artist.contains(needle, ignoreCase = true) ||
            track.album.contains(needle, ignoreCase = true)
        ) {
            return true
        }
        if (!needle.contains(' ')) return false

        val title = track.title
        val artist = track.artist
        val album = track.album
        val totalLength = title.length + artist.length + album.length + 2
        if (needle.length > totalLength) return false
        for (start in 0..totalLength - needle.length) {
            var matches = true
            for (offset in needle.indices) {
                val index = start + offset
                val character = when {
                    index < title.length -> title[index]
                    index == title.length -> ' '
                    index < title.length + artist.length + 1 ->
                        artist[index - title.length - 1]
                    index == title.length + artist.length + 1 -> ' '
                    else -> album[index - title.length - artist.length - 2]
                }
                if (!character.equals(needle[offset], ignoreCase = true)) {
                    matches = false
                    break
                }
            }
            if (matches) return true
        }
        return false
    }

    fun apply(
        tracks: List<MediaTrack>,
        minDurationSeconds: Int,
        ignoredDirectories: String,
        ignoreMp4Videos: Boolean,
        query: String = "",
    ): List<MediaTrack> {
        val directories = parseIgnoredDirectories(ignoredDirectories)
        val needle = query.trim()
        return tracks.filter { track ->
            (minDurationSeconds <= 0 || track.durationMs >= minDurationSeconds * 1_000L) &&
                !isIgnoredDirectory(track, directories) &&
                (!ignoreMp4Videos || !isVideo(track)) &&
                matchesNormalizedQuery(track, needle)
        }
    }

    private fun parseIgnoredDirectories(value: String): List<String> =
        value.split('\n', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.replace('\\', '/').trimEnd('/') }

    private fun isIgnoredDirectory(track: MediaTrack, directories: List<String>): Boolean {
        if (directories.isEmpty()) return false
        val path = track.path.replace('\\', '/')
        return directories.any { directory ->
            path == directory || path.startsWith("$directory/")
        }
    }
}
