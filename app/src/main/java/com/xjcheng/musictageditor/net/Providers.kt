/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.net

import com.xjcheng.musictageditor.core.net.MergePolicy
import com.xjcheng.musictageditor.core.net.ProviderRegistry
import com.xjcheng.musictageditor.core.net.http.OkHttpTransport
import com.xjcheng.musictageditor.core.net.providers.AppleMusicProvider
import com.xjcheng.musictageditor.core.net.providers.CoverArtArchive
import com.xjcheng.musictageditor.core.net.providers.ItunesProvider
import com.xjcheng.musictageditor.core.net.providers.KugouProvider
import com.xjcheng.musictageditor.core.net.providers.KuwoProvider
import com.xjcheng.musictageditor.core.net.providers.MusicBrainzProvider
import com.xjcheng.musictageditor.core.net.providers.NeteaseProvider
import com.xjcheng.musictageditor.core.net.providers.QqProvider
import com.xjcheng.musictageditor.settings.Settings

/**
 * Wires the providers together for the app.
 *
 * The four Chinese services are the primary catalogue; Apple Music, iTunes and MusicBrainz
 * (with Cover Art Archive) are the international fallback. The priority order lives in
 * [MergePolicy.DEFAULT]: the source-priority text settings were removed because editing them
 * had no visible effect on the result.
 *
 * Kept out of the activities so the same registry can be reused by the batch service and,
 * later, by tests that swap in a fake transport.
 */
object Providers {

    fun registry(settings: Settings): ProviderRegistry {
        val transport = OkHttpTransport()
        return ProviderRegistry(
            listOf(
                NeteaseProvider(transport),
                QqProvider(transport),
                KuwoProvider(transport),
                KugouProvider(transport),
                AppleMusicProvider(
                    transport = transport,
                    region = settings.appleRegion,
                    language = settings.appleLanguage,
                    coverSize = settings.appleCoverSize,
                ),
                ItunesProvider(transport),
                MusicBrainzProvider(transport),
                CoverArtArchive(transport),
            ),
            MergePolicy.DEFAULT,
        )
    }
}
