/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.activity

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xjcheng.musictageditor.R
import com.xjcheng.musictageditor.batch.BatchEdit
import com.xjcheng.musictageditor.batch.BatchField
import com.xjcheng.musictageditor.batch.BatchMode
import com.xjcheng.musictageditor.service.BatchService
import com.xjcheng.musictageditor.ui.batch.BatchEditScreen
import com.xjcheng.musictageditor.ui.batch.BatchUiState
import com.xjcheng.musictageditor.ui.theme.MusicTagTheme

/**
 * Batch tag editing.
 *
 * Reached by other apps sending one or many files (`SEND` / `SEND_MULTIPLE`) or by an
 * explicit component call with [EXTRA_URIS]. Collects the fields to write and hands the
 * job to [BatchService], which shows progress in a notification and can be cancelled from
 * there.
 */
class BatchEditSongsDetail : ComponentActivity() {

    private var ui by mutableStateOf(BatchUiState())

    private val uris: List<String> by lazy { collectUris(intent) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ui.copy(fileCount = uris.size)
        setContent {
            MusicTagTheme {
                BatchEditScreen(
                    state = ui,
                    onValueChange = { field, value -> ui = ui.copy(values = ui.values + (field to value)) },
                    onFillMissingChange = { ui = ui.copy(fillMissingOnly = it) },
                    onStart = { startBatch() },
                    onCancel = { finish() },
                )
            }
        }
    }

    private fun startBatch() {
        if (uris.isEmpty()) {
            ui = ui.copy(message = getString(R.string.batch_error_no_files))
            return
        }
        val values = ui.values.filterValues { it.isNotBlank() }.mapKeys { it.key }
        if (values.isEmpty()) {
            ui = ui.copy(message = getString(R.string.batch_error_no_fields))
            return
        }
        val edit = BatchEdit(
            values = values,
            mode = if (ui.fillMissingOnly) BatchMode.FILL_MISSING else BatchMode.OVERWRITE,
        )
        BatchService.start(this, uris, edit)
        finish()
    }

    private fun collectUris(intent: Intent?): List<String> {
        if (intent == null) return emptyList()
        intent.getStringArrayListExtra(EXTRA_URIS)?.let { return it.filter { uri -> uri.isNotBlank() } }

        val streams = mutableListOf<Uri>()
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { streams += it }
        } else {
            parcelableStream(intent)?.let { streams += it }
        }
        intent.data?.let { streams += it }
        return streams.map { it.toString() }.distinct()
    }

    private fun parcelableStream(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    companion object {
        const val EXTRA_URIS = "com.xjcheng.musictageditor.extra.BATCH_URIS"

        /** Field labels are part of no contract; this only exists for tests and callers. */
        val FIELD_NAMES: List<String> = BatchField.entries.map { it.name }
    }
}
