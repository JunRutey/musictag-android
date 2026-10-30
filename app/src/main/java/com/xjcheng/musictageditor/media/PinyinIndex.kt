/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.media

import net.sourceforge.pinyin4j.PinyinHelper

/**
 * Pinyin bucketing for the library index bar.
 *
 * The reference design groups Chinese titles under the initial of their pinyin, Latin titles
 * under their own first letter, and everything else (digits, punctuation, symbols) under `#`.
 * Only the first reading of a polyphone is used, which is why 重庆 lands under `Z` — the
 * spelling is deterministic and the same in every list, which matters more here than
 * linguistic perfection.
 */
object PinyinIndex {

    const val OTHER = "#"

    private val LETTERS = ('A'..'Z').map(Char::toString)

    /** The sections the index bar shows, in order. */
    val sections: List<String> = LETTERS + OTHER

    /** Bucket of one title: `A`–`Z`, or `#` for everything that is not a letter. */
    fun initialOf(text: String): String {
        val first = text.trimStart().firstOrNull() ?: return OTHER
        return initialOf(first)
    }

    fun initialOf(char: Char): String {
        pinyinOf(char)?.let { pinyin ->
            val initial = pinyin.firstOrNull()?.uppercaseChar()
            if (initial != null && initial in 'A'..'Z') return initial.toString()
        }
        if (char in 'a'..'z' || char in 'A'..'Z') return char.uppercaseChar().toString()
        return OTHER
    }

    /**
     * Stable, comparable sort key: pinyin for Chinese, the lowercase character otherwise.
     *
     * Tone digits are dropped so `qing1` and `qing` sort together; the result is only used
     * for ordering and display grouping, never written to a file.
     */
    fun sortKeyOf(text: String): String = buildString(text.length * 2) {
        for (char in text) {
            val pinyin = pinyinOf(char)
            if (pinyin != null) {
                append(pinyin.filter { it !in '0'..'9' })
            } else {
                append(char.lowercaseChar())
            }
        }
    }

    /** Music-store style ordering: numeric tracks first, then `#`. */
    fun compare(a: String, b: String): Int {
        val rankA = rank(initialOf(a))
        val rankB = rank(initialOf(b))
        if (rankA != rankB) return rankA.compareTo(rankB)
        val keyOrder = sortKeyOf(a).compareTo(sortKeyOf(b))
        if (keyOrder != 0) return keyOrder
        return a.compareTo(b, ignoreCase = true)
    }

    private fun rank(initial: String): Int = if (initial == OTHER) sections.size else initial[0] - 'A'

    private fun pinyinOf(char: Char): String? {
        if (char.code < 0x3400) return null
        return runCatching { PinyinHelper.toHanyuPinyinStringArray(char)?.firstOrNull() }.getOrNull()
    }
}
