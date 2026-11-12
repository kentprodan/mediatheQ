package io.github.kentprodan.mediatheq.core.model

/** What a Home section lists. */
enum class HomeKind { Albums, Artists, Tracks, Playlists, Shortcuts }

/** How a Home section is laid out (#127; chosen per section in #182). */
enum class HomeDisplay { Row, Grid, Carousel, List }

/** How one Home section looks (#127); changed per section by its gear in Settings -> Home page (#182). */
data class HomeSectionSettings(
    val display: HomeDisplay = HomeDisplay.Row,
    /** Rows of a [HomeDisplay.Grid], [GRID_ROWS]. */
    val gridRows: Int = 2,
    /** [MAX_ITEMS]. */
    val maxItems: Int = 16,
    val showHeader: Boolean = true,
    val moreIndicator: Boolean = true,
    val hideIfEmpty: Boolean = true,
    /** Card text centered; `null` follows Settings -> Media lists (#123). */
    val centerText: Boolean? = null,
    /** Cards without their second line, tracks without their artists. */
    val hideSubtitles: Boolean = false,
    /** A play button on each cover: plays the album, playlist or artist without opening it. */
    val playButton: Boolean = false,
    /** The favorite and downloaded marks on covers; `null` follows Settings -> Media lists (#123). */
    val favoriteOverlay: Boolean? = null,
    val offlineOverlay: Boolean? = null,
) {
    /** Within the ranges a stored or copied value may have left. */
    fun clamped(): HomeSectionSettings =
        copy(gridRows = gridRows.coerceIn(GRID_ROWS), maxItems = maxItems.coerceIn(MAX_ITEMS))

    companion object {
        val GRID_ROWS = 1..3
        val MAX_ITEMS = 5..50
    }
}

/** The Home sections (#127), in their default order; [shownByDefault] rows make today's Home and more. */
enum class HomeSection(
    val kind: HomeKind,
    val shownByDefault: Boolean,
    val defaultSettings: HomeSectionSettings,
) {
    /** Buttons that start a mix (#128); its own settings come with the section gear (#182). */
    Shortcuts(HomeKind.Shortcuts, true, HomeSectionSettings(moreIndicator = false)),
    FavoritePlaylists(HomeKind.Playlists, true, HomeSectionSettings()),
    FavoriteAlbums(HomeKind.Albums, true, HomeSectionSettings()),
    FavoriteTracks(HomeKind.Tracks, true, TRACK_LIST),
    FavoriteArtists(HomeKind.Artists, true, HomeSectionSettings()),
    RecentlyAddedAlbums(HomeKind.Albums, true, HomeSectionSettings(display = HomeDisplay.Grid, gridRows = 2)),
    RediscoverAlbums(HomeKind.Albums, true, HomeSectionSettings(display = HomeDisplay.Carousel, maxItems = 9)),
    LastPlayedAlbums(HomeKind.Albums, true, HomeSectionSettings()),
    RandomArtists(HomeKind.Artists, true, HomeSectionSettings()),
    RandomAlbums(HomeKind.Albums, true, HomeSectionSettings()),
    ContinueAlbums(HomeKind.Albums, true, HomeSectionSettings()),
    MostPlayedTracks(HomeKind.Tracks, true, TRACK_LIST),
    RecentlyAddedTracks(HomeKind.Tracks, false, TRACK_LIST),
    RecentlyAddedArtists(HomeKind.Artists, false, HomeSectionSettings()),
    LastPlayedTracks(HomeKind.Tracks, false, TRACK_LIST),
    LastPlayedArtists(HomeKind.Artists, false, HomeSectionSettings()),
    MostPlayedAlbums(HomeKind.Albums, false, HomeSectionSettings()),
    MostPlayedArtists(HomeKind.Artists, false, HomeSectionSettings()),
    RandomTracks(HomeKind.Tracks, false, TRACK_LIST),
    RediscoverTracks(HomeKind.Tracks, false, TRACK_LIST),
    FiveStarTracks(HomeKind.Tracks, false, TRACK_LIST),
    ;

    /** Random sections draw a new set at each app start and pull to refresh. */
    val isRandom: Boolean get() = this == RandomArtists || this == RandomAlbums || this == RandomTracks
}

private val TRACK_LIST: HomeSectionSettings get() = HomeSectionSettings(display = HomeDisplay.List)
