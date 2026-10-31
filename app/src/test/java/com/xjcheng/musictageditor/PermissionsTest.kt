/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor

import android.Manifest
import com.xjcheng.musictageditor.media.Permissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The permission sets the first-launch prompt uses, per platform level. */
class PermissionsTest {

    @Test
    fun android13AsksForMediaAudioAndNotifications() {
        assertEquals(
            listOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
            ),
            Permissions.firstLaunch(33),
        )
        assertEquals(
            listOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
            ),
            Permissions.firstLaunch(36),
        )
    }

    @Test
    fun legacyAsksForStorageAndSkipsNotifications() {
        assertEquals(
            listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ),
            Permissions.firstLaunch(28),
        )
        // Android 10/12 still use READ_EXTERNAL_STORAGE but have no notification dialog.
        assertEquals(
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            Permissions.firstLaunch(30),
        )
    }

    @Test
    fun android24WorksWithStoragePermissions() {
        val permissions = Permissions.firstLaunch(24)
        assertEquals(
            listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ),
            permissions,
        )
        assertEquals(permissions.size, permissions.toSet().size)
    }

    @Test
    fun optionalPermissionsAreNullWhenThePlatformLacksThem() {
        assertNull(Permissions.notifications(32))
        assertNull(Permissions.storageWrite(29))
        assertNull(Permissions.storageWrite(36))
    }
}
