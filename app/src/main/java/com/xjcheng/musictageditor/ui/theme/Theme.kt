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
import androidx.compose.ui.graphics.Color
import com.xjcheng.musictageditor.settings.Appearance
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme
import top.yukonga.miuix.kmp.theme.platformDynamicColors

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
 * The colour scheme is chosen straight from [Appearance], which is snapshot state: toggling
 * the theme in the settings screen recomposes this function and every screen under it
 * immediately. Monet variants call Miuix's own [platformDynamicColors], which reads the
 * wallpaper on Android 12+ and falls back to the normal scheme before that.
 */
@Composable
fun MusicTagTheme(
    content: @Composable () -> Unit,
) {
    val mode = Appearance.mode
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        ColorSchemeMode.Light, ColorSchemeMode.MonetLight -> false
        ColorSchemeMode.Dark, ColorSchemeMode.MonetDark -> true
        ColorSchemeMode.System, ColorSchemeMode.MonetSystem -> systemDark
    }
    val miuixColors = when (mode) {
        ColorSchemeMode.System -> if (dark) miuixDarkColorScheme() else miuixLightColorScheme()
        ColorSchemeMode.Light -> miuixLightColorScheme()
        ColorSchemeMode.Dark -> miuixDarkColorScheme()
        ColorSchemeMode.MonetSystem -> platformDynamicColors(systemDark)
        ColorSchemeMode.MonetLight -> platformDynamicColors(false)
        ColorSchemeMode.MonetDark -> platformDynamicColors(true)
    }
    val materialColors = if (dark) MaterialDarkColors else MaterialLightColors
    MiuixTheme(colors = miuixColors) {
        MaterialTheme(colorScheme = materialColors, content = content)
    }
}
