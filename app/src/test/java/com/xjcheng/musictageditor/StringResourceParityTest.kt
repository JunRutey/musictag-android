/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the Chinese-first copy rules without needing an Android device.
 *
 * Two invariants:
 * 1. `values/strings.xml` (the default locale, Simplified Chinese) and
 *    `values-en/strings.xml` must declare exactly the same keys, so a missing
 *    translation can never fall back to an English string on a Chinese device
 *    and vice versa.
 * 2. Default-locale values must actually be Chinese. Keys that are legitimately
 *    ASCII-only have to be listed in [ASCII_ALLOWED] with a reason.
 */
class StringResourceParityTest {

    private val moduleDir: File = sequenceOf(
        File("src/main/res"),
        File("app/src/main/res"),
    ).firstOrNull { it.isDirectory }
        ?: error("cannot locate res/ from ${File(".").absolutePath}")

    @Test
    fun defaultLocaleAndEnglishDeclareTheSameKeys() {
        val zh = readStrings(File(moduleDir, "values/strings.xml"))
        val en = readStrings(File(moduleDir, "values-en/strings.xml"))

        assertEquals("missing in values-en: ${zh.keys - en.keys}", emptySet<String>(), zh.keys - en.keys)
        assertEquals("missing in values: ${en.keys - zh.keys}", emptySet<String>(), en.keys - zh.keys)
    }

    @Test
    fun everyDefaultLocaleValueIsNonBlank() {
        val zh = readStrings(File(moduleDir, "values/strings.xml"))
        val blank = zh.filterValues { it.isBlank() }.keys
        assertEquals("blank Chinese strings: $blank", emptySet<String>(), blank)
    }

    @Test
    fun defaultLocaleValuesAreChinese() {
        val zh = readStrings(File(moduleDir, "values/strings.xml"))
        val notChinese = zh.filterKeys { it !in ASCII_ALLOWED }.filterValues { !it.hasCjk() }.keys
        assertEquals(
            "default-locale strings must be Chinese (or listed in ASCII_ALLOWED): $notChinese",
            emptySet<String>(),
            notChinese,
        )
    }

    @Test
    fun placeholdersMatchBetweenLocales() {
        val zh = readStrings(File(moduleDir, "values/strings.xml"))
        val en = readStrings(File(moduleDir, "values-en/strings.xml"))
        for ((key, zhValue) in zh) {
            val enValue = en[key] ?: continue
            assertEquals("format arguments differ for $key", zhValue.placeholders(), enValue.placeholders())
        }
    }

    private fun String.placeholders(): List<String> =
        Regex("%[0-9]+\\\$[a-z]").findAll(this).map { it.value }.sorted().toList()

    private fun String.hasCjk(): Boolean = any { it.code in 0x3400..0x4DBF || it.code in 0x4E00..0x9FFF }

    /** Parses `<string name="key">value</string>`; escapes are irrelevant for these checks. */
    private fun readStrings(file: File): Map<String, String> {
        assertTrue("missing resource file ${file.path}", file.isFile)
        val text = file.readText()
        val entries = LinkedHashMap<String, String>()
        for (match in STRING_ENTRY.findAll(text)) {
            entries[match.groupValues[1]] = match.groupValues[2].trim()
        }
        assertTrue("no <string> entries parsed from ${file.path}", entries.isNotEmpty())
        return entries
    }

    private companion object {
        val STRING_ENTRY = Regex(
            """<string\s+name="([^"]+)"\s*>(.*?)</string>""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )

        /** Keys allowed to stay ASCII in the default (Chinese) locale, with the reason. */
        val ASCII_ALLOWED: Set<String> = emptySet()
    }
}
