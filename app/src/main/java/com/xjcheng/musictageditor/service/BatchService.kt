/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Batch-processing foreground service.
 *
 * Declared since 1.2.5.2 (`FOREGROUND_SERVICE` + `WAKE_LOCK`). The real work queue,
 * progress notifications and cancellation land in M5; M0 keeps the component present
 * and inert so the manifest contract stays valid.
 */
class BatchService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
