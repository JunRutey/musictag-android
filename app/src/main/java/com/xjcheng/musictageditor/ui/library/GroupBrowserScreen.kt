/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xjcheng.musictageditor.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** One artist or album row. */
data class GroupRow(
    val title: String,
    val summary: String,
    /** The query the song list is filtered by when the row is opened. */
    val filter: String,
)

/** What the artist and album browsers render. */
data class GroupBrowserState(
    val rows: List<GroupRow> = emptyList(),
    val loading: Boolean = true,
    val permissionGranted: Boolean = false,
)

/**
 * The artist and album browsers.
 *
 * Both are the same list: a title, then one row per group with its track count. Opening a
 * row hands the name to the library as a filter, which is why this screen stays a thin
 * projection of [MediaGrouping] instead of carrying its own song list.
 */
@Composable
fun GroupBrowserScreen(
    title: String,
    state: GroupBrowserState,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpen: (GroupRow) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Useful.Back, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!state.permissionGranted) {
                Text(
                    text = stringResource(R.string.library_permission_reason),
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                BasicComponent(
                    title = stringResource(R.string.action_grant),
                    onClick = onRequestPermission,
                )
                return@Column
            }
            when {
                state.loading -> Text(
                    text = stringResource(R.string.library_loading),
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(24.dp),
                )
                state.rows.isEmpty() -> Text(
                    text = stringResource(R.string.library_empty),
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(24.dp),
                )
                else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(state.rows, key = { it.title + "\u0000" + it.filter }) { row ->
                        BasicComponent(
                            title = row.title,
                            summary = row.summary,
                            onClick = { onOpen(row) },
                        )
                    }
                }
            }
        }
    }
}
