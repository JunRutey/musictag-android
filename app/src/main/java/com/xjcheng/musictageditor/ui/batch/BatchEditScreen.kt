/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.ui.batch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import com.xjcheng.musictageditor.batch.BatchField

/** What the batch dialog renders. */
data class BatchUiState(
    val fileCount: Int = 0,
    val values: Map<BatchField, String> = emptyMap(),
    val fillMissingOnly: Boolean = false,
    val message: String? = null,
)

/**
 * Batch field editor.
 *
 * Only fields the user types into are applied, so a batch cannot clear tags by accident.
 * Clearing a field is not exposed here yet; [BatchEdit] itself supports it (a present but
 * empty value means "clear"), which is what the future explicit-clear action will use.
 */
@Composable
fun BatchEditScreen(
    state: BatchUiState,
    onValueChange: (BatchField, String) -> Unit,
    onFillMissingChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Batch edit", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${state.fileCount} file(s) selected · " +
                        "${state.values.size} field(s) to write",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Only fill missing values", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Existing tags are left untouched.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.fillMissingOnly, onCheckedChange = onFillMissingChange)
                }
                state.message?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(BatchField.entries.size, key = { index -> BatchField.entries[index].name }) { index ->
                    val field = BatchField.entries[index]
                    OutlinedTextField(
                        value = state.values[field].orEmpty(),
                        onValueChange = { onValueChange(field, it) },
                        label = { Text(field.label) },
                        singleLine = field != BatchField.LYRICS,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                Button(
                    onClick = onStart,
                    enabled = state.values.isNotEmpty() && state.fileCount > 0,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text("Write tags")
                }
            }
        }
    }
}
