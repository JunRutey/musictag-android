/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.activity

import com.xjcheng.musictageditor.R

/**
 * Registered screens that are not implemented yet.
 *
 * Every class here keeps the historical fully-qualified name from
 * com.xjcheng.musictageditor 1.2.5.2 so that explicit-component callers keep resolving.
 * The manifest declares them; implemented screens have their own files while these stay
 * placeholders. Titles and notes are resource ids, so they render in Chinese by default.
 */

class SearchWebPictureActivity :
    PlaceholderActivity(R.string.menuitem_websearch, R.string.placeholder_note_providers)

class SearchWebLyricActivity :
    PlaceholderActivity(R.string.placeholder_search_lyric, R.string.placeholder_note_providers)

class SearchWebTagActivity :
    PlaceholderActivity(R.string.menuitem_searchalltags, R.string.placeholder_note_providers)

class BatchESDSelTagPic :
    PlaceholderActivity(R.string.placeholder_batch_cover, R.string.placeholder_note_batch)

class IgnoreDirSetupActivity :
    PlaceholderActivity(R.string.perf_title_dirpath_of_ignore_medias, R.string.placeholder_note_filters)

/**
 * Intentionally empty: the donation feature is not part of this rewrite. The class exists
 * only so an explicit caller still resolves instead of getting ActivityNotFoundException.
 */
class DonateActivity :
    PlaceholderActivity(R.string.app_name, R.string.placeholder_note_donate)

class CropImageActivity :
    PlaceholderActivity(R.string.label_croppicture, R.string.placeholder_note_crop)
