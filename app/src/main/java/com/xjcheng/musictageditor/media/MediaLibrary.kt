/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * Reads the device's audio library.
 *
 * The query is deliberately broad (everything MediaStore considers music) and the settings
 * decide what is shown, through [MediaFilters] - which keeps the rules testable.
 */
class MediaLibrary(private val context: Context) {

    fun query(limit: Int = 5_000): List<MediaTrack> {
        val collection: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            @Suppress("DEPRECATION")
            add(MediaStore.Audio.Media.ALBUM_ID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.BITRATE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Audio.Media.DATA)
            }
        }.toTypedArray()

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val order = "${MediaStore.Audio.Media.ARTIST} COLLATE NOCASE, " +
            "${MediaStore.Audio.Media.ALBUM} COLLATE NOCASE, ${MediaStore.Audio.Media.TRACK}"

        val tracks = mutableListOf<MediaTrack>()
        context.contentResolver.query(collection, projection, selection, null, order)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val nameColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val trackColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val yearColumn = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val bitrateColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE)
            } else {
                -1
            }
            val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            }

            while (cursor.moveToNext() && tracks.size < limit) {
                val id = cursor.getLong(idColumn)
                tracks += MediaTrack(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    title = cursor.getString(titleColumn).orEmpty(),
                    artist = cursor.getString(artistColumn).orEmpty().unknownAsEmpty(),
                    album = cursor.getString(albumColumn).orEmpty().unknownAsEmpty(),
                    durationMs = cursor.getLong(durationColumn),
                    path = cursor.getString(pathColumn).orEmpty(),
                    mimeType = cursor.getString(mimeColumn).orEmpty(),
                    displayName = nameColumn.optionalString(cursor).orEmpty(),
                    albumId = albumIdColumn.optionalLong(cursor),
                    sizeBytes = sizeColumn.optionalLong(cursor),
                    track = trackColumn.optionalInt(cursor),
                    year = yearColumn.optionalInt(cursor),
                    bitrateBps = bitrateColumn.optionalInt(cursor),
                )
            }
        }
        return tracks
    }

    private fun Int.optionalString(cursor: android.database.Cursor): String? =
        if (this < 0) null else cursor.getString(this)

    private fun Int.optionalLong(cursor: android.database.Cursor): Long =
        if (this < 0) 0L else cursor.getLong(this)

    private fun Int.optionalInt(cursor: android.database.Cursor): Int =
        if (this < 0) 0 else cursor.getInt(this)

    /** MediaStore uses the literal string `<unknown>` for missing artist/album. */
    private fun String.unknownAsEmpty(): String = if (this == "<unknown>") "" else this
}
