/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xjcheng.musictageditor.R
import com.xjcheng.musictageditor.media.AlbumArtLoader
import com.xjcheng.musictageditor.media.AlbumGroup
import com.xjcheng.musictageditor.media.ArtistGroup
import com.xjcheng.musictageditor.media.MediaGrouping
import com.xjcheng.musictageditor.media.MediaTrack
import com.xjcheng.musictageditor.media.PinyinIndex
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Edit
import top.yukonga.miuix.kmp.icon.icons.useful.Order
import top.yukonga.miuix.kmp.icon.icons.useful.Personal
import top.yukonga.miuix.kmp.icon.icons.useful.Play
import top.yukonga.miuix.kmp.icon.icons.useful.Refresh
import top.yukonga.miuix.kmp.icon.icons.useful.Search
import top.yukonga.miuix.kmp.icon.icons.useful.Settings
import top.yukonga.miuix.kmp.icon.icons.useful.Stick
import top.yukonga.miuix.kmp.theme.MiuixTheme

const val TAB_SONGS = 0
const val TAB_ARTISTS = 1
const val TAB_ALBUMS = 2

/** How the song list is ordered. */
enum class LibrarySort { TITLE, ARTIST, ALBUM, DURATION }

/** What the library screen renders. */
data class LibraryUiState(
    val tracks: List<MediaTrack> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
    val permissionGranted: Boolean = false,
    val message: String? = null,
    val selectedTab: Int = TAB_SONGS,
)

/** One row of the flattened song list: a section header or a track. */
internal sealed interface LibraryEntry {
    data class Header(val letter: String) : LibraryEntry
    data class Song(val track: MediaTrack) : LibraryEntry
}

