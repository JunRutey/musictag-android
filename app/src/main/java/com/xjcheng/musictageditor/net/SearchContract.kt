/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.net

import android.content.Intent
import com.xjcheng.musictageditor.core.net.TagCandidate

/**
 * The extras contract between the editor and the search screen.
 *
 * Flat string extras rather than a Parcelable keep the two activities decoupled and make
 * the result trivially inspectable in bug reports.
 */
object SearchContract {

    const val REQUEST_CODE = 4201

    const val EXTRA_TITLE = "musictag.search.title"
    const val EXTRA_ARTIST = "musictag.search.artist"
    const val EXTRA_ALBUM = "musictag.search.album"

    const val RESULT_TITLE = "musictag.result.title"
    const val RESULT_ARTIST = "musictag.result.artist"
    const val RESULT_ALBUM = "musictag.result.album"
    const val RESULT_ALBUM_ARTIST = "musictag.result.albumArtist"
    const val RESULT_GENRE = "musictag.result.genre"
    const val RESULT_COMPOSER = "musictag.result.composer"
    const val RESULT_TRACK = "musictag.result.track"
    const val RESULT_DISC = "musictag.result.disc"
    const val RESULT_YEAR = "musictag.result.year"
    const val RESULT_COMMENT = "musictag.result.comment"
    const val RESULT_LYRICS = "musictag.result.lyrics"
    const val RESULT_COVER_URL = "musictag.result.coverUrl"

    fun request(title: String, artist: String, album: String): Intent =
        Intent()
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_ARTIST, artist)
            .putExtra(EXTRA_ALBUM, album)

    fun write(candidate: TagCandidate): Intent = Intent().apply {
        candidate.title?.let { putExtra(RESULT_TITLE, it) }
        candidate.artist?.let { putExtra(RESULT_ARTIST, it) }
        candidate.album?.let { putExtra(RESULT_ALBUM, it) }
        candidate.albumArtist?.let { putExtra(RESULT_ALBUM_ARTIST, it) }
        candidate.genre?.let { putExtra(RESULT_GENRE, it) }
        candidate.composer?.let { putExtra(RESULT_COMPOSER, it) }
        candidate.track?.let { putExtra(RESULT_TRACK, it) }
        candidate.disc?.let { putExtra(RESULT_DISC, it) }
        candidate.year?.let { putExtra(RESULT_YEAR, it) }
        candidate.comment?.let { putExtra(RESULT_COMMENT, it) }
        candidate.lyrics?.let { putExtra(RESULT_LYRICS, it) }
        candidate.coverUrl?.let { putExtra(RESULT_COVER_URL, it) }
    }

    fun read(data: Intent): TagCandidate = TagCandidate(
        title = data.getStringExtra(RESULT_TITLE),
        artist = data.getStringExtra(RESULT_ARTIST),
        album = data.getStringExtra(RESULT_ALBUM),
        albumArtist = data.getStringExtra(RESULT_ALBUM_ARTIST),
        genre = data.getStringExtra(RESULT_GENRE),
        composer = data.getStringExtra(RESULT_COMPOSER),
        track = data.getStringExtra(RESULT_TRACK),
        disc = data.getStringExtra(RESULT_DISC),
        year = data.getStringExtra(RESULT_YEAR),
        comment = data.getStringExtra(RESULT_COMMENT),
        lyrics = data.getStringExtra(RESULT_LYRICS),
        coverUrl = data.getStringExtra(RESULT_COVER_URL),
    )
}
