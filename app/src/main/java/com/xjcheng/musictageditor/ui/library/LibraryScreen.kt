/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
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

/**
 * One row of the flattened song list: a section header or a track.
 *
 * [key] is the identity the lazy list uses. Compose treats two items with the same key in
 * one list as a fatal error, so the builder assigns it and guarantees it is unique instead
 * of deriving it from display text, which can legitimately repeat.
 */
internal sealed interface LibraryEntry {
    val key: String

    data class Header(val letter: String, override val key: String) : LibraryEntry

    data class Song(val track: MediaTrack, override val key: String) : LibraryEntry
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
    onSettings: () -> Unit,
    onTabSelected: (Int) -> Unit = {},
) {
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

    val libraryTitle = when (state.selectedTab) {
        TAB_ARTISTS -> stringResource(
            R.string.list_title_count,
            stringResource(R.string.tab_artists),
            remember(state.tracks) { MediaGrouping.artists(state.tracks) }.size,
        )
        TAB_ALBUMS -> stringResource(
            R.string.list_title_count,
            stringResource(R.string.tab_albums),
            remember(state.tracks) { MediaGrouping.albums(state.tracks) }.size,
        )
        else -> stringResource(R.string.song_list_title, state.tracks.size)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = libraryTitle,
                navigationIcon = {
                    IconButton(onClick = onSettings) {
                        Icon(MiuixIcons.Useful.Settings, stringResource(R.string.action_settings))
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(MiuixIcons.Useful.Refresh, stringResource(R.string.action_refresh))
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
            // The filter and the sort chips stay visible on the song list and are hidden on
            // the artist/album browsers, which have their own (alphabetical) ordering.
            if (state.selectedTab == TAB_SONGS) {
                LibrarySearchField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
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
            items(items = entries, key = { it.key }) { entry ->
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

/** One artist or album row: artwork, name, and the count underneath. */
internal data class LibraryGroupRow(
    val title: String,
    val summary: String,
    val albumId: Long,
    val open: () -> Unit,
)

/** A header letter or a row, in the order the list renders them. */
internal sealed interface GroupEntry {
    data class Header(val letter: String) : GroupEntry
    data class Row(val row: LibraryGroupRow) : GroupEntry
}

/** Groups rows by pinyin initial and inserts one header per initial, in A–Z/# order. */
internal fun buildGroupEntries(rows: List<LibraryGroupRow>): List<GroupEntry> {
    val entries = ArrayList<GroupEntry>(rows.size + PinyinIndex.sections.size)
    var current: String? = null
    for (row in rows) {
        val initial = PinyinIndex.initialOf(row.title)
        if (initial != current) {
            entries += GroupEntry.Header(initial)
            current = initial
        }
        entries += GroupEntry.Row(row)
    }
    return entries
}

@Composable
private fun ArtistList(tracks: List<MediaTrack>, onOpenArtist: (ArtistGroup) -> Unit) {
    val artists = remember(tracks) { MediaGrouping.artists(tracks) }
    val countFormat = stringResource(R.string.library_count)
    val rows = remember(artists, countFormat) {
        artists.map { artist ->
            LibraryGroupRow(
                title = artist.name,
                summary = countFormat.format(artist.trackCount),
                albumId = artist.albumId,
                open = { onOpenArtist(artist) },
            )
        }
    }
    GroupList(rows, emptyLabel = stringResource(R.string.label_unknown_artist))
}

@Composable
private fun AlbumList(tracks: List<MediaTrack>, onOpenAlbum: (AlbumGroup) -> Unit) {
    val albums = remember(tracks) { MediaGrouping.albums(tracks) }
    val countFormat = stringResource(R.string.library_count)
    val rows = remember(albums, countFormat) {
        albums.map { album ->
            LibraryGroupRow(
                title = album.name,
                summary = listOf(album.artist, countFormat.format(album.trackCount))
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                albumId = album.albumId,
                open = { onOpenAlbum(album) },
            )
        }
    }
    GroupList(rows, emptyLabel = stringResource(R.string.label_unknown_album))
}

@Composable
private fun GroupList(rows: List<LibraryGroupRow>, emptyLabel: String) {
    if (rows.isEmpty()) {
        Hint(stringResource(R.string.library_empty))
        return
    }
    val entries = remember(rows) { buildGroupEntries(rows) }
    val listState = rememberLazyListState()
    val letterIndex = remember(entries) {
        entries.withIndex()
            .mapNotNull { (index, entry) ->
                (entry as? GroupEntry.Header)?.let { it.letter to index }
            }
            .toMap()
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(entries.size) { index ->
                when (val entry = entries[index]) {
                    is GroupEntry.Header -> SectionHeader(entry.letter)
                    is GroupEntry.Row -> GroupRow(
                        row = entry.row,
                        fallbackTitle = emptyLabel,
                    )
                }
            }
        }
        AlphabetBar(
            letters = PinyinIndex.sections,
            listState = listState,
            letterIndex = letterIndex,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun GroupRow(row: LibraryGroupRow, fallbackTitle: String) {
    val context = LocalContext.current
    val loader = remember(context) { AlbumArtLoader.get(context) }
    var cover by remember(row.albumId) { mutableStateOf(loader.cached(row.albumId)) }
    LaunchedEffect(row.albumId) {
        if (cover == null && row.albumId > 0) cover = loader.load(row.albumId)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { row.open() }
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
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title.ifBlank { fallbackTitle },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 16.sp,
            )
            if (row.summary.isNotBlank()) {
                Text(
                    text = row.summary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = 13.sp,
                )
            }
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

/**
 * Right-edge A–Z/# index.
 *
 * Dragging along the column scrubs the list the way a launcher app-drawer does: the letter
 * under the finger jumps the list and a bubble shows which section is under it. A single tap
 * still jumps once.
 */
@Composable
private fun AlphabetBar(
    letters: List<String>,
    listState: LazyListState,
    letterIndex: Map<String, Int>,
    modifier: Modifier = Modifier,
) {
    if (letters.size < 2) return
    val scope = rememberCoroutineScope()
    var bubble by remember { mutableStateOf<String?>(null) }

    fun jump(letter: String) {
        bubble = letter
        val index = letterIndex[letter] ?: return
        scope.launch { listState.scrollToItem(index) }
    }

    fun letterAt(y: Float, height: Int): String? {
        if (height <= 0) return null
        val ratio = (y / height).coerceIn(0f, 0.9999f)
        return letters.getOrNull((ratio * letters.size).toInt())
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .pointerInput(letters, letterIndex) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            letterAt(offset.y, size.height)?.let(::jump)
                        },
                        onDragEnd = { bubble = null },
                        onDragCancel = { bubble = null },
                    ) { change, _ ->
                        change.consume()
                        val letter = letterAt(change.position.y, size.height)
                        if (letter != null && letter != bubble) jump(letter)
                    }
                }
                .pointerInput(letters, letterIndex) {
                    detectTapGestures { offset ->
                        letterAt(offset.y, size.height)?.let { letter ->
                            jump(letter)
                            scope.launch {
                                delay(450)
                                bubble = null
                            }
                        }
                    }
                }
                .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.8f))
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            for (letter in letters) {
                Text(
                    text = letter,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
            }
        }
        bubble?.let { current ->
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-64).dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = current,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
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
    val sortKeys = tracks.associate { it.id to PinyinIndex.sortKeyOf(it.title) }
    val comparator = when (sort) {
        LibrarySort.TITLE -> compareBy<MediaTrack> { PinyinIndex.rankOf(it.title) }
            .thenBy { sortKeys[it.id] ?: "" }
        LibrarySort.ARTIST -> compareBy<MediaTrack> { PinyinIndex.rankOf(it.artist) }
            .thenBy { PinyinIndex.sortKeyOf(it.artist) }
            .thenBy { sortKeys[it.id] ?: "" }
        LibrarySort.ALBUM -> compareBy<MediaTrack> { PinyinIndex.rankOf(it.album) }
            .thenBy { PinyinIndex.sortKeyOf(it.album) }
            .thenBy { sortKeys[it.id] ?: "" }
        LibrarySort.DURATION -> compareBy<MediaTrack> { it.durationMs }
    }
    val ordered = tracks.sortedWith(if (ascending) comparator else comparator.reversed())

    val entries = ArrayList<LibraryEntry>(ordered.size + PinyinIndex.sections.size)
    val usedKeys = HashSet<String>()
    var current: String? = null
    for (track in ordered) {
        // Only the alphabetical orders have sections. A duration-ordered list has no letter
        // grouping at all, so a section label there would repeat - and, with it, the key.
        if (sort != LibrarySort.DURATION) {
            val letter = when (sort) {
                LibrarySort.ARTIST -> PinyinIndex.initialOf(track.artist)
                LibrarySort.ALBUM -> PinyinIndex.initialOf(track.album)
                else -> PinyinIndex.initialOf(track.title)
            }.ifEmpty { PinyinIndex.OTHER }
            if (letter != current) {
                current = letter
                entries += LibraryEntry.Header(letter, uniqueKey("header-$letter", usedKeys))
            }
        }
        entries += LibraryEntry.Song(track, uniqueKey("song-${track.id}", usedKeys))
    }
    return entries
}

/**
 * Returns [base], or an occurrence-suffixed variant when it is already taken, so a section
 * label that repeats or a media row that MediaStore reports twice can never produce two
 * items with the same lazy-list key.
 */
private fun uniqueKey(base: String, used: MutableSet<String>): String {
    if (used.add(base)) return base
    var occurrence = 2
    while (!used.add("$base#$occurrence")) occurrence++
    return "$base#$occurrence"
}

/** `m:ss` for a duration, which is enough for a library row. */
fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