/**
 * The local library, laid out like the reference MIUI design: title with the track count,
 * gear on the left, search and sort on the right, artwork rows with a format badge and the
 * duration/bitrate column, an A–Z/# index bar, and the three-tab bottom bar.
 *
 * Sorting, bucketing and the tab lists are derived from [LibraryUiState.tracks]; the artist
 * and album tabs open the song list filtered by that name, which keeps the two placeholder
 * activities untouched until their own screens land.
 */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenTrack: (MediaTrack) -> Unit,
    onBatchEdit: () -> Unit,
    onSettings: () -> Unit,
    onTabSelected: (Int) -> Unit = {},
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var sortOpen by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.TITLE) }
    var ascending by rememberSaveable { mutableStateOf(true) }

    val entries = remember(state.tracks, sort, ascending) {
        buildEntries(state.tracks, sort, ascending)
    }
    val letterIndex = remember(entries) {
        entries.withIndex().mapNotNull { (index, entry) ->
            (entry as? LibraryEntry.Header)?.let { it.letter to index }
        }.toMap()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.song_list_title, state.tracks.size),
                navigationIcon = {
                    IconButton(onClick = onSettings) {
                        Icon(MiuixIcons.Useful.Settings, stringResource(R.string.action_settings))
                    }
                },
                actions = {
                    IconButton(onClick = { searchOpen = !searchOpen }) {
                        Icon(MiuixIcons.Useful.Search, stringResource(R.string.library_filter_hint))
                    }
                    IconButton(onClick = {
                        if (sortOpen) sortOpen = false else {
                            sortOpen = true
                            searchOpen = false
                        }
                    }) {
                        Icon(MiuixIcons.Useful.Order, stringResource(R.string.library_sort))
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(MiuixIcons.Useful.Refresh, stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = onBatchEdit, enabled = state.tracks.isNotEmpty()) {
                        Icon(MiuixIcons.Useful.Edit, stringResource(R.string.action_batch_edit))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(
                items = listOf(
                    NavigationItem(stringResource(R.string.tab_songs), MiuixIcons.Useful.Play),
                    NavigationItem(stringResource(R.string.tab_artists), MiuixIcons.Useful.Personal),
                    NavigationItem(stringResource(R.string.tab_albums), MiuixIcons.Useful.Stick),
                ),
                selected = state.selectedTab,
                onClick = onTabSelected,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (searchOpen) {
                LibrarySearchField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (sortOpen) {
                SortBar(
                    sort = sort,
                    ascending = ascending,
                    onSort = { sort = it },
                    onToggleOrder = { ascending = !ascending },
                )
            }
            state.message?.let { message ->
                Text(
                    text = message,
                    color = MiuixTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            if (!state.permissionGranted) {
                PermissionPrompt(onRequestPermission)
                return@Column
            }
            if (state.loading) {
                Hint(stringResource(R.string.library_loading))
                return@Column
            }
            when (state.selectedTab) {
                TAB_ARTISTS -> ArtistList(state.tracks, onOpenArtist = { artist ->
                    onTabSelected(TAB_SONGS)
                    onQueryChange(artist.name)
                })
                TAB_ALBUMS -> AlbumList(state.tracks, onOpenAlbum = { album ->
                    onTabSelected(TAB_SONGS)
                    onQueryChange(album.name)
                })
                else -> SongList(
                    entries = entries,
                    letterIndex = letterIndex,
                    onOpenTrack = onOpenTrack,
                )
            }
        }
    }
}

@Composable
private fun SongList(
    entries: List<LibraryEntry>,
    letterIndex: Map<String, Int>,
    onOpenTrack: (MediaTrack) -> Unit,
) {
    if (entries.isEmpty()) {
        Hint(stringResource(R.string.library_empty))
        return
    }
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            items(
                items = entries,
                key = { entry ->
                    when (entry) {
                        is LibraryEntry.Header -> "header-${entry.letter}"
                        is LibraryEntry.Song -> "song-${entry.track.id}"
                    }
                },
            ) { entry ->
                when (entry) {
                    is LibraryEntry.Header -> SectionHeader(entry.letter)
                    is LibraryEntry.Song -> SongRow(entry.track, onOpenTrack)
                }
            }
        }
        AlphabetBar(
            letters = letterIndex.keys.toList(),
            listState = listState,
            letterIndex = letterIndex,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun SectionHeader(letter: String) {
    Text(
        text = letter,
        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

/** One song row: 48dp artwork with the container badge, two text lines, duration/bitrate. */
@Composable
private fun SongRow(track: MediaTrack, onOpenTrack: (MediaTrack) -> Unit) {
    val context = LocalContext.current
    val loader = remember(context) { AlbumArtLoader.get(context) }
    var cover by remember(track.albumId) { mutableStateOf(loader.cached(track.albumId)) }
    LaunchedEffect(track.albumId) {
        if (cover == null && track.albumId > 0) cover = loader.load(track.albumId)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenTrack(track) }
            .padding(start = 16.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MiuixTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = cover
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            val badge = track.format
            if (badge.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badge,
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = track.title.ifBlank { stringResource(R.string.label_unknown_title) },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle(track),
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = formatDuration(track.durationMs),
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                fontSize = 12.sp,
            )
            val kbps = track.bitrateKbps
            if (kbps > 0) {
                Text(
                    text = stringResource(R.string.library_bitrate, kbps),
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun ArtistList(tracks: List<MediaTrack>, onOpenArtist: (ArtistGroup) -> Unit) {
    val artists = remember(tracks) { MediaGrouping.artists(tracks) }
    if (artists.isEmpty()) {
        Hint(stringResource(R.string.library_empty))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(artists, key = { it.name }) { artist ->
            BasicComponent(
                title = artist.name.ifBlank { stringResource(R.string.label_unknown_artist) },
                summary = stringResource(R.string.library_count, artist.trackCount),
                onClick = { onOpenArtist(artist) },
            )
        }
    }
}

@Composable
private fun AlbumList(tracks: List<MediaTrack>, onOpenAlbum: (AlbumGroup) -> Unit) {
    val albums = remember(tracks) { MediaGrouping.albums(tracks) }
    if (albums.isEmpty()) {
        Hint(stringResource(R.string.library_empty))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(albums, key = { "${it.name}\u0000${it.artist}" }) { album ->
            BasicComponent(
                title = album.name.ifBlank { stringResource(R.string.label_unknown_album) },
                summary = listOf(
                    album.artist.ifBlank { stringResource(R.string.label_unknown_artist) },
                    stringResource(R.string.library_count, album.trackCount),
                ).joinToString(" · "),
                onClick = { onOpenAlbum(album) },
            )
        }
    }
}

/** Filter field styled like the MIUI search capsule. */
@Composable
private fun LibrarySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            MiuixIcons.Useful.Search,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = stringResource(R.string.library_filter_hint),
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = 14.sp,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 14.sp),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Sort chips: the field, then ascending/descending. */
@Composable
private fun SortBar(
    sort: LibrarySort,
    ascending: Boolean,
    onSort: (LibrarySort) -> Unit,
    onToggleOrder: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SortChip(stringResource(R.string.sort_title), sort == LibrarySort.TITLE) { onSort(LibrarySort.TITLE) }
        SortChip(stringResource(R.string.sort_artist), sort == LibrarySort.ARTIST) { onSort(LibrarySort.ARTIST) }
        SortChip(stringResource(R.string.sort_album), sort == LibrarySort.ALBUM) { onSort(LibrarySort.ALBUM) }
        SortChip(stringResource(R.string.sort_duration), sort == LibrarySort.DURATION) { onSort(LibrarySort.DURATION) }
        SortChip(
            stringResource(if (ascending) R.string.sort_ascending else R.string.sort_descending),
            selected = false,
            onClick = onToggleOrder,
        )
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceContainerVariant,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Right-edge A–Z/# index. Tapping a letter scrolls the list to that section. */
@Composable
private fun AlphabetBar(
    letters: List<String>,
    listState: LazyListState,
    letterIndex: Map<String, Int>,
    modifier: Modifier = Modifier,
) {
    if (letters.size < 2) return
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .padding(end = 2.dp)
            .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.8f))
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        for (letter in letters) {
            Text(
                text = letter,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                fontSize = 9.sp,
                modifier = Modifier
                    .clickable {
                        val index = letterIndex[letter] ?: return@clickable
                        scope.launch { listState.scrollToItem(index) }
                    }
                    .padding(horizontal = 3.dp),
            )
        }
    }
}

@Composable
private fun PermissionPrompt(onRequestPermission: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.library_permission_reason),
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        BasicComponent(
            title = stringResource(R.string.action_grant),
            onClick = onRequestPermission,
        )
    }
}

@Composable
private fun Hint(text: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(text = text, color = MiuixTheme.colorScheme.onSurfaceContainerVariant, fontSize = 14.sp)
    }
}

@Composable
private fun subtitle(track: MediaTrack): String {
    val artist = track.artist.ifBlank { stringResource(R.string.label_unknown_artist) }
    return if (track.album.isBlank()) artist else "$artist - ${track.album}"
}

/** Flattens the filtered list into headers + rows, ordered by [sort]. */
internal fun buildEntries(tracks: List<MediaTrack>, sort: LibrarySort, ascending: Boolean): List<LibraryEntry> {
    if (tracks.isEmpty()) return emptyList()
    val keys = tracks.associate { it.id to PinyinIndex.sortKeyOf(it.title) }
    val comparator = when (sort) {
        LibrarySort.TITLE -> compareBy<MediaTrack> { PinyinIndex.initialOf(it.title) }
            .thenBy { keys[it.id] ?: "" }
        LibrarySort.ARTIST -> compareBy<MediaTrack> { PinyinIndex.initialOf(it.artist) }
            .thenBy { PinyinIndex.sortKeyOf(it.artist) }
            .thenBy { keys[it.id] ?: "" }
        LibrarySort.ALBUM -> compareBy<MediaTrack> { PinyinIndex.initialOf(it.album) }
            .thenBy { PinyinIndex.sortKeyOf(it.album) }
            .thenBy { keys[it.id] ?: "" }
        LibrarySort.DURATION -> compareBy<MediaTrack> { it.durationMs }
    }
    val ordered = tracks.sortedWith(if (ascending) comparator else comparator.reversed())

    val entries = ArrayList<LibraryEntry>(ordered.size + 27)
    var current: String? = null
    for (track in ordered) {
        val letter = when (sort) {
            LibrarySort.ARTIST -> PinyinIndex.initialOf(track.artist)
            LibrarySort.ALBUM -> PinyinIndex.initialOf(track.album)
            else -> PinyinIndex.initialOf(track.title)
        }.ifEmpty { PinyinIndex.OTHER }
        if (letter != current) {
            entries += LibraryEntry.Header(letter)
            current = letter
        }
        entries += LibraryEntry.Song(track)
    }
    return entries
}

/** `m:ss` for a duration, which is enough for a library row. */
fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
