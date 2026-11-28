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
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * Loads album artwork for the library rows.
 *
 * On Android 10+ (API 29) the old `content://media/external/audio/albumart/<id>`
 * URI is no longer openable by third-party apps: MediaStore deprecated the
 * `ALBUM_ART` column with scoped storage, and `openInputStream`/`loadThumbnail`
 * on that URI throws `FileNotFoundException` or `UnsupportedOperationException`.
 * The supported API is [android.content.ContentResolver.loadThumbnail] with the
 * **audio media URI** (`content://media/external/audio/media/<trackId>`), which
 * resolves the embedded cover through the media provider. Older Android
 * releases can still open the legacy albumart URI, so that path is kept only
 * as a fallback below API 29.
 *
 * Thumbnails are requested at the row size and kept in an in-memory LRU, which
 * is why scrolling back up is instant and no disk cache is needed. Concurrent
 * requests for the same album share one decode, and the cache is bounded by
 * estimated bitmap memory.
 */
class AlbumArtLoader private constructor(
    private val context: Context,
    private val targetPx: Int,
) {

    private val cache = object : LruCache<Long, ImageBitmap>(cacheSizeKb()) {
        override fun sizeOf(key: Long, value: ImageBitmap): Int =
            (value.width.toLong() * value.height * 4 / 1024)
                .coerceIn(1, Int.MAX_VALUE.toLong())
                .toInt()
    }
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<Long, Deferred<ImageBitmap?>>()

    fun cached(albumId: Long): ImageBitmap? = if (albumId <= 0) null else cache.get(albumId)

    /**
     * Loads the cover for [albumId]. [audioUri] is the MediaStore URI of any track in
     * that album; it is the only artwork source that works on Android 10 and above.
     */
    suspend fun load(albumId: Long, audioUri: Uri? = null): ImageBitmap? {
        if (albumId <= 0) return null
        cached(albumId)?.let { return it }
        val request = inFlight.computeIfAbsent(albumId) {
            loadScope.async {
                cached(albumId)?.let { return@async it }
                val bitmap = decode(albumId, audioUri) ?: return@async null
                bitmap.asImageBitmap().also { cache.put(albumId, it) }
            }
        }
        request.invokeOnCompletion { inFlight.remove(albumId, request) }
        return request.await()
    }

    private fun decode(albumId: Long, audioUri: Uri?): Bitmap? {
        // API 29+: loadThumbnail on the *audio* URI is the documented way to get
        // an album cover. Passing the legacy albumart URI here fails with
        // FileNotFoundException/UnsupportedOperationException on modern Android.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && audioUri != null) {
            safeDecode { context.contentResolver.loadThumbnail(audioUri, Size(targetPx, targetPx), null) }
                ?.let { return it }
        }

        // Pre-Q fallback: the albumart provider URI is still directly openable.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val uri = ContentUris.withAppendedId(ALBUM_ART, albumId)
            safeDecode { context.contentResolver.loadThumbnail(uri, Size(targetPx, targetPx), null) }
                ?.let { return it }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val gotBounds = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                    true
                } ?: false
            } catch (_: Exception) {
                false
            }
            if (gotBounds && bounds.outWidth > 0 && bounds.outHeight > 0) {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetPx)
                }
                safeDecode {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, options)
                    }
                }?.let { return it }
            }
        }
        return null
    }

    /** MediaStore throws several exception types for missing/unsupported artwork. */
    private inline fun safeDecode(block: () -> Bitmap?): Bitmap? = try {
        block()
    } catch (_: FileNotFoundException) {
        null
    } catch (_: java.io.IOException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: UnsupportedOperationException) {
        null
    }

    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        val targetSize = target.coerceAtLeast(1)
        var sample = 1
        while (sample <= Int.MAX_VALUE / 2 &&
            maxOf(width, height) / (sample * 2) >= targetSize
        ) {
            sample *= 2
        }
        return sample
    }

    companion object {
        private val ALBUM_ART: Uri = Uri.parse("content://media/external/audio/albumart")

        private fun cacheSizeKb(): Int =
            (Runtime.getRuntime().maxMemory() / 1024 / 8)
                .coerceIn(1024, Int.MAX_VALUE.toLong())
                .toInt()

        @Volatile
        private var instance: AlbumArtLoader? = null

        fun get(context: Context, targetPx: Int = 144): AlbumArtLoader =
            instance ?: synchronized(this) {
                instance ?: AlbumArtLoader(context.applicationContext, targetPx).also { instance = it }
            }
    }
}
