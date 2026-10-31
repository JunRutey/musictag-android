/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor

import com.xjcheng.musictageditor.settings.SettingType
import com.xjcheng.musictageditor.settings.Settings
import com.xjcheng.musictageditor.settings.SettingsSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The preference schema is the single source of truth for the settings screen, so it is
 * checked here: every key is usable, defaults round-trip, and the retired keys stay retired.
 */
class SettingsSchemaTest {

    @Test
    fun everySpecIsUsable() {
        assertTrue("expected a substantial preference set", SettingsSchema.specs.size >= 20)
        for (spec in SettingsSchema.specs) {
            assertTrue("key must not be blank", spec.key.isNotBlank())
            assertTrue("title must not be blank for ${spec.key}", spec.title.isNotBlank())
            assertNotNull(SettingsSchema.spec(spec.key))
            if (spec.type == SettingType.CHOICE) {
                assertTrue("choice ${spec.key} needs options", spec.options.size >= 2)
                assertTrue(
                    "choice ${spec.key} must include its default",
                    spec.options.any { it.value == spec.default },
                )
            }
        }
    }

    @Test
    fun keysAreUnique() {
        val keys = SettingsSchema.specs.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun originalKeyNamesArePreserved() {
        // Names from the original prefs_settings.xml, where the semantics still match.
        val expected = listOf(
            "write_id3v1",
            "id3v2_mode",
            "web_search_items_limit_count",
            "download_lyric_translations",
            "download_lyric_translations_t2s_mode",
            "connectors_lyric_and_tlyric",
            "connectors_artists",
            "reformat_lyric_timetag",
            "remove_lyric_timetag",
            "delete_lyric_blank_text_lines",
            "delete_lyric_head_tags",
            "lrcfiles_encoding",
            "lrcfiles_filename_format_mode",
            "allow_save_lrc_file_while_save_file_tags",
            "duration_of_ignore_medias",
            "dirpath_of_ignore_medias",
            "video_of_ignore_medias",
            "theme_mode",
            "dynamic_color",
            "search_recent_suggestions",
        )
        for (key in expected) {
            assertNotNull("missing original key $key", SettingsSchema.spec(key))
        }
    }

    @Test
    fun retiredKeysAreNoLongerDeclared() {
        val retired = listOf(
            // 1.1.1: preferences that changed nothing when toggled.
            "tags_sources_combination",
            "tags_sources_picture",
            "tags_sources_lyric",
            "web_search_criteria",
            "primary_color",
            "accent_color",
            "allow_black_theme",
            "enable_animations",
            "tab_items",
            "always_show_batch_buttons",
            "media_store_rescan",
            "keep_screen_on_while_batch",
            // 1.1.6: Apple Music source removed.
            "apple_region",
            "apple_language",
            "apple_cover_size",
            // 1.1.7: manual batch removed, .lrc directory/template fixed, dark_theme replaced.
            "batch_rename_mode",
            "batch_rename_template",
            "lrcfiles_save_directory_mode",
            "lrcfiles_filename_format",
            "dark_theme",
            // Never supported.
            "lastfm_api_key",
            "vgmdb_api_key",
        )
        for (key in retired) {
            assertNull("retired key still declared: $key", SettingsSchema.spec(key))
        }
    }

    @Test
    fun defaultsMatchTheirDeclaredValues() {
        val defaults = SettingsSchema.defaults
        for (spec in SettingsSchema.specs) {
            when (spec.type) {
                SettingType.BOOLEAN ->
                    assertEquals(spec.key, spec.default as Boolean, defaults.boolean(spec.key))
                SettingType.INT ->
                    assertEquals(spec.key, spec.default as Int, defaults.int(spec.key))
                SettingType.STRING, SettingType.CHOICE ->
                    assertEquals(spec.key, spec.default as String, defaults.string(spec.key))
            }
        }
    }

    @Test
    fun valuesSurviveStringTypedStorage() {
        // SharedPreferences can hand back strings for values written by another version;
        // the accessors coerce instead of throwing or silently resetting.
        val asStrings = SettingsSchema.specs.associate { it.key to it.default.toString() }
        val decoded = Settings(asStrings)
        for (spec in SettingsSchema.specs) {
            when (spec.type) {
                SettingType.BOOLEAN ->
                    assertEquals(spec.key, spec.default as Boolean, decoded.boolean(spec.key))
                SettingType.INT ->
                    assertEquals(spec.key, spec.default as Int, decoded.int(spec.key))
                SettingType.STRING, SettingType.CHOICE ->
                    assertEquals(spec.key, spec.default as String, decoded.string(spec.key))
            }
        }
    }

    @Test
    fun withIgnoresUnknownKeys() {
        val settings = SettingsSchema.defaults
        assertEquals(settings, settings.with("not_a_real_key", "x"))
        val changed = settings.with("write_id3v1", true)
        assertTrue(changed.writeId3v1)
        assertFalse(settings.writeId3v1)
    }

    @Test
    fun writeOptionsPackTheNativeFlags() {
        val base = SettingsSchema.defaults
        assertEquals(0, base.with("write_id3v1", false).with("id3v2_mode", "4").writeOptions())
        assertEquals(1, base.with("write_id3v1", true).with("id3v2_mode", "4").writeOptions())
        assertEquals(2, base.with("write_id3v1", false).with("id3v2_mode", "3").writeOptions())
        assertEquals(3, base.with("write_id3v1", true).with("id3v2_mode", "3").writeOptions())
        assertTrue(base.with("id3v2_mode", "3").id3v23)
        assertFalse(base.with("id3v2_mode", "4").id3v23)
    }

    @Test
    fun typedGettersReadTheirOwnKeys() {
        val settings = SettingsSchema.defaults
            .with("id3v2_mode", "3")
            .with("web_search_items_limit_count", 7)
            .with("connectors_lyric_and_tlyric", " | ")
            .with("duration_of_ignore_medias", "30")
            .with("theme_mode", "dark")
            .with("dynamic_color", true)
        assertTrue(settings.id3v23)
        assertEquals(7, settings.searchLimit)
        assertEquals(" | ", settings.connectorLyricTranslation)
        assertEquals(30, settings.ignoreDurationSeconds)
        assertEquals("dark", settings.themeMode)
        assertTrue(settings.dynamicColor)
    }

    @Test
    fun oldTypesStillReadAfterAChoiceConversion() {
        // 1.1.6 stored this as an Int; 1.1.7 made it a choice. Reading it must not throw.
        val migrated = Settings(mapOf("duration_of_ignore_medias" to 45))
        assertEquals("45", migrated.string("duration_of_ignore_medias"))
        assertEquals(45, migrated.ignoreDurationSeconds)
        assertEquals(true, Settings(mapOf("dynamic_color" to "true")).dynamicColor)
        assertEquals(7, Settings(mapOf("web_search_items_limit_count" to "7")).searchLimit)
    }

    @Test
    fun choiceLabelsAreResolved() {
        val spec = SettingsSchema.spec("theme_mode")!!
        assertEquals("跟随系统", spec.labelOf("system"))
        assertEquals("深色主题", spec.labelOf("dark"))
        // An unknown stored value falls back to itself instead of crashing the row.
        assertEquals("wat", spec.labelOf("wat"))
    }

    @Test
    fun schemaStaysDeliberatelySmall() {
        // The screen is meant to be scannable: keep it well under the original 48 entries.
        assertEquals(22, SettingsSchema.specs.size)
    }

    @Test
    fun stringsAreGroupedSoTheScreenCanRenderThem() {
        for (group in com.xjcheng.musictageditor.settings.SettingGroup.entries) {
            val specs = SettingsSchema.specs.filter { it.group == group }
            assertTrue("group ${group.name} should not be empty", specs.isNotEmpty())
        }
    }
}
