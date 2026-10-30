/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.media

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads album artwork for the library rows.
 *
 * Artwork lives behind MediaStore's `albumart` provider, so the rows never open the audio
 * file itself. Thumbnails are requested at the row size and kept in an in-memory LRU, which
 * is why scrolling back up is instant and no disk cache is needed.
 */
class AlbumArtLoader private constructor(
    private val context: Context,
    private val targetPx: Int,
) {

    private val cache = object : LruCache<Long, ImageBitmap>(180) {}

    fun cached(albumId: Long): ImageBitmap? = if (albumId <= 0) null else cache.get(albumId)

    suspend fun load(albumId: Long): ImageBitmap? {
        if (albumId <= 0) return null
        cached(albumId)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bitmap = runCatching { decode(albumId) }.getOrNull() ?: return@withContext null
            val image = bitmap.asImageBitmap()
            cache.put(albumId, image)
            image
        }
    }

    private fun decode(albumId: Long): Bitmap? {
        val uri = ContentUris.withAppendedId(ALBUM_ART, albumId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.contentResolver.loadThumbnail(uri, Size(targetPx, targetPx), null)
            }.getOrNull()?.let { return it }
        }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options())
        }
    }

    companion object {
        private val ALBUM_ART: Uri = Uri.parse("content://media/external/audio/albumart")

        @Volatile
        private var instance: AlbumArtLoader? = null

        fun get(context: Context, targetPx: Int = 144): AlbumArtLoader =
            instance ?: synchronized(this) {
                instance ?: AlbumArtLoader(context.applicationContext, targetPx).also { instance = it }
            }
    }
}
