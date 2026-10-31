/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor

import android.content.SearchRecentSuggestionsProvider

/**
 * Recent-query suggestions for the system search dialog.
 *
 * Lives in the root package because that is where com.xjcheng.musictageditor 1.2.5.2
 * declared it (`com.xjcheng.musictageditor.SearchSuggProvider`); the class name plus
 * the release authority are part of the compatibility contract.
 *
 * M5 starts recording queries; the provider exists from M0 so the search integration
 * never 404s on the authority.
 */
class SearchSuggProvider : SearchRecentSuggestionsProvider() {

    init {
        setupSuggestions(AUTHORITY, SearchRecentSuggestionsProvider.DATABASE_MODE_QUERIES)
    }

    companion object {
        /**
         * Authority this provider answers on.
         *
         * Derived from the application id instead of hardcoded: the release build resolves
         * to `com.xjcheng.musictageditor.SearchSugg` (the contract value) while the `.debug`
         * build gets its own, so both APKs can be installed at the same time. Must stay in
         * sync with `android:authorities` in AndroidManifest.xml and the searchable config.
         */
        val AUTHORITY: String get() = "${BuildConfig.APPLICATION_ID}.SearchSugg"
    }
}
