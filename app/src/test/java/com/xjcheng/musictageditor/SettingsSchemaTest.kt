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
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSchemaTest {

    @Test
    fun everySpecIsUsable() {
        assertTrue("expected a substantial preference set", SettingsSchema.specs.size >= 40)
        for (spec in SettingsSchema.specs) {
            assertTrue("key must not be blank", spec.key.isNotBlank())
            assertTrue("title must not be blank for ${spec.key}", spec.title.isNotBlank())
            assertNotNull(SettingsSchema.spec(spec.key))
        }
    }

    @Test
    fun keysAreUnique() {
        val keys = SettingsSchema.specs.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun originalKeyNamesArePreserved() {
        // Names from the original pref_settings.xml, where the semantics match.
        val expected = listOf(
            "is_show_filetags_image",
            "is_show_albumart_image",
            "is_show_folder_image",
            "tags_sources_combination",
            "tags_sources_picture",
            "tags_sources_lyric",
            "download_lyric_translations",
            "download_lyric_translations_t2s_mode",
            "connectors_lyric_and_tlyric",
            "connectors_artists",
            "reformat_lyric_timetag",
            "remove_lyric_timetag",
            "delete_lyric_blank_text_lines",
            "delete_lyric_head_tags",
            "lrcfiles_encoding",
            "lrcfiles_save_directory_mode",
            "lrcfiles_filename_format_mode",
            "allow_save_lrc_file_while_save_file_tags",
            "duration_of_ignore_medias",
            "dirpath_of_ignore_medias",
            "video_of_ignore_medias",
            "dark_theme",
            "allow_black_theme",
            "primary_color",
            "accent_color",
            "tab_items",
            "web_search_criteria",
            "web_search_items_limit_count",
            "picture_in_file_tags_size_limit",
            "picture_in_file_tags_format_limit",
            "id3v2_mode",
            "allow_comment_tag_write_163key",
            "allow_edit_dff_file_tags",
            "always_show_batch_buttons",
            "force_replace_albumart",
        )
        for (key in expected) {
            assertNotNull("missing original key $key", SettingsSchema.spec(key))
        }
    }

    @Test
    fun defaultsMatchTheirDeclaredValues() {
        val defaults = SettingsSchema.defaults
        for (spec in SettingsSchema.specs) {
            when (spec.type) {
                SettingType.BOOLEAN -> assertEquals(spec.key, spec.default as Boolean, defaults.boolean(spec.key))
                SettingType.INT -> assertEquals(spec.key, spec.default as Int, defaults.int(spec.key))
                SettingType.STRING -> assertEquals(spec.key, spec.default as String, defaults.string(spec.key))
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
                SettingType.BOOLEAN -> assertEquals(spec.key, spec.default as Boolean, decoded.boolean(spec.key))
                SettingType.INT -> assertEquals(spec.key, spec.default as Int, decoded.int(spec.key))
                SettingType.STRING -> assertEquals(spec.key, spec.default as String, decoded.string(spec.key))
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
            .with("lastfm_api_key", "abc")
            .with("connectors_lyric_and_tlyric", " | ")
        assertTrue(settings.id3v23)
        assertEquals(7, settings.searchLimit)
        assertEquals("abc", settings.lastFmApiKey)
        assertEquals(" | ", settings.connectorLyricTranslation)
        assertEquals("musicbrainz,itunes,netease,qq,kuwo,kugou,vgmdb,lastfm", settings.tagsSourcesCombination)
    }

    @Test
    fun stringsAreGroupedSoTheScreenCanRenderThem() {
        for (group in com.xjcheng.musictageditor.settings.SettingGroup.entries) {
            val specs = SettingsSchema.specs.filter { it.group == group }
            assertTrue("group ${group.name} should not be empty", specs.isNotEmpty())
        }
    }
}
