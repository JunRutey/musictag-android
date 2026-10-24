/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.lyrics

/** One timed lyric line. */
data class LrcLine(val timeMs: Long, val text: String)

/**
 * LRC handling.
 *
 * The original app exposed these as menu actions over a selection of files ("reformat
 * timestamps", "remove timestamps", "adjust offset", "delete blank lines", "delete head
 * tags", "merge translation"). Keeping them as pure functions on text makes them testable
 * and lets the editor and the batch service share exactly one implementation.
 *
 * Timestamps follow the LRC convention `[mm:ss.xx]`; one line may carry several timestamps
 * (repeated chorus lines) and metadata lines look like `[ti:...]`.
 */
object Lrc {

    private val TIMESTAMP = Regex("""\[(\d{1,3}):([0-5]\d)(?:[.:](\d{1,3}))?]""")
    private val METADATA = Regex("""^\[(ti|ar|al|by|offset|length|re|ve|tool|encoding):.*]$""", RegexOption.IGNORE_CASE)
    private val OFFSET = Regex("""\[offset:\s*([-+]?\d+)]""", RegexOption.IGNORE_CASE)

    /** Parses every timestamped line, in file order; malformed lines are skipped. */
    fun parse(text: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd('\r')
            if (line.isBlank()) continue
            val matches = TIMESTAMP.findAll(line).toList()
            if (matches.isEmpty()) continue
            val content = line.substring(matches.last().range.last + 1)
            for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: continue
                val seconds = match.groupValues[2].toLongOrNull() ?: continue
                lines += LrcLine(minutes * 60_000 + seconds * 1_000 + fractionMs(match.groupValues[3]), content)
            }
        }
        return lines
    }

    /** Renders lines back to LRC, sorted, dropping empty text. */
    fun format(lines: List<LrcLine>, decimals: Int = 2): String = buildString {
        for (line in lines.sortedBy { it.timeMs }) {
            if (line.text.isBlank()) continue
            append(timestamp(line.timeMs, decimals)).append(line.text).append('\n')
        }
    }

    /** `[mm:ss.xx]` for a millisecond position. */
    fun timestamp(timeMs: Long, decimals: Int = 2): String {
        val safe = if (timeMs < 0) 0 else timeMs
        val minutes = safe / 60_000
        val seconds = (safe % 60_000) / 1_000
        val digits = decimals.coerceIn(1, 3)
        val scaled = scale(safe % 1_000, digits)
        return "[%02d:%02d.%0${digits}d]".format(minutes, seconds, scaled)
    }

    /** Rewrites every timestamp to [decimals] digits without touching the lyric text. */
    fun reformat(text: String, decimals: Int = 2): String {
        if (text.isBlank()) return ""
        val digits = decimals.coerceIn(1, 3)
        val body = text.lineSequence().joinToString("\n") { raw ->
            TIMESTAMP.replace(raw) { match ->
                val millis = fractionMs(match.groupValues[3])
                "[${match.groupValues[1]}:${match.groupValues[2]}.%0${digits}d]".format(scale(millis, digits))
            }
        }
        return body.trimEnd('\n') + "\n"
    }

    /** Shifts every timestamp by [deltaMs]; negative results clamp to zero. */
    fun offset(text: String, deltaMs: Long): String {
        if (text.isBlank()) return ""
        val body = text.lineSequence().joinToString("\n") { raw ->
            TIMESTAMP.replace(raw) { match ->
                val base = match.groupValues[1].toLong() * 60_000 +
                    match.groupValues[2].toLong() * 1_000 +
                    fractionMs(match.groupValues[3])
                val shifted = (base + deltaMs).coerceAtLeast(0)
                val digits = match.groupValues[3].length.coerceIn(1, 3)
                val minutes = shifted / 60_000
                val seconds = (shifted % 60_000) / 1_000
                "[%02d:%02d.%0${digits}d]".format(minutes, seconds, scale(shifted % 1_000, digits))
            }
        }
        return body.trimEnd('\n') + "\n"
    }

    /** Removes timestamps, keeping the text lines (used to store plain lyrics). */
    fun stripTimestamps(text: String): String =
        joinLines(text.lineSequence().map { TIMESTAMP.replace(it, "").trim() }.filter { it.isNotEmpty() })

    /** Removes blank lines while keeping everything else as-is. */
    fun dropBlankLines(text: String): String =
        joinLines(text.lineSequence().filter { it.isNotBlank() })

    /** Removes `[ti:…]`-style metadata lines. */
    fun dropHeadTags(text: String): String =
        joinLines(text.lineSequence().filterNot { METADATA.matches(it.trim()) })

    /** Reads the `[offset:…]` metadata value, if present. */
    fun declaredOffset(text: String): Long? =
        OFFSET.find(text)?.groupValues?.get(1)?.toLongOrNull()

    /** Reads a `[ti:…]`-style value, if present. */
    fun metadata(text: String, tag: String): String? = text.lineSequence()
        .map { it.trim() }
        .firstNotNullOfOrNull { line ->
            val prefix = "[$tag:"
            if (line.startsWith(prefix, ignoreCase = true) && line.endsWith("]")) {
                line.substring(prefix.length, line.length - 1).trim()
            } else {
                null
            }
        }

    /**
     * Merges a translation track into the original by timestamp, which is how the NetEase
     * and QQ translation tracks are combined. Lines without a translation keep the
     * original text; translation-only lines are added at their own timestamp.
     */
    fun mergeTranslation(original: String, translation: String, separator: String = " // "): String {
        if (translation.isBlank()) return original
        val translated = parse(translation).associate { it.timeMs to it.text }
        val merged = parse(original).map { line ->
            val extra = translated[line.timeMs]
            if (extra.isNullOrBlank()) line
            else LrcLine(line.timeMs, line.text.trim() + separator + extra.trim())
        }
        val known = merged.map { it.timeMs }.toSet()
        val leftovers = parse(translation).filterNot { it.timeMs in known }
        return format(merged + leftovers)
    }

    /** Joins lines with a trailing newline, and keeps the empty case truly empty. */
    private fun joinLines(lines: Sequence<String>): String {
        val kept = lines.toList()
        return if (kept.isEmpty()) "" else kept.joinToString("\n", postfix = "\n")
    }

    /** Two or three digits after the separator mean different fractions; normalise to ms. */
    private fun fractionMs(digits: String): Long = when (digits.length) {
        0 -> 0
        1 -> digits.toLong() * 100
        2 -> digits.toLong() * 10
        else -> digits.take(3).toLong()
    }

    private fun scale(millis: Long, digits: Int): Long = when (digits) {
        1 -> millis / 100
        2 -> millis / 10
        else -> millis
    }
}
