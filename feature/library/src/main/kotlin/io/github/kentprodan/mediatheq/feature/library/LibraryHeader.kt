package io.github.kentprodan.mediatheq.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import io.github.kentprodan.mediatheq.core.designsystem.motion.mqBackButtonAboveCovers
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.ui.MqMenuItem
import io.github.kentprodan.mediatheq.core.ui.OverflowMenu
import io.github.kentprodan.mediatheq.core.ui.title

/**
 * The Library list header (#123, #535): the title bar (back when [onBack] is set and not hidden, icon, title, then New
 * playlist, view, select and search in list as icons), the top buttons acting on the whole list (Play filled, the rest
 * tonal, hidden ones in More), and a chip row: sort (a tap opens its menu) with its direction arrow, the Favorites and
 * Downloaded filters, and the item count.
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
                IconButton(onClick = onBack, modifier = Modifier.mqBackButtonAboveCovers()) {
                    Icon(MqIcons.ArrowBack, contentDescription = stringResource(R.string.library_back))
                }
            }
            Icon(state.titleIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                state.title,
                Modifier.weight(1f).padding(horizontal = MediatheqSpacing.m).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.filter == LibraryFilter.Playlists && state.sectionPage == null) {
                IconButton(onClick = onNewPlaylist) {
                    Icon(MqIcons.Add, contentDescription = stringResource(R.string.library_new_playlist))
                }
            }
            BarTools(state, onAction, onSelect)
        }
        TopButtons(header.topButtonStyle, header.hiddenTopButtons, state.itemCount > 0, onAction)
        ChipRow(state, onAction)
    }
}

/** Years, genres and labels are no items of their own: nothing to filter, select or search (#129-#131). */
private val LibraryUiState.itemTools: Boolean
    get() = filter != LibraryFilter.Years && filter != LibraryFilter.Genres && filter != LibraryFilter.RecordLabels

/** View (album columns, genres grid or rows), select and search in list, in the title bar (#535). */
@Composable
private fun BarTools(
    state: LibraryUiState,
    onAction: (LibraryAction) -> Unit,
    onSelect: (() -> Unit)?,
) {
    var viewMenu by rememberSaveable { mutableStateOf(false) }
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
    if (onSelect != null && state.itemTools) {
        IconButton(onClick = onSelect, enabled = state.itemCount > 0) {
            Icon(MqIcons.Checklist, contentDescription = stringResource(R.string.library_select))
        }
    }
    if (state.itemTools) {
        IconButton(onClick = { onAction(LibraryAction.Search(if (state.listFilter.search == null) "" else null)) }) {
            Icon(MqIcons.Search, contentDescription = stringResource(R.string.library_search_in_list))
        }
    }
}

