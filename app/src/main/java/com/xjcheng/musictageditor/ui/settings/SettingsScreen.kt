/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.xjcheng.musictageditor.settings.SettingGroup
import com.xjcheng.musictageditor.settings.SettingSpec
import com.xjcheng.musictageditor.settings.SettingType
import com.xjcheng.musictageditor.settings.Settings
import com.xjcheng.musictageditor.settings.SettingsSchema

/**
 * Settings, generated from [SettingsSchema] so the screen cannot drift from the stored keys.
 *
 * Edits are applied to the in-memory copy and persisted by the activity when it leaves the
 * foreground, which avoids writing the whole preference file on every keystroke.
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    onUpdate: (String, Any?) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Settings", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReset) { Text("Reset") }
                    OutlinedButton(onClick = onBack) { Text("Done") }
                }
            }
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                for (group in SettingGroup.entries) {
                    val specs = SettingsSchema.specs.filter { it.group == group }
                    if (specs.isEmpty()) continue
                    item(key = "header-${group.name}") {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(specs.size, key = { index -> specs[index].key }) { index ->
                        SettingRow(specs[index], settings, onUpdate)
                    }
                }
                item {
                    Text(
                        text = "${SettingsSchema.specs.size} preferences · " +
                            "stored in SharedPreferences(\"musictag_settings\")",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRow(spec: SettingSpec, settings: Settings, onUpdate: (String, Any?) -> Unit) {
    when (spec.type) {
        SettingType.BOOLEAN -> Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(spec.title, style = MaterialTheme.typography.bodyLarge)
                if (spec.summary.isNotBlank()) {
                    Text(
                        text = spec.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = settings.boolean(spec.key),
                onCheckedChange = { onUpdate(spec.key, it) },
            )
        }

        SettingType.INT -> OutlinedTextField(
            value = settings.int(spec.key).toString(),
            onValueChange = { text -> text.toIntOrNull()?.let { onUpdate(spec.key, it) } },
            label = { Text(spec.title) },
            supportingText = if (spec.summary.isNotBlank()) {
                { Text(spec.summary) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        SettingType.STRING -> OutlinedTextField(
            value = settings.string(spec.key),
            onValueChange = { onUpdate(spec.key, it) },
            label = { Text(spec.title) },
            supportingText = if (spec.summary.isNotBlank()) {
                { Text(spec.summary) }
            } else {
                null
            },
            singleLine = true,
            textStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
