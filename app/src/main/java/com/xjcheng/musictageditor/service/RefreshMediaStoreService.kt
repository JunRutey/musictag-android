/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * MediaStore refresh service.
 *
 * After tags are written the media database has to be told about changed files.
 * The batched implementation (scan queue, progress, wake lock) lands in M5.
 */
class RefreshMediaStoreService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
