/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.xjcheng.musictageditor.MainActivity
import com.xjcheng.musictageditor.batch.BatchEdit
import com.xjcheng.musictageditor.batch.BatchField
import com.xjcheng.musictageditor.batch.BatchMode
import com.xjcheng.musictageditor.batch.BatchOps
import com.xjcheng.musictageditor.batch.BatchProgress
import com.xjcheng.musictageditor.batch.BatchRunner
import com.xjcheng.musictageditor.batch.BatchTagIo
import com.xjcheng.musictageditor.batch.BatchTarget
import com.xjcheng.musictageditor.core.tag.TagLibEngine
import com.xjcheng.musictageditor.io.DocumentSource
import com.xjcheng.musictageditor.io.DocumentWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Applies a [BatchEdit] to many files in the background.
 *
 * The loop itself lives in [BatchRunner] (unit tested); this service supplies the real tag
 * engine, keeps the process alive with a foreground notification showing progress, and
 * stops between files when the notification's cancel action is used.
 */
class BatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine = TagLibEngine()
    private var job: Job? = null

    @Volatile
    private var cancelled = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelled = true
                return START_NOT_STICKY
            }
        }

        val uris = intent?.getStringArrayListExtra(EXTRA_URIS).orEmpty()
        val edit = readEdit(intent)
        if (uris.isEmpty() || edit.isEmpty()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        cancelled = false
        startForegroundCompat(buildNotification(0, uris.size, null))

        job?.cancel()
        job = scope.launch {
            val items = uris.map { uri ->
                BatchTarget(uri = uri, displayName = uri.substringAfterLast('/'))
            }
            val runner = BatchRunner(
                readFields = { uri -> readFields(uri) },
                writeFields = { uri, values -> writeFields(uri, values) },
            )
            val results = runner.run(
                items = items,
                edit = edit,
                onProgress = { progress -> updateNotification(progress, uris.size) },
                isCancelled = { cancelled },
            )
            notifyFinished(runner.summarize(results), results.count { !it.success })
            stopForegroundCompat()
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------------ tag I/O

    private fun readFields(uri: String): Map<BatchField, String> {
        val document = DocumentSource.resolve(this, Uri.parse(uri))
            ?: error("cannot open $uri")
        val data = engine.read(document.path) ?: error("cannot read tags")
        return BatchTagIo.readFields(data)
    }

    /** Returns an error message, or null when the file was written back successfully. */
    private fun writeFields(uri: String, values: Map<BatchField, String>): String? {
        val parsed = Uri.parse(uri)
        val document = DocumentSource.resolve(this, parsed) ?: return "cannot open the file"
        val data = engine.read(document.path) ?: return "cannot read the tags"
        val result = engine.write(document.path, BatchTagIo.edit(data, values))
        if (!result.ok) return "the tag engine rejected the write"
        return when (val written = DocumentWriter.writeBack(this, parsed, document)) {
            is DocumentWriter.Result.Success -> null
            is DocumentWriter.Result.Failure -> written.message
        }
    }

    // ------------------------------------------------------------------- notification

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun updateNotification(progress: BatchProgress, total: Int) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(progress.processed, total, progress.currentName))
    }

    private fun notifyFinished(summary: String, failures: Int) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val text = if (failures == 0) summary else "$summary · check the failed files"
        manager.notify(NOTIFICATION_ID + 1, buildNotification(1, 1, text))
    }

    private fun buildNotification(processed: Int, total: Int, current: String?): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, BatchService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Writing tags")
            .setContentText(current ?: "$processed of $total")
            .setProgress(total, processed, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Cancel", cancel)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Batch tag writing", NotificationManager.IMPORTANCE_LOW),
        )
    }

    // ---------------------------------------------------------------------- extras

    private fun readEdit(intent: Intent?): BatchEdit {
        val lines = intent?.getStringArrayListExtra(EXTRA_FIELDS).orEmpty()
        val values = lines.mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val field = BatchField.entries.firstOrNull { it.name == line.substring(0, separator) }
                ?: return@mapNotNull null
            field to line.substring(separator + 1)
        }.toMap()
        val mode = if (intent?.getStringExtra(EXTRA_MODE) == MODE_FILL) BatchMode.FILL_MISSING else BatchMode.OVERWRITE
        return BatchEdit(values, mode)
    }

    companion object {
        const val ACTION_CANCEL = "com.xjcheng.musictageditor.action.BATCH_CANCEL"
        private const val EXTRA_URIS = "com.xjcheng.musictageditor.extra.URIS"
        private const val EXTRA_FIELDS = "com.xjcheng.musictageditor.extra.FIELDS"
        private const val EXTRA_MODE = "com.xjcheng.musictageditor.extra.MODE"
        private const val MODE_FILL = "fill"
        private const val CHANNEL = "batch"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, uris: List<String>, edit: BatchEdit) {
            val intent = Intent(context, BatchService::class.java)
                .putStringArrayListExtra(EXTRA_URIS, ArrayList(uris))
                .putStringArrayListExtra(
                    EXTRA_FIELDS,
                    ArrayList(edit.values.map { (field, value) -> "${field.name}=$value" }),
                )
                .putExtra(EXTRA_MODE, if (edit.mode == BatchMode.FILL_MISSING) MODE_FILL else "overwrite")
            // A foreground service must be started with startForegroundService from the
            // background; plain startService throws on Android 8+.
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BatchService::class.java).setAction(ACTION_CANCEL),
            )
        }
    }
}
