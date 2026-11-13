package io.github.kentprodan.mediatheq.feature.library

/** The same items in the opposite order (#187); states without items stay as they are. */
internal fun LibraryContent.reversed(): LibraryContent =
    when (this) {
        is LibraryContent.Albums -> LibraryContent.Albums(albums.asReversed())
        is LibraryContent.Artists -> LibraryContent.Artists(artists.asReversed())
        is LibraryContent.Playlists -> LibraryContent.Playlists(playlists.asReversed())
        is LibraryContent.Songs -> LibraryContent.Songs(tracks.asReversed())
        is LibraryContent.Genres -> LibraryContent.Genres(genres.asReversed())
        is LibraryContent.RecordLabels -> LibraryContent.RecordLabels(labels.asReversed())
        is LibraryContent.Years -> LibraryContent.Years(years.asReversed())
        else -> this
    }
