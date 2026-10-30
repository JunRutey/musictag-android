/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.ui.batch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xjcheng.musictageditor.R
import com.xjcheng.musictageditor.batch.BatchField
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** What the batch editor renders. */
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
 * [com.xjcheng.musictageditor.batch.BatchEdit] itself supports an explicit clear (a present
 * but empty value), which the future clear action will use.
 */
@Composable
fun BatchEditScreen(
    state: BatchUiState,
    onValueChange: (BatchField, String) -> Unit,
    onFillMissingChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.batch_title),
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(MiuixIcons.Useful.Back, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll),
        ) {
            Text(
                text = stringResource(R.string.batch_summary, state.fileCount, state.values.size),
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            state.message?.let { message ->
                Text(
                    text = message,
                    color = MiuixTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            BasicComponent(
                title = stringResource(R.string.batch_fill_missing),
                summary = stringResource(R.string.batch_fill_missing_summary),
                rightActions = {
                    Switch(checked = state.fillMissingOnly, onCheckedChange = onFillMissingChange)
                },
            )
            for (field in BatchField.entries) {
                BasicComponent(
                    title = field.label,
                    bottomAction = {
                        BasicTextField(
                            value = state.values[field].orEmpty(),
                            onValueChange = { onValueChange(field, it) },
                            singleLine = field != BatchField.LYRICS,
                            maxLines = if (field == BatchField.LYRICS) 6 else 1,
                            textStyle = TextStyle(
                                color = MiuixTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                            ),
                            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    },
                )
            }
            BasicComponent(
                title = stringResource(R.string.action_write_tags),
                summary = stringResource(R.string.batch_apply_hint),
                enabled = state.values.isNotEmpty() && state.fileCount > 0,
                onClick = onStart,
            )
        }
    }
}
