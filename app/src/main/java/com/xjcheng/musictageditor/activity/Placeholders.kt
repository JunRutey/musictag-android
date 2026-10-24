/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.activity

/**
 * Registered screens that are not implemented yet.
 *
 * Every class here keeps the historical fully-qualified name from
 * com.xjcheng.musictageditor 1.2.5.2 so that explicit-component callers keep
 * resolving. The manifest declares them; M2–M6 replace the bodies with real
 * Compose screens while the class names stay put.
 */

class AlbumActivity :
    PlaceholderActivity("Album", "M2 — library browsing")

class ArtistActivity :
    PlaceholderActivity("Artist", "M2 — library browsing")

class SearchWebPictureActivity :
    PlaceholderActivity("Search cover art", "M4 — online providers")

class SearchWebLyricActivity :
    PlaceholderActivity("Search lyrics", "M4 — online providers")

class SearchWebTagActivity :
    PlaceholderActivity("Search tags online", "M4 — online providers")

class BatchESDSelTagPic :
    PlaceholderActivity("Choose cover for selection", "M5 — batch editing")

class IgnoreDirSetupActivity :
    PlaceholderActivity("Ignored directories", "M2 — media index filters")

/**
 * Intentionally empty: the donation feature is not part of this rewrite. The class
 * exists only so an explicit caller still resolves instead of getting
 * ActivityNotFoundException.
 */
class DonateActivity :
    PlaceholderActivity("Music Tag", "Donations are not supported in this build")

class CropImageActivity :
    PlaceholderActivity("Crop picture", "M6 — cover art cropping")
