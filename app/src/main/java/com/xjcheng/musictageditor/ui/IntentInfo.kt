/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.ui

import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * Renders the intent that woke an activity up.
 *
 * In M0 every screen shows this so the compatibility contract can be checked by hand
 * on a device (and by the compat-harness app later): action, MIME type, URI and the
 * resolved data source are exactly what a third-party caller handed over.
 */
fun describeIntent(intent: Intent?): List<String> {
    if (intent == null) return listOf("intent: <none>")
    val lines = mutableListOf<String>()
    lines += "action: ${intent.action ?: "<none>"}"
    lines += "type: ${intent.type ?: "<none>"}"
    val uri: Uri? = intent.data
    if (uri != null) {
        lines += "data: $uri"
        lines += "scheme: ${uri.scheme ?: "<none>"}"
        lines += "authority: ${uri.authority ?: "<none>"}"
        lines += "lastPathSegment: ${uri.lastPathSegment ?: "<none>"}"
    } else {
        lines += "data: <none>"
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        val clip = intent.clipData
        if (clip != null) {
            lines += "clipData: ${clip.itemCount} item(s)"
            for (i in 0 until clip.itemCount) {
                lines += "  [$i] ${clip.getItemAt(i).uri}"
            }
        }
    }
    lines += "extras: ${intent.extras?.keySet()?.joinToString() ?: "<none>"}"
    return lines
}
