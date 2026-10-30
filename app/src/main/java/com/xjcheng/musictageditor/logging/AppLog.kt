/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.logging

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small on-device log that can be exported without adb.
 *
 * Writes timestamped lines (plus stack traces) to `files/logs/app.log`, rotates at
 * [MAX_BYTES] into `app.log.1`, and mirrors everything to logcat under the same tag. An
 * uncaught exception handler appends the crash before the process dies, so "the app just
 * closed" still leaves evidence behind.
 */
object AppLog {

    private const val ROOT_TAG = "MusicTag"
    private const val DIR = "logs"
    private const val FILE_NAME = "app.log"
    private const val ARCHIVE_NAME = "app.log.1"
    private const val MAX_BYTES = 512 * 1024

    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var directory: File? = null

    @Volatile
    private var handlerInstalled = false

    /** Binds the log to this app's private storage; safe to call more than once. */
    fun init(context: Context) {
        if (directory != null) return
        synchronized(this) {
            if (directory != null) return
            val dir = File(context.applicationContext.filesDir, DIR).apply { mkdirs() }
            directory = dir
            if (!handlerInstalled) {
                handlerInstalled = true
                installCrashHandler()
            }
            i("AppLog", "session started on ${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}")
        }
    }

    fun i(tag: String, message: String) {
        Log.i("$ROOT_TAG/$tag", message)
        append("I", tag, message, null)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        Log.w("$ROOT_TAG/$tag", message, error)
        append("W", tag, message, error)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        Log.e("$ROOT_TAG/$tag", message, error)
        append("E", tag, message, error)
    }

    fun file(context: Context): File {
        init(context)
        return File(directory ?: context.filesDir, FILE_NAME)
    }

    fun sizeBytes(context: Context): Long {
        val dir = file(context).parentFile
        return (dir?.listFiles()?.sumOf { it.length() }) ?: 0L
    }

    fun clear(context: Context) {
        init(context)
        val dir = directory ?: return
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        i("AppLog", "log cleared")
    }

    private fun append(level: String, tag: String, message: String, error: Throwable?) {
        val dir = directory ?: return
        val line = buildString {
            append(stamp.format(Date()))
            append(' ')
            append(level)
            append(' ')
            append(tag)
            append(": ")
            append(message)
            if (error != null) {
                append('\n')
                append(stackTraceOf(error))
            }
            append('\n')
        }
        synchronized(this) {
            runCatching {
                val file = File(dir, FILE_NAME)
                if (file.isFile && file.length() + line.length > MAX_BYTES) {
                    val archive = File(dir, ARCHIVE_NAME)
                    archive.delete()
                    file.renameTo(archive)
                }
                file.appendText(line, Charsets.UTF_8)
            }
        }
    }

    private fun stackTraceOf(error: Throwable): String {
        val writer = StringWriter()
        PrintWriter(writer).use { error.printStackTrace(it) }
        return writer.toString().trimEnd()
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            e("Crash", "uncaught in ${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }
    }
}
