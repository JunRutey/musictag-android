/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.tag

/** How a write should treat one typed picture. */
class PictureEdit(
    val data: ByteArray,
    val mimeType: String,
    val type: Int = TagPicture.FRONT_COVER,
)

/** How a write should treat the embedded cover. */
sealed interface CoverEdit {
    /** Leave the existing artwork alone. */
    data object Unchanged : CoverEdit

    /** Delete the embedded artwork. */
    data object Remove : CoverEdit

    /** Replace the embedded artwork. */
    class Replace(val data: ByteArray, val mimeType: String) : CoverEdit
}

/**
 * The editable state of one file's tag.
 *
 * Field order follows the original application's editor so behaviour can be compared
 * one-to-one. Track and disc are kept in their combined `"3/12"` form, which is what
 * users see in the UI; the engine splits them when writing.
 */
data class TagEdit(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val composer: String = "",
    val track: String = "",
    val disc: String = "",
    val year: String = "",
    val comment: String = "",
    val lyrics: String = "",
    val cover: CoverEdit = CoverEdit.Unchanged,
    /**
     * A complete typed picture set: null leaves artwork alone, an empty list removes every
     * picture, otherwise the file ends up with exactly these pictures. When set it wins
     * over [cover].
     */
    val pictures: List<PictureEdit>? = null,
    /** MP3 only: also write an ID3v1 tag alongside ID3v2. */
    val writeId3v1: Boolean = false,
    /** MP3 only: write ID3v2.3 instead of ID3v2.4. */
    val id3v23: Boolean = false,
) {
    /** The flat 13-value array the native layer expects. */
    fun fields(): Array<String> = arrayOf(
        title,
        artist,
        album,
        albumArtist,
        genre,
        composer,
        track.substringBefore('/').trim(),
        track,
        disc.substringBefore('/').trim(),
        disc,
        year,
        comment,
        lyrics,
    )

    companion object {
        /** Pre-fills the editor from a freshly read file. */
        fun from(tag: TagData): TagEdit = TagEdit(
            title = tag.title,
            artist = tag.artist,
            album = tag.album,
            albumArtist = tag.albumArtist,
            genre = tag.genre,
            composer = tag.composer,
            track = tag.trackStr.ifBlank { if (tag.track > 0) tag.track.toString() else "" },
            disc = tag.discStr.ifBlank { if (tag.disc > 0) tag.disc.toString() else "" },
            year = tag.year,
            comment = tag.comment,
            lyrics = tag.lyrics,
        )
    }
}

sealed interface WriteResult {
    data object Success : WriteResult

    data class Failure(val message: String) : WriteResult

    val ok: Boolean get() = this is Success
}
