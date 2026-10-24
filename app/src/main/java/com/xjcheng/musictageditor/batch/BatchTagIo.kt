/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.batch

import com.xjcheng.musictageditor.core.tag.TagData
import com.xjcheng.musictageditor.core.tag.TagEdit

/**
 * Converts between the tag engine's [TagData] and the batch model.
 *
 * Pure, so the mapping (including the `3/12` track/disc strings the engine exposes) is
 * covered by tests instead of being implicit inside the service loop.
 */
object BatchTagIo {

    fun readFields(data: TagData): Map<BatchField, String> = buildMap {
        put(BatchField.TITLE, data.title)
        put(BatchField.ARTIST, data.artist)
        put(BatchField.ALBUM, data.album)
        put(BatchField.ALBUM_ARTIST, data.albumArtist)
        put(BatchField.GENRE, data.genre)
        put(BatchField.COMPOSER, data.composer)
        put(BatchField.TRACK, data.trackStr.ifBlank { data.track.takeIf { it > 0 }?.toString().orEmpty() })
        put(BatchField.DISC, data.discStr.ifBlank { data.disc.takeIf { it > 0 }?.toString().orEmpty() })
        put(BatchField.YEAR, data.year)
        put(BatchField.COMMENT, data.comment)
        put(BatchField.LYRICS, data.lyrics)
    }

    /** Applies batch values on top of the file's current tags, leaving everything else. */
    fun edit(data: TagData, values: Map<BatchField, String>): TagEdit = TagEdit(
        title = values[BatchField.TITLE] ?: data.title,
        artist = values[BatchField.ARTIST] ?: data.artist,
        album = values[BatchField.ALBUM] ?: data.album,
        albumArtist = values[BatchField.ALBUM_ARTIST] ?: data.albumArtist,
        genre = values[BatchField.GENRE] ?: data.genre,
        composer = values[BatchField.COMPOSER] ?: data.composer,
        track = values[BatchField.TRACK] ?: data.trackStr,
        disc = values[BatchField.DISC] ?: data.discStr,
        year = values[BatchField.YEAR] ?: data.year,
        comment = values[BatchField.COMMENT] ?: data.comment,
        lyrics = values[BatchField.LYRICS] ?: data.lyrics,
    )
}
