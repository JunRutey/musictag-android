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
                )
            }
        }
        return tracks
    }

    /** MediaStore uses the literal string `<unknown>` for missing artist/album. */
    private fun String.unknownAsEmpty(): String = if (this == "<unknown>") "" else this
}
