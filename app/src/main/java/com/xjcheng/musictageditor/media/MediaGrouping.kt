/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.media

/** One artist row of the artist tab. */
data class ArtistGroup(
    val name: String,
    val trackCount: Int,
    /** Album id of the first track, for the row artwork. */
    val albumId: Long = 0,
)

/** One album row of the album tab. */
data class AlbumGroup(
    val name: String,
    val artist: String,
    val trackCount: Int,
    val albumId: Long = 0,
)

/**
 * Turns a flat track list into the artist and album tabs.
 *
 * Pure functions over [MediaTrack] so the grouping and its pinyin ordering are unit tested
 * instead of being asserted by eye on a device.
 */
object MediaGrouping {

    fun artists(tracks: List<MediaTrack>): List<ArtistGroup> =
        tracks.groupBy { it.artist.trim() }
            .map { (artist, group) ->
                ArtistGroup(
                    name = artist,
                    trackCount = group.size,
                    albumId = group.firstOrNull { it.albumId > 0 }?.albumId ?: 0,
                )
            }
            .sortedWith { first, second -> PinyinIndex.compare(first.name, second.name) }

    /**
     * Albums are keyed by name *and* artist: two different artists can both release a
     * "Greatest Hits" and merging them would be wrong.
     */
    fun albums(tracks: List<MediaTrack>): List<AlbumGroup> =
        tracks.groupBy { it.album.trim() to it.artist.trim() }
            .map { (key, group) ->
                AlbumGroup(
                    name = key.first,
                    artist = key.second,
                    trackCount = group.size,
                    albumId = group.firstOrNull { it.albumId > 0 }?.albumId ?: 0,
                )
            }
            .sortedWith { first, second ->
                val byName = PinyinIndex.compare(first.name, second.name)
                if (byName != 0) byName else PinyinIndex.compare(first.artist, second.artist)
            }
}
