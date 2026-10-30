/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xjcheng.musictageditor.activity.SearchActivity
import com.xjcheng.musictageditor.activity.SearchMediaActivity
import com.xjcheng.musictageditor.activity.SettingsActivity
import com.xjcheng.musictageditor.ui.theme.MusicTagTheme

/**
 * Launcher screen.
 *
 * The original app opened straight into a library; that browser arrives with the media
 * index, so for now this is the entry point that makes the rest reachable: pick a file to
 * edit (the same editor the intents open), search tags online, or change settings. The
 * compatibility surface stays live regardless of this screen.
 */
class MainActivity : ComponentActivity() {

    private val openFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        startEditor(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MusicTagTheme {
                HomeScreen(
                    version = BuildConfig.VERSION_NAME,
                    onOpenFile = { pickFile() },
                    onLibrary = { startActivity(Intent(this, SearchMediaActivity::class.java)) },
                    onSearch = { startActivity(Intent(this, SearchActivity::class.java)) },
                    onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                )
            }
        }
    }

    private fun pickFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_MIME_TYPES, AUDIO_TYPES)
        }
        openFile.launch(intent)
    }

    private fun startEditor(uri: android.net.Uri) {
        startActivity(
            Intent(this, SongDetailActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
        )
    }

    private companion object {
        val AUDIO_TYPES = arrayOf(
            "audio/mpeg",
            "audio/flac",
            "audio/x-flac",
            "audio/ogg",
            "audio/mp4",
            "audio/x-m4a",
            "audio/x-ape",
            "audio/x-wav",
            "audio/aiff",
            "audio/x-ms-wma",
        )
    }
}

@Composable
private fun HomeScreen(
    version: String,
    onOpenFile: () -> Unit,
    onLibrary: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = stringResource(R.string.home_version, version, "com.xjcheng.musictageditor"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onLibrary, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_library))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenFile, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_open_file))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onSearch, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_search))
                }
                OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_settings))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.home_compat),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_engine),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
