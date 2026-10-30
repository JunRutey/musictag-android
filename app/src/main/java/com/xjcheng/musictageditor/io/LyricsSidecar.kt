/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.io

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.xjcheng.musictageditor.settings.Settings
import java.io.File
import java.nio.charset.Charset

/**
 * The `.lrc` side file next to an audio file.
 *
 * Only real filesystem paths are supported, because a single SAF document URI does not grant
 * write access to its directory. Callers resolve the path with [pathFor] first; when the
 * document is not MediaStore-backed the sidecar is reported as skipped instead of failing
 * the tag write.
 */
object LyricsSidecar {

    sealed interface Result {
        data object Written : Result
        data class Skipped(val reason: String) : Result
    }

    /** File name for the `.lrc`, honouring the three naming modes from the settings. */
    fun fileNameFor(
        audioName: String,
        mode: String,
        template: String,
        title: String,
        artist: String,
        album: String,
    ): String {
        val base = audioName.substringBeforeLast('.', audioName)
        val rendered = when (mode.lowercase()) {
            "same" -> base
            "artist-title" -> listOf(artist, title).filter { it.isNotBlank() }.joinToString(" - ")
            "custom" -> expandTemplate(template, title, artist, album)
            else -> base
        }
        val safe = sanitise(rendered).ifBlank { base }
        return "$safe.lrc"
    }

    /** Resolves `<audio>.lrc` (or the configured name) next to [audioPath]. */
    fun fileFor(
        audioPath: String,
        settings: Settings,
        title: String,
        artist: String,
        album: String,
    ): File {
        val audio = File(audioPath)
        val name = fileNameFor(audio.name, settings.lrcFilenameMode, settings.lrcFilenameTemplate, title, artist, album)
        return File(audio.parentFile ?: File("."), name)
    }

    fun write(audioPath: String, lyrics: String, settings: Settings, title: String, artist: String, album: String): Result {
        if (lyrics.isBlank()) return Result.Skipped("no lyrics")
        val target = fileFor(audioPath, settings, title, artist, album)
        return runCatching {
            target.writeText(lyrics, charsetFor(settings.lrcEncoding))
            Result.Written
        }.getOrElse { Result.Skipped(it.message ?: "write failed") }
    }

    fun read(audioPath: String, settings: Settings, title: String, artist: String, album: String): String? {
        val target = fileFor(audioPath, settings, title, artist, album)
        if (!target.isFile) return null
        return runCatching { target.readText(charsetFor(settings.lrcEncoding)) }.getOrNull()
    }

    fun delete(audioPath: String, settings: Settings, title: String, artist: String, album: String): Boolean {
        val target = fileFor(audioPath, settings, title, artist, album)
        return runCatching { !target.exists() || target.delete() }.getOrDefault(false)
    }

    /**
     * Real file path behind a MediaStore audio row, or null.
     *
     * `DATA` is deprecated from Android 10 on, which is why the platform falling back to an
     * opaque document is reported as "no path" rather than guessed.
     */
    fun pathFor(context: Context, uri: Uri): String? {
        val authority = uri.authority.orEmpty()
        if (!authority.startsWith("media")) return null
        return runCatching {
            context.contentResolver
                .query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
                }
        }.getOrNull()
    }

    private fun expandTemplate(template: String, title: String, artist: String, album: String): String {
        if (template.isBlank()) return title
        return template
            .replace("%t", title)
            .replace("%a", artist)
            .replace("%l", album)
            .replace("%A", artist)
    }

    private fun sanitise(value: String): String =
        value.map { char ->
            when {
                char == '/' || char == '\\' || char == ':' || char == '*' || char == '?' ||
                    char == '"' || char == '<' || char == '>' || char == '|' -> '_'
                char.code < 0x20 -> '_'
                else -> char
            }
        }.joinToString("").trim().trim('.').take(120)

    private fun charsetFor(name: String): Charset =
        runCatching { Charset.forName(name.ifBlank { "UTF-8" }) }.getOrDefault(Charsets.UTF_8)
}
