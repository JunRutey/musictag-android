/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.activity

import com.xjcheng.musictageditor.R

/**
 * Manual batch editing was removed in 1.1.1.
 *
 * Writing the same value into many files turned out to be of little use, so the class now
 * renders the placeholder shell. The name and the [EXTRA_URIS] extra stay because
 * `SEND_MULTIPLE` callers and the compatibility contract still target this component; the
 * batch auto-match flow replaces it.
 */
class BatchEditSongsDetail :
    PlaceholderActivity(R.string.batch_title, R.string.placeholder_note_batch_removed) {

    companion object {
        const val EXTRA_URIS = "com.xjcheng.musictageditor.extra.BATCH_URIS"
    }
}
