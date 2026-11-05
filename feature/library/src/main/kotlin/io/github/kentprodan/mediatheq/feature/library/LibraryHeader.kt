package io.github.kentprodan.mediatheq.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.data.TopButton
import io.github.kentprodan.mediatheq.core.data.TopButtonStyle
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/**
 * The Library list header (#123): icon and title (with back when [onBack] is set and not hidden), the top button row
 * acting on the whole list, the tool row (sort with its direction arrow, filter, view, select, search in list) and the
 * item count.
 */
@Composable
internal fun LibraryHeader(
    state: LibraryUiState,
    onAction: (LibraryAction) -> Unit,
    onNewPlaylist: () -> Unit,
    onBack: (() -> Unit)?,
    onSelect: (() -> Unit)?,
) {
    val header = state.header
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = MediatheqSpacing.l)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null && !header.hideBackButton) {
                IconButton(onClick = onBack) {
                    Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.library_back))
                }
            }
            Icon(state.titleIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                state.title,
                Modifier.weight(1f).padding(horizontal = MediatheqSpacing.m).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.filter == LibraryFilter.Playlists) {
                IconButton(onClick = onNewPlaylist) {
                    Icon(MqIcons.Add, contentDescription = stringResource(R.string.library_new_playlist))
                }
            }
        }
        TopButtons(header.topButtonStyle, TopButton.entries - header.hiddenTopButtons, state.itemCount > 0, onAction)
        ToolRow(state, onAction, onSelect)
    }
}

