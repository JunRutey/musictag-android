/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The runtime permissions this app needs, decided from the platform level.
 *
 * The app asks for everything it may need the first time it starts, instead of waiting for a
 * feature to fail: reading the library, writing `.lrc` side files on older releases, and
 * posting the batch progress notification. Each helper is a pure function of the SDK level so
 * the combinations are unit-testable without a device.
 */
object Permissions {

    const val PREFS = "permissions"
    private const val KEY_ASKED = "asked_on_first_launch"

    /** Audio read permission: split into media-specific permissions on Android 13+. */
    fun audio(sdk: Int = Build.VERSION.SDK_INT): String =
        if (sdk >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            @Suppress("DEPRECATION")
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    /** Legacy write access, only needed to save `.lrc` files next to the audio on Android 9-. */
    fun storageWrite(sdk: Int = Build.VERSION.SDK_INT): String? =
        if (sdk <= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        } else {
            null
        }

    /** Notification permission exists from Android 13 on. */
    fun notifications(sdk: Int = Build.VERSION.SDK_INT): String? =
        if (sdk >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    /** Everything to ask for on first launch, in a stable order. */
    fun firstLaunch(sdk: Int = Build.VERSION.SDK_INT): List<String> =
        buildList {
            add(audio(sdk))
            storageWrite(sdk)?.let(::add)
            notifications(sdk)?.let(::add)
        }.distinct()

    fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun audioGranted(context: Context): Boolean = granted(context, audio())

    /** True once the first-launch prompt has run, so it is not repeated on every start. */
    fun askedBefore(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ASKED, true)
            .apply()
    }
}
