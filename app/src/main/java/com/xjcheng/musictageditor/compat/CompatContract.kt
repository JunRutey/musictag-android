/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.compat

/**
 * Values that third-party callers and the Android framework depend on.
 *
 * Nothing here is cosmetic: renaming a constant silently breaks either explicit-component
 * launches, the search suggestions URI, or file sharing. See docs/COMPAT.md and
 * docs/MANIFEST_CONTRACT.json; ManifestContractTest and the CI contract check enforce it.
 */
object CompatContract {

    /** Application id == package name of the original app (1.2.5.2). */
    const val PACKAGE: String = "com.xjcheng.musictageditor"

    /** SearchRecentSuggestionsProvider authority declared in res/xml/searchable.xml. */
    const val SUGGEST_AUTHORITY: String = "com.xjcheng.musictageditor.SearchSugg"

    /** FileProvider authority used when sharing edited files. */
    const val FILE_PROVIDER_AUTHORITY: String = "com.xjcheng.musictageditor.provider"

    /** Suggest intent data URI from res/xml/searchable.xml. */
    const val SUGGEST_INTENT_DATA: String = "content://com.xjcheng.musictageditor/datatable"

    /**
     * Audio MIME types accepted by the VIEW filter. Order matches the original manifest
     * so a diff of the generated contract stays readable.
     */
    val AUDIO_MIME_TYPES: List<String> = listOf(
        "audio/ape",
        "audio/x-ape",
        "audio/flac",
        "audio/mpeg",
        "audio/x-wav",
        "audio/mp4",
        "audio/mp4a-latm",
        "audio/ogg",
        "audio/opus",
        "audio/x-ms-wma",
        "audio/x-aiff",
        "audio/x-musepack",
        "audio/x-wavpack",
        "audio/x-tta",
        "audio/dsf",
        "audio/x-dsf",
        "audio/dff",
        "audio/x-dff",
        "audio/aac",
        "audio/x-aac",
    )

    /**
     * Extensions matched through the `*/*` filter, including the duplicated-path
     * variants the original app shipped to tolerate multi-dot file names.
     */
    val EXTENSION_PATH_PATTERNS: List<String> = buildList {
        val extensions = listOf("ape", "tta", "wv", "mpc", "opus", "dsf", "dff", "aac")
        for (ext in extensions) add(""".*\.$ext""")
        for (ext in extensions) add(""".*\..*\.$ext""")
        for (ext in extensions) add(""".*\..*\..*\.$ext""")
    }

    /** Screens that must stay registered under their historical class names. */
    val COMPONENT_CLASSES: List<String> = listOf(
        "com.xjcheng.musictageditor.MainActivity",
        "com.xjcheng.musictageditor.SongDetailActivity",
        "com.xjcheng.musictageditor.activity.SearchActivity",
        "com.xjcheng.musictageditor.activity.SearchMediaActivity",
        "com.xjcheng.musictageditor.activity.BatchEditSongsDetail",
        "com.xjcheng.musictageditor.activity.SettingsActivity",
        "com.xjcheng.musictageditor.activity.AlbumActivity",
        "com.xjcheng.musictageditor.activity.ArtistActivity",
        "com.xjcheng.musictageditor.activity.SearchWebPictureActivity",
        "com.xjcheng.musictageditor.activity.SearchWebLyricActivity",
        "com.xjcheng.musictageditor.activity.SearchWebTagActivity",
        "com.xjcheng.musictageditor.activity.BatchESDSelTagPic",
        "com.xjcheng.musictageditor.activity.IgnoreDirSetupActivity",
        "com.xjcheng.musictageditor.activity.DonateActivity",
        "com.xjcheng.musictageditor.activity.CropImageActivity",
        "com.xjcheng.musictageditor.service.BatchService",
        "com.xjcheng.musictageditor.service.RefreshMediaStoreService",
        "com.xjcheng.musictageditor.SearchSuggProvider",
    )
}
