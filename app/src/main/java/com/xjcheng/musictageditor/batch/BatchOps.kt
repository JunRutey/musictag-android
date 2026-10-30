/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.batch

/** The eleven editable text fields, in the order the editor and the native layer use. */
enum class BatchField(val label: String, val placeholder: Char) {
    TITLE("标题", 't'),
    ARTIST("艺术家", 'a'),
    ALBUM("专辑", 'l'),
    ALBUM_ARTIST("专辑艺术家", 'A'),
    GENRE("流派", 'g'),
    COMPOSER("作曲", 'c'),
    TRACK("音轨", 'n'),
    DISC("碟号", 'd'),
    YEAR("年份", 'y'),
    COMMENT("注释", 'm'),
    LYRICS("歌词", 'L'),
}

enum class BatchMode {
    /** Replace every field the job supplies, keeping the rest. */
    OVERWRITE,

    /** Only fill fields that are currently blank. */
    FILL_MISSING,
}

/**
 * What a batch run should write.
 *
 * [values] holds one entry per field the user actually filled; a present but empty value
 * means "clear this field", while an absent entry means "leave it alone". That distinction
 * is what makes overwrite-mode batches safe: they cannot wipe a field the dialog never
 * touched.
 */
data class BatchEdit(
    val values: Map<BatchField, String> = emptyMap(),
    val mode: BatchMode = BatchMode.OVERWRITE,
) {
    fun isEmpty(): Boolean = values.isEmpty()
}

/** A file the batch run works on. */
data class BatchTarget(val uri: String, val displayName: String, val existing: Map<BatchField, String> = emptyMap())

data class BatchOutcome(
    val target: BatchTarget,
    val values: Map<BatchField, String>,
    val newName: String? = null,
)

/**
 * The pure part of batch editing: what each file's fields become, and what it should be
 * renamed to. The service does the I/O around these decisions.
 */
object BatchOps {

    /** Applies [edit] to one file's current values. */
    fun apply(existing: Map<BatchField, String>, edit: BatchEdit): Map<BatchField, String> {
        val result = existing.toMutableMap()
        for ((field, value) in edit.values) {
            val current = existing[field].orEmpty()
            val shouldWrite = when (edit.mode) {
                BatchMode.OVERWRITE -> true
                BatchMode.FILL_MISSING -> current.isBlank()
            }
            if (shouldWrite) {
                if (value.isEmpty()) result.remove(field) else result[field] = value
            }
        }
        return result
    }

    /** True when applying [edit] would change anything for this file. */
    fun wouldChange(existing: Map<BatchField, String>, edit: BatchEdit): Boolean =
        apply(existing, edit) != existing

    /**
     * Expands a rename template.
     *
     * Placeholders: `%t` title, `%a` artist, `%l` album, `%A` album artist, `%n` track,
     * `%y` year, `%%` a literal percent. Empty placeholders collapse, and the result is
     * sanitised so it is a legal file name on every supported file system.
     */
    fun rename(template: String, values: Map<BatchField, String>, fallback: String): String {
        val expanded = buildString {
            var index = 0
            while (index < template.length) {
                val char = template[index]
                if (char == '%' && index + 1 < template.length) {
                    val code = template[index + 1]
                    when (code) {
                        '%' -> append('%')
                        else -> {
                            val field = BatchField.entries.firstOrNull { it.placeholder == code }
                            if (field != null) append(values[field].orEmpty())
                            else append('%').append(code)
                        }
                    }
                    index += 2
                } else {
                    append(char)
                    index += 1
                }
            }
        }
        val cleaned = sanitize(expanded)
        return cleaned.ifBlank { sanitize(fallback).ifBlank { "untitled" } }
    }

    /** Keeps the base name legal and bounded; the caller keeps the original extension. */
    fun sanitize(name: String): String = name
        // Reserved path characters become underscores; control characters are dropped
        // outright, because "__" from junk bytes is not a usable file name either.
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .replace(Regex("""[\u0000-\u001f]"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .trim('.')
        .take(120)

    /**
     * Applies the lyric post-processing the settings describe, in the original order:
     * optional translation merge first, then timestamp/blank/head-tag handling.
     */
    fun processLyrics(
        lyrics: String,
        mergeTranslation: ((String) -> String)? = null,
        reformatTimestamps: Boolean = false,
        removeTimestamps: Boolean = false,
        dropBlankLines: Boolean = false,
        dropHeadTags: Boolean = false,
        toSimplified: Boolean = false,
        offsetMs: Long = 0,
    ): String {
        var text = lyrics
        if (mergeTranslation != null) text = mergeTranslation(text)
        if (reformatTimestamps) text = com.xjcheng.musictageditor.core.lyrics.Lrc.reformat(text)
        if (removeTimestamps) text = com.xjcheng.musictageditor.core.lyrics.Lrc.stripTimestamps(text)
        if (dropBlankLines) text = com.xjcheng.musictageditor.core.lyrics.Lrc.dropBlankLines(text)
        if (dropHeadTags) text = com.xjcheng.musictageditor.core.lyrics.Lrc.dropHeadTags(text)
        if (toSimplified) text = com.xjcheng.musictageditor.core.lyrics.ChineseText.toSimplified(text)
        if (offsetMs != 0L) text = com.xjcheng.musictageditor.core.lyrics.Lrc.offset(text, offsetMs)
        return text
    }
}
