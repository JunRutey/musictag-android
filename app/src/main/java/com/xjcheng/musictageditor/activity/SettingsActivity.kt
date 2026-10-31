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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.xjcheng.musictageditor.io.IgnoreDirectory
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

    private val pickDirectory =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@registerForActivityResult
            IgnoreDirectory.persist(this, uri)
            val path = IgnoreDirectory.relativePath(uri)
            if (path == null) {
                AppLog.w("Settings", "cannot read the relative path of $uri")
                return@registerForActivityResult
            }
            settings = settings.copy(
                values = settings.values + ("dirpath_of_ignore_medias" to
                    IgnoreDirectory.add(settings.ignoreDirectories, path)),
            )
            AppLog.i("Settings", "ignore directory added: $path")
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
                    onPickIgnoreDirectory = { pickDirectory.launch(null) },
                    onClearIgnoreDirectories = {
                        settings = settings.copy(
                            values = settings.values + ("dirpath_of_ignore_medias" to ""),
                        )
                    },
                    onUpdate = { key, value ->
                        settings = settings.with(key, value)
                        when (key) {
                            // Apply from the in-memory copy: refreshing from storage here
                            // would read the previous value, because the file is only
                            // written when the activity pauses.
                            "theme_mode", "dynamic_color" -> {
                                Appearance.update(settings.themeMode, settings.dynamicColor)
                                repository.save(settings)
                                AppLog.i("Settings", "appearance=${Appearance.mode}")
                            }
                            "verbose_logging" -> {
                                AppLog.setVerbose(settings.verboseLogging)
                                repository.save(settings)
                                AppLog.i("Settings", "verbose logging=${settings.verboseLogging}")
                            }
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
