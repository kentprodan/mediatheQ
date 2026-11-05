package io.github.kentprodan.mediatheq.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.model.LibraryShortcut

/** A Library tile's name, on the hub and on the Library page settings (#129). */
@get:StringRes
val LibraryShortcut.label: Int
    get() =
        when (this) {
            LibraryShortcut.Albums -> R.string.core_ui_library_albums
            LibraryShortcut.AlbumArtists -> R.string.core_ui_library_album_artists
            LibraryShortcut.Genres -> R.string.core_ui_library_genres
            LibraryShortcut.Tracks -> R.string.core_ui_library_tracks
            LibraryShortcut.Playlists -> R.string.core_ui_library_playlists
            LibraryShortcut.Years -> R.string.core_ui_library_years
        }

/** A Library tile's icon. */
val LibraryShortcut.icon: ImageVector
    @Composable get() =
        when (this) {
            LibraryShortcut.Albums -> MqIcons.Library
            LibraryShortcut.AlbumArtists -> MqIcons.Person
            LibraryShortcut.Genres -> MqIcons.Genres
            LibraryShortcut.Tracks -> MqIcons.MusicNote
            LibraryShortcut.Playlists -> MqIcons.QueueMusic
            LibraryShortcut.Years -> MqIcons.CalendarMonth
        }
