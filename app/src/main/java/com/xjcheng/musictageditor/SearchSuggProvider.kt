/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor

import android.content.SearchRecentSuggestionsProvider
import com.xjcheng.musictageditor.compat.CompatContract

/**
 * Recent-query suggestions for the system search dialog.
 *
 * Lives in the root package because that is where com.xjcheng.musictageditor 1.2.5.2
 * declared it (`com.xjcheng.musictageditor.SearchSuggProvider`); the class name plus
 * the authority below are part of the compatibility contract.
 *
 * M5 starts recording queries; the provider exists from M0 so the search integration
 * never 404s on the authority.
 */
class SearchSuggProvider : SearchRecentSuggestionsProvider() {

    init {
        setupSuggestions(AUTHORITY, SearchRecentSuggestionsProvider.DATABASE_MODE_QUERIES)
    }

    companion object {
        const val AUTHORITY: String = CompatContract.SUGGEST_AUTHORITY
    }
}
