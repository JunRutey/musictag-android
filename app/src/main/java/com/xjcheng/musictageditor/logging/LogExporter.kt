/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.logging

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Copies the on-device log somewhere a human or `adb pull` can reach.
 *
 * The share sheet is not always available (no target app, no compatible activity), so this
 * writes a plain text file into the public Downloads collection instead: on Android 10+ through
 * MediaStore, on older releases straight into the Download directory with an app-private
 * fallback when the legacy permission is missing.
 */
object LogExporter {

    sealed interface Result {
        /** Path as a human reads it, e.g. `Download/musictag-log-20261031-034500.txt`. */
        data class Saved(val displayPath: String) : Result
        data class Failed(val reason: String) : Result
    }

    fun export(context: Context): Result {
        val source = AppLog.file(context)
        if (!source.isFile || source.length() == 0L) return Result.Failed("log is empty")
        val name = "musictag-log-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.txt"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToDownloads(context, source, name)
        } else {
            saveLegacy(context, source, name)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToDownloads(context: Context, source: File, name: String): Result {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = runCatching {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return Result.Failed("cannot create a Downloads entry")

        return runCatching {
            resolver.openOutputStream(uri)?.use { output -> source.inputStream().use { it.copyTo(output) } }
                ?: return Result.Failed("cannot open the Downloads entry")
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            AppLog.i("LogExport", "saved to Download/$name")
            Result.Saved("Download/$name")
        }.getOrElse { error ->
            runCatching { resolver.delete(uri, null, null) }
            AppLog.w("LogExport", "Downloads export failed", error)
            Result.Failed(error.message ?: "write failed")
        }
    }

    private fun saveLegacy(context: Context, source: File, name: String): Result {
        val target = runCatching {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                name,
            )
        }.getOrNull()
        val written = runCatching {
            requireNotNull(target)
            target.outputStream().use { output -> source.inputStream().use { it.copyTo(output) } }
            target
        }.getOrNull()
        if (written != null) {
            AppLog.i("LogExport", "saved to ${written.absolutePath}")
            return Result.Saved(written.absolutePath)
        }
        val fallback = File(context.getExternalFilesDir(null) ?: context.filesDir, name)
        return runCatching {
            fallback.outputStream().use { output -> source.inputStream().use { it.copyTo(output) } }
            AppLog.i("LogExport", "saved to ${fallback.absolutePath}")
            Result.Saved(fallback.absolutePath)
        }.getOrElse { error ->
            Result.Failed(error.message ?: "write failed")
        }
    }
}
