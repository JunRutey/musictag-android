/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.IBinder
import java.util.concurrent.Executors

/**
 * Tells MediaStore about files the app changed on disk.
 *
 * Tag writes and renames go through this service so the scan never blocks the UI thread,
 * and so a caller can hand over a whole batch at once. It stops itself when the queue is
 * empty; there is nothing to resume, which is why it is not sticky.
 */
class RefreshMediaStoreService : Service() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val paths = intent?.getStringArrayExtra(EXTRA_PATHS).orEmpty().filter { it.isNotBlank() }
        if (paths.isEmpty()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        executor.execute {
            runCatching {
                MediaScannerConnection.scanFile(applicationContext, paths.toTypedArray(), null, null)
            }
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PATHS = "com.xjcheng.musictageditor.extra.REFRESH_PATHS"

        /** Queues a scan for [paths]; safe to call from the UI thread. */
        fun start(context: Context, paths: List<String>) {
            val usable = paths.filter { it.isNotBlank() }
            if (usable.isEmpty()) return
            val intent = Intent(context, RefreshMediaStoreService::class.java)
                .putExtra(EXTRA_PATHS, usable.toTypedArray())
            runCatching { context.startService(intent) }
        }
    }
}
