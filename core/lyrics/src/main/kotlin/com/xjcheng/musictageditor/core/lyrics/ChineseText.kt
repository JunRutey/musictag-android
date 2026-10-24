/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.lyrics

import com.github.houbb.opencc4j.util.ZhConverterUtil

/**
 * Traditional/simplified Chinese conversion.
 *
 * Delegated to OpenCC4J, which ships the OpenCC dictionaries, instead of maintaining a
 * character table here. The original app offered the same conversion as a tag/lyric action.
 */
object ChineseText {

    fun toSimplified(text: String): String {
        if (text.isEmpty()) return text
        return ZhConverterUtil.toSimple(text)
    }

    fun toTraditional(text: String): String {
        if (text.isEmpty()) return text
        return ZhConverterUtil.toTraditional(text)
    }

    /** True when conversion would change the text, so callers can skip the work. */
    fun hasTraditional(text: String): Boolean = toSimplified(text) != text
}
