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
 * Miuix is the primary design language: every rewritten screen reads
 * `MiuixTheme.colorScheme`. The Material 3 theme stays nested inside it until the
 * remaining screens migrate, so both component sets see a coherent palette during
 * the transition.
 */
@Composable
fun MusicTagTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val miuixColors = if (darkTheme) miuixDarkColorScheme() else miuixLightColorScheme()
    val materialColors = if (darkTheme) MaterialDarkColors else MaterialLightColors
    MiuixTheme(colors = miuixColors) {
        MaterialTheme(colorScheme = materialColors, content = content)
    }
}