@Composable
private fun TopButtons(
    style: TopButtonStyle,
    buttons: List<TopButton>,
    enabled: Boolean,
    onAction: (LibraryAction) -> Unit,
) {
    if (style == TopButtonStyle.Hidden || buttons.isEmpty()) return
    // Scrolls rather than clipping labels at large font sizes.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = MediatheqSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
    ) {
        buttons.forEach { button ->
            val label = stringResource(button.label)
            val onClick = { onAction(LibraryAction.Top(button)) }
            when (style) {
                TopButtonStyle.Normal -> {
                    FilledTonalButton(
                        onClick,
                        enabled = enabled,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(button.icon, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                        Text(label, Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                }

                TopButtonStyle.LargeIcons -> {
                    FilledTonalIconButton(
                        onClick,
                        Modifier.size(IconButtonDefaults.mediumContainerSize()),
                        enabled = enabled,
                    ) { Icon(button.icon, label, Modifier.size(IconButtonDefaults.mediumIconSize)) }
                }

                else -> {
                    FilledTonalIconButton(onClick, enabled = enabled) { Icon(button.icon, label) }
                }
            }
        }
    }
}

@Composable
private fun ToolRow(
    state: LibraryUiState,
    onAction: (LibraryAction) -> Unit,
    onSelect: (() -> Unit)?,
) {
    var sortMenu by rememberSaveable { mutableStateOf(false) }
    var filterMenu by rememberSaveable { mutableStateOf(false) }
    var viewMenu by rememberSaveable { mutableStateOf(false) }
    val listFilter = state.listFilter
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box {
            val ascending = state.sort.ascending != state.header.invertedSortArrows
            TextButton(onClick = { sortMenu = true }, enabled = state.filter.sorts.size > 1) {
                Text(stringResource(state.sort.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(
                    if (ascending) MqIcons.ArrowUpward else MqIcons.ArrowDownward,
                    contentDescription = null,
                    Modifier.padding(start = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
                )
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                state.filter.sorts.forEach { sort ->
                    CheckedMenuItem(stringResource(sort.label), checked = sort == state.sort) {
                        onAction(LibraryAction.SelectSort(sort))
                        sortMenu = false
                    }
                }
            }
        }
        Box(Modifier.weight(1f))
        // Years, genres and labels are no items of their own: nothing to filter, select or search (#129-#131).
        val itemTools =
            state.filter != LibraryFilter.Years && state.filter != LibraryFilter.Genres &&
                state.filter != LibraryFilter.RecordLabels
        if (itemTools) {
            Box {
                // Downloaded only stays set across lists but does nothing on Artists, where it cannot be unticked.
                val filterOn =
                    listFilter.favoritesOnly || (listFilter.downloadedOnly && state.filter != LibraryFilter.Artists)
                val on = stringResource(R.string.library_filter_on)
                IconButton(
                    onClick = { filterMenu = true },
                    colors =
                        if (filterOn) {
                            IconButtonDefaults.filledTonalIconButtonColors()
                        } else {
                            IconButtonDefaults
                                .iconButtonColors()
                        },
                    modifier = Modifier.semantics { if (filterOn) stateDescription = on },
                ) { Icon(MqIcons.FilterList, contentDescription = stringResource(R.string.library_filter)) }
                DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                    CheckMenuItem(stringResource(R.string.library_favorites_only), listFilter.favoritesOnly) {
                        onAction(LibraryAction.SetFavoritesOnly(!listFilter.favoritesOnly))
                    }
                    // Artists have no download state of their own.
                    if (state.filter != LibraryFilter.Artists) {
                        CheckMenuItem(stringResource(R.string.library_downloaded_only), listFilter.downloadedOnly) {
                            onAction(LibraryAction.SetDownloadedOnly(!listFilter.downloadedOnly))
                        }
                    }
                }
            }
        }
        // Only the album grid (its columns) and the genres (grid or rows) have view options.
        if (state.filter == LibraryFilter.Albums || state.filter == LibraryFilter.Genres) {
            Box {
                IconButton(onClick = { viewMenu = true }) {
                    Icon(MqIcons.GridView, contentDescription = stringResource(R.string.library_view_options))
                }
                DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }) {
                    if (state.filter == LibraryFilter.Genres) {
                        val views = listOf(false to R.string.library_view_list, true to R.string.library_view_grid)
                        views.forEach { (grid, label) ->
                            CheckedMenuItem(stringResource(label), grid == state.genresGrid) {
                                onAction(LibraryAction.SetGenresGrid(grid))
                                viewMenu = false
                            }
                        }
                        HorizontalDivider()
                    }
                    LibraryPreferences.ALBUM_COLUMNS.forEach { columns ->
                        CheckedMenuItem(
                            stringResource(R.string.library_columns, columns),
                            columns == state.albumColumns,
                        ) {
                            onAction(LibraryAction.SetAlbumColumns(columns))
                            viewMenu = false
                        }
                    }
                }
            }
        }
        if (onSelect != null && itemTools) {
            IconButton(onClick = onSelect, enabled = state.itemCount > 0) {
                Icon(MqIcons.Checklist, contentDescription = stringResource(R.string.library_select))
            }
        }
        if (itemTools) {
            IconButton(onClick = { onAction(LibraryAction.Search(if (listFilter.search == null) "" else null)) }) {
                Icon(MqIcons.Search, contentDescription = stringResource(R.string.library_search_in_list))
            }
        }
        if (state.header.showItemCounts && state.itemCount > 0) {
            Text(
                pluralStringResource(R.plurals.library_items, state.itemCount, state.itemCount),
                Modifier.padding(start = MediatheqSpacing.xs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A menu choice with a check mark on the selected one (selection is not shown by color alone). */
@Composable
internal fun CheckedMenuItem(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        leadingIcon = { if (checked) Icon(MqIcons.Check, contentDescription = null) else Box(Modifier) },
        // TalkBack announces which option is active.
        modifier =
            Modifier.semantics {
                selected = checked
                role = Role.RadioButton
            },
    )
}

/** An on/off menu item: a check while on, checkbox semantics. */
@Composable
private fun CheckMenuItem(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    val on = stringResource(R.string.library_filter_on)
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        leadingIcon = { if (checked) Icon(MqIcons.Check, contentDescription = null) else Box(Modifier) },
        modifier =
            Modifier.semantics {
                role = Role.Checkbox
                if (checked) stateDescription = on
            },
    )
}

/** The natural direction of a sort: names up (A to Z), dates, years and counts down (newest / most first). */
internal val LibrarySort.ascending: Boolean
    get() =
        this != LibrarySort.RecentlyAdded && this != LibrarySort.Year && this != LibrarySort.AlbumCount &&
            this != LibrarySort.TrackCount

/** The list's name; on a year's page the year ("Unknown year" without one), on a label's page its name. */
private val LibraryUiState.title: String
    @Composable get() =
        yearPage?.let { page -> page.year?.toString() ?: stringResource(R.string.library_unknown_year) }
            ?: labelPage?.name
            ?: stringResource(filter.label)

private val LibraryUiState.titleIcon: ImageVector
    @Composable get() =
        when {
            yearPage != null -> MqIcons.CalendarMonth
            labelPage != null -> MqIcons.RecordLabel
            else -> filter.icon
        }

internal val LibraryFilter.icon: ImageVector
    @Composable get() =
        when (this) {
            LibraryFilter.Albums -> MqIcons.Library
            LibraryFilter.Artists -> MqIcons.Person
            LibraryFilter.Playlists -> MqIcons.QueueMusic
            LibraryFilter.Songs -> MqIcons.MusicNote
            LibraryFilter.Years -> MqIcons.CalendarMonth
            LibraryFilter.Genres -> MqIcons.Genres
            LibraryFilter.RecordLabels -> MqIcons.RecordLabel
        }

private val TopButton.label: Int
    get() =
        when (this) {
            TopButton.Play -> R.string.library_play
            TopButton.Shuffle -> R.string.library_shuffle
            TopButton.PlayNext -> R.string.library_play_next
            TopButton.AddToQueue -> R.string.library_add_to_queue
        }

private val TopButton.icon: ImageVector
    @Composable get() =
        when (this) {
            TopButton.Play -> MqIcons.PlayArrow
            TopButton.Shuffle -> MqIcons.Shuffle
            TopButton.PlayNext -> MqIcons.PlayNext
            TopButton.AddToQueue -> MqIcons.AddToQueue
        }

internal val LibraryFilter.label: Int
    get() =
        when (this) {
            LibraryFilter.Albums -> R.string.library_filter_albums
            LibraryFilter.Artists -> R.string.library_filter_artists
            LibraryFilter.Playlists -> R.string.library_filter_playlists
            LibraryFilter.Songs -> R.string.library_filter_songs
            LibraryFilter.Years -> R.string.library_filter_years
            LibraryFilter.Genres -> R.string.library_filter_genres
            LibraryFilter.RecordLabels -> R.string.library_filter_record_labels
        }

internal val LibrarySort.label: Int
    get() =
        when (this) {
            LibrarySort.Name -> R.string.library_sort_name
            LibrarySort.RecentlyAdded -> R.string.library_sort_recently_added
            LibrarySort.Year -> R.string.library_sort_year
            LibrarySort.Artist -> R.string.library_sort_artist
            LibrarySort.Album -> R.string.library_sort_album
            LibrarySort.AlbumCount -> R.string.library_sort_album_count
            LibrarySort.TrackCount -> R.string.library_sort_track_count
        }
