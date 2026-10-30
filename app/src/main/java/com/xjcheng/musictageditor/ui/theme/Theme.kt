/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme as materialDarkColorScheme
import androidx.compose.material3.lightColorScheme as materialLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.xjcheng.musictageditor.settings.SettingsRepository
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

private val Seed = Color(0xFF3F6FB5)

private val MaterialLightColors = materialLightColorScheme(
    primary = Seed,
    secondary = Color(0xFF59616F),
    tertiary = Color(0xFF77536B),
)

private val MaterialDarkColors = materialDarkColorScheme(
    primary = Color(0xFFA8C7FA),
    secondary = Color(0xFFC1C7D6),
    tertiary = Color(0xFFE6B9D2),
)

/**
 * App theme.
 *
 * The `dark_theme` preference is honoured here, so the switch in the settings screen has a
 * visible effect; the system setting still wins when it is dark. Miuix is the primary
 * design language and Material 3 stays nested for the placeholder shell.
 */
@Composable
fun MusicTagTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val prefersDark = remember(context) {
        SettingsRepository(context.applicationContext).load().darkTheme
    }
    val dark = darkTheme ?: (prefersDark || isSystemInDarkTheme())
    val miuixColors = if (dark) miuixDarkColorScheme() else miuixLightColorScheme()
    val materialColors = if (dark) MaterialDarkColors else MaterialLightColors
    MiuixTheme(colors = miuixColors) {
        MaterialTheme(colorScheme = materialColors, content = content)
    }
}
