/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xjcheng.musictageditor.R
import com.xjcheng.musictageditor.settings.SettingGroup
import com.xjcheng.musictageditor.settings.SettingSpec
import com.xjcheng.musictageditor.settings.SettingType
import com.xjcheng.musictageditor.settings.Settings
import com.xjcheng.musictageditor.settings.SettingsSchema
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Settings, generated from [SettingsSchema] so the screen cannot drift from the stored keys.
 *
 * Rows come in three shapes: a switch, a choice that opens a selector sheet, or a free text
 * field. The ignore-directory row hands off to the system folder picker instead of asking the
 * user to type a path. Edits go to the in-memory copy and the activity persists the whole file
 * when it leaves the foreground.
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    versionName: String,
    repositoryUrl: String,
    logSummary: String,
    exportSummary: String,
    onOpenRepository: () -> Unit,
    onExportLog: () -> Unit,
    onExportToDownloads: () -> Unit,
    onClearLog: () -> Unit,
    onPickIgnoreDirectory: () -> Unit,
    onClearIgnoreDirectories: () -> Unit,
    onUpdate: (String, Any?) -> Unit,
    onBack: () -> Unit,
) {
    var chooser by remember { mutableStateOf<SettingSpec?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = stringResource(R.string.title_activity_settings),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.Useful.Back, stringResource(R.string.action_back))
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(modifier = Modifier.fillMaxWidth().padding(padding)) {
                for (group in SettingGroup.entries) {
                    val specs = SettingsSchema.specs.filter { it.group == group }
                    if (specs.isEmpty()) continue
                    item(key = "header-${group.name}") { GroupTitle(group.title) }
                    items(specs, key = { it.key }) { spec ->
                        if (spec.key == IGNORE_DIRECTORY_KEY) {
                            IgnoreDirectoryRows(
                                settings = settings,
                                spec = spec,
                                onPick = onPickIgnoreDirectory,
                                onClear = onClearIgnoreDirectories,
                            )
                        } else {
                            SettingRow(
                                spec = spec,
                                settings = settings,
                                onUpdate = onUpdate,
                                onChoose = { chooser = spec },
                            )
                        }
                    }
                }
                item(key = "header-about") { GroupTitle(stringResource(R.string.settings_about)) }
                item(key = "about-repository") {
                    BasicComponent(
                        title = stringResource(R.string.settings_repository),
                        summary = repositoryUrl,
                        onClick = onOpenRepository,
                    )
                }
                item(key = "about-log") {
                    BasicComponent(
                        title = stringResource(R.string.settings_export_log),
                        summary = logSummary,
                        onClick = onExportLog,
                    )
                }
                item(key = "about-export-download") {
                    BasicComponent(
                        title = stringResource(R.string.settings_export_download),
                        summary = exportSummary,
                        onClick = onExportToDownloads,
                    )
                }
                item(key = "about-clear-log") {
                    BasicComponent(
                        title = stringResource(R.string.settings_clear_log),
                        onClick = onClearLog,
                    )
                }
                item(key = "about-version") {
                    BasicComponent(
                        title = stringResource(R.string.settings_version),
                        summary = versionName,
                    )
                }
                item(key = "footer") {
                    Text(
                        text = stringResource(R.string.settings_footer, SettingsSchema.specs.size),
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                }
            }
        }

        chooser?.let { spec ->
            ChoiceSheet(
                spec = spec,
                current = settings.string(spec.key),
                onPick = { value ->
                    chooser = null
                    onUpdate(spec.key, value)
                },
                onDismiss = { chooser = null },
            )
        }
    }
}

private const val IGNORE_DIRECTORY_KEY = "dirpath_of_ignore_medias"

@Composable
private fun GroupTitle(title: String) {
    Text(
        text = title,
        color = MiuixTheme.colorScheme.primary,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(
    spec: SettingSpec,
    settings: Settings,
    onUpdate: (String, Any?) -> Unit,
    onChoose: () -> Unit,
) {
    when (spec.type) {
        SettingType.BOOLEAN -> BasicComponent(
            title = spec.title,
            summary = spec.summary.ifBlank { null },
            rightActions = {
                Switch(
                    checked = settings.boolean(spec.key),
                    onCheckedChange = { onUpdate(spec.key, it) },
                )
            },
        )

        SettingType.CHOICE -> BasicComponent(
            title = spec.title,
            summary = listOf(spec.labelOf(settings.string(spec.key)), spec.summary)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            onClick = onChoose,
        )

        SettingType.INT -> BasicComponent(
            title = spec.title,
            summary = spec.summary.ifBlank { null },
            bottomAction = {
                InlineTextField(
                    value = settings.int(spec.key).toString(),
                    onValueChange = { text -> text.toIntOrNull()?.let { onUpdate(spec.key, it) } },
                    keyboardType = KeyboardType.Number,
                )
            },
        )

        SettingType.STRING -> BasicComponent(
            title = spec.title,
            summary = spec.summary.ifBlank { null },
            bottomAction = {
                InlineTextField(
                    value = settings.string(spec.key),
                    onValueChange = { onUpdate(spec.key, it) },
                    keyboardType = KeyboardType.Text,
                    monospace = true,
                )
            },
        )
    }
}

/** The ignore-directory preference is a picker, not a text field. */
@Composable
private fun IgnoreDirectoryRows(
    settings: Settings,
    spec: SettingSpec,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val directories = settings.string(spec.key)
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    BasicComponent(
        title = spec.title,
        summary = if (directories.isEmpty()) {
            stringResource(R.string.settings_ignore_dirs_empty)
        } else {
            directories.joinToString("\n")
        },
        onClick = onPick,
    )
    BasicComponent(
        title = stringResource(R.string.settings_ignore_dirs_add),
        summary = stringResource(R.string.settings_ignore_dirs_add_hint),
        onClick = onPick,
    )
    if (directories.isNotEmpty()) {
        BasicComponent(
            title = stringResource(R.string.settings_ignore_dirs_clear),
            onClick = onClear,
        )
    }
}

/** Bottom sheet listing the options of one [SettingType.CHOICE] preference. */
@Composable
private fun ChoiceSheet(
    spec: SettingSpec,
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = spec.title,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
                for (option in spec.options) {
                    val selected = option.value == current
                    BasicComponent(
                        title = option.label,
                        summary = if (selected) stringResource(R.string.settings_choice_current) else null,
                        onClick = { onPick(option.value) },
                    )
                }
                BasicComponent(
                    title = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun InlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    monospace: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            ),
            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
