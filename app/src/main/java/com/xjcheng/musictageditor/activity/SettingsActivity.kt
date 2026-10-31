/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.xjcheng.musictageditor.R
import com.xjcheng.musictageditor.logging.AppLog
import com.xjcheng.musictageditor.logging.LogExporter
import com.xjcheng.musictageditor.settings.Appearance
import com.xjcheng.musictageditor.settings.Settings
import com.xjcheng.musictageditor.settings.SettingsRepository
import com.xjcheng.musictageditor.ui.settings.SettingsScreen
import com.xjcheng.musictageditor.ui.theme.MusicTagTheme

/**
 * Settings.
 *
 * Replaces the M0 placeholder while keeping the historical class name and the manifest
 * entry, so explicit-component callers keep working. The whole preference file is written
 * once, when the activity leaves the foreground; the appearance preference additionally
 * updates [Appearance] immediately so the switch repaints every screen.
 */
class SettingsActivity : ComponentActivity() {

    private val repository by lazy { SettingsRepository(this) }
    private var settings by mutableStateOf(Settings())
    private var logBytes by mutableStateOf(0L)
    private var exportSummary by mutableStateOf("")

    private fun appVersion(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull().orEmpty()

    private fun openRepository() {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL))) }
    }

    private fun logSummary(): String = if (logBytes <= 0L) {
        getString(R.string.settings_log_empty)
    } else {
        getString(R.string.settings_log_size, Formatter.formatFileSize(this, logBytes))
    }

    private fun exportLog() {
        AppLog.i("Settings", "log export requested")
        val uri = runCatching {
            FileProvider.getUriForFile(
                this,
                "$packageName.provider",
                AppLog.file(this),
            )
        }.getOrNull() ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.settings_export_log))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(
                Intent.createChooser(send, getString(R.string.settings_export_log))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }
    }

    private fun exportToDownloads() {
        when (val result = LogExporter.export(this)) {
            is LogExporter.Result.Saved -> {
                exportSummary = result.displayPath
                logBytes = AppLog.sizeBytes(this)
            }
            is LogExporter.Result.Failed -> {
                exportSummary = getString(R.string.settings_export_failed, result.reason)
            }
        }
    }

    private fun clearLog() {
        AppLog.clear(this)
        logBytes = AppLog.sizeBytes(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.init(this)
        Appearance.refresh(this)
        settings = repository.load()
        logBytes = AppLog.sizeBytes(this)
        setContent {
            MusicTagTheme {
                SettingsScreen(
                    settings = settings,
                    versionName = appVersion(),
                    repositoryUrl = REPOSITORY_URL,
                    logSummary = logSummary(),
                    exportSummary = exportSummary,
                    onOpenRepository = { openRepository() },
                    onExportLog = { exportLog() },
                    onExportToDownloads = { exportToDownloads() },
                    onClearLog = { clearLog() },
                    onUpdate = { key, value ->
                        settings = settings.with(key, value)
                        if (key == "dark_theme") {
                            Appearance.update(value as? Boolean ?: false)
                            AppLog.i("Settings", "dark_theme=$value")
                        }
                    },
                    onBack = { finish() },
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        repository.save(settings)
        logBytes = AppLog.sizeBytes(this)
    }
}

/** The public repository, shown in the About section. */
private const val REPOSITORY_URL = "https://github.com/JunRutey/musictag-android"
