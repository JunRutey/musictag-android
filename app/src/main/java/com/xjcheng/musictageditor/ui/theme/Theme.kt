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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.xjcheng.musictageditor.settings.Appearance
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

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
 * The `dark_theme` preference is read through [Appearance], a snapshot state, so the switch in
 * the settings screen repaints every open screen immediately instead of being cached in a
 * `remember`. The system setting still wins when it is dark.
 */
@Composable
fun MusicTagTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(context) { Appearance.refresh(context) }

    val controller = remember { ThemeController(Appearance.mode) }
    LaunchedEffect(Appearance.mode) { controller.colorSchemeMode = Appearance.mode }
    val dark = darkTheme ?: (Appearance.mode == ColorSchemeMode.Dark ||
        Appearance.mode == ColorSchemeMode.MonetDark ||
        (Appearance.mode == ColorSchemeMode.System && isSystemInDarkTheme()) ||
        (Appearance.mode == ColorSchemeMode.MonetSystem && isSystemInDarkTheme()))
    val materialColors = if (dark) MaterialDarkColors else MaterialLightColors
    MiuixTheme(controller = controller) {
        MaterialTheme(colorScheme = materialColors, content = content)
    }
}