/** Play (filled) and the other shown buttons (tonal), in the chosen style; hidden ones sit in More (#535). */
@Composable
private fun TopButtons(
    style: TopButtonStyle,
    hidden: Set<TopButton>,
    enabled: Boolean,
    onAction: (LibraryAction) -> Unit,
) {
    if (style == TopButtonStyle.Hidden) return
    val shown = TopButton.entries - hidden
    // Scrolls rather than clipping labels at large font sizes.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = MediatheqSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shown.forEach { button -> TopButtonView(button, style, enabled) { onAction(LibraryAction.Top(button)) } }
        if (hidden.isNotEmpty() && enabled) {
            OverflowMenu(stringResource(R.string.library_more_actions)) { close ->
                hidden.sortedBy { it.ordinal }.forEach { button ->
                    MqMenuItem(
                        text = { Text(stringResource(button.label)) },
                        leadingIcon = { Icon(button.icon, contentDescription = null) },
                        onClick = {
                            close()
                            onAction(LibraryAction.Top(button))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TopButtonView(
    button: TopButton,
    style: TopButtonStyle,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(button.label)
    val main = button == TopButton.Play
    when (style) {
        TopButtonStyle.Normal -> {
            val content: @Composable RowScope.() -> Unit = {
                Icon(button.icon, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                Text(label, Modifier.padding(start = ButtonDefaults.IconSpacing))
            }
            val padding = ButtonDefaults.ButtonWithIconContentPadding
            if (main) {
                Button(onClick, enabled = enabled, contentPadding = padding, content = content)
            } else {
                FilledTonalButton(onClick, enabled = enabled, contentPadding = padding, content = content)
            }
        }

        else -> {
            val large = style == TopButtonStyle.LargeIcons
            val modifier = if (large) Modifier.size(IconButtonDefaults.mediumContainerSize()) else Modifier
            val icon: @Composable () -> Unit = {
                Icon(button.icon, label, if (large) Modifier.size(IconButtonDefaults.mediumIconSize) else Modifier)
            }
            if (main) {
                FilledIconButton(onClick, modifier, enabled = enabled, content = icon)
            } else {
                FilledTonalIconButton(onClick, modifier, enabled = enabled, content = icon)
            }
        }
    }
}

/** Sort and its direction, the filters as chips (#535), then the item count. Scrolls at large font sizes. */
@Composable
private fun ChipRow(
    state: LibraryUiState,
    onAction: (LibraryAction) -> Unit,
) {
    val listFilter = state.listFilter
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A Home section's list keeps the section's order (#183): no sort.
        if (state.sectionPage == null) SortChip(state, onAction)
        if (state.itemTools) {
            FilterToggleChip(stringResource(R.string.library_favorites_only), listFilter.favoritesOnly) {
                onAction(LibraryAction.SetFavoritesOnly(!listFilter.favoritesOnly))
            }
            // Artists have no download state of their own.
            if (state.filter != LibraryFilter.Artists) {
                FilterToggleChip(stringResource(R.string.library_downloaded_only), listFilter.downloadedOnly) {
                    onAction(LibraryAction.SetDownloadedOnly(!listFilter.downloadedOnly))
                }
            }
        }
        if (state.header.showItemCounts && state.itemCount > 0) {
            Text(
                pluralStringResource(R.plurals.library_items, state.itemCount, state.itemCount),
                Modifier.padding(start = MediatheqSpacing.xs),
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** An on/off filter: a check while on (not color alone). */
@Composable
private fun FilterToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon =
            if (selected) {
                { Icon(MqIcons.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
            } else {
                null
            },
    )
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

/** The sort as a chip that opens its menu, then the separate direction arrow (#187). */
@Composable
private fun SortChip(
    state: LibraryUiState,
    onAction: (LibraryAction) -> Unit,
) {
    var sortMenu by rememberSaveable { mutableStateOf(false) }
    // The real direction (#187); Inverted sort arrows only changes how the arrow is drawn.
    val ascending = state.sort.ascending != state.sortReversed
    val direction =
        stringResource(if (ascending) R.string.library_sort_ascending else R.string.library_sort_descending)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            FilterChip(
                selected = false,
                onClick = { sortMenu = true },
                label = { Text(stringResource(state.sort.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = {
                    Icon(
                        MqIcons.KeyboardArrowDown,
                        contentDescription = null,
                        Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
                enabled = state.filter.sorts.size > 1,
                modifier =
                    Modifier.semantics {
                        role = Role.DropdownList
                        stateDescription = direction
                    },
            )
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                state.filter.sorts.forEach { sort ->
                    // The sort already chosen turns around (owner, #187).
                    CheckedMenuItem(stringResource(sort.label), checked = sort == state.sort) {
                        onAction(if (sort == state.sort) LibraryAction.ReverseSort else LibraryAction.SelectSort(sort))
                        sortMenu = false
                    }
                }
            }
        }
        IconButton(
            onClick = { onAction(LibraryAction.ReverseSort) },
            Modifier.semantics { stateDescription = direction },
        ) {
            Icon(
                if (ascending != state.header.invertedSortArrows) MqIcons.ArrowUpward else MqIcons.ArrowDownward,
                contentDescription = stringResource(R.string.library_reverse_sort),
            )
        }
    }
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
            ?: sectionPage?.let { stringResource(it.section.title) }
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
