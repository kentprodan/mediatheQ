package io.github.kentprodan.mediatheq.core.model

/** A tile of the Library hub (#129), in its default order. */
enum class LibraryShortcut { Albums, AlbumArtists, Genres, Tracks, Playlists, Years, RecordLabels, Movies, Series }

/** Shown in [mode] (ADR 0051): Movies and Series with video, the other tiles with music. */
fun LibraryShortcut.allowed(mode: AppMode): Boolean =
    if (this == LibraryShortcut.Movies || this == LibraryShortcut.Series) mode.video else mode.music
