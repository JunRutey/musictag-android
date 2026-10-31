/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.net

import com.xjcheng.musictageditor.core.net.MergePolicy
import com.xjcheng.musictageditor.core.net.ProviderRegistry
import com.xjcheng.musictageditor.core.net.http.OkHttpTransport
import com.xjcheng.musictageditor.core.net.providers.CoverArtArchive
import com.xjcheng.musictageditor.core.net.providers.ItunesProvider
import com.xjcheng.musictageditor.core.net.providers.KuwoProvider
import com.xjcheng.musictageditor.core.net.providers.MusicBrainzProvider
import com.xjcheng.musictageditor.core.net.providers.NeteaseProvider
import com.xjcheng.musictageditor.core.net.providers.QqProvider
import com.xjcheng.musictageditor.settings.Settings

/**
 * Wires the providers together for the app.
 *
 * NetEase, QQ and Kuwo are the primary catalogue; iTunes and MusicBrainz (with Cover Art
 * Archive) are the international fallback. Kugou and Apple Music were removed in 1.1.6: the
 * Kugou endpoint returned nothing useful and the Apple Music token scrape failed often enough
 * that both reported "no response" on every search. The order lives in [MergePolicy.DEFAULT].
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
                ItunesProvider(transport),
                MusicBrainzProvider(transport),
                CoverArtArchive(transport),
            ),
            MergePolicy.DEFAULT,
        )
    }
}
