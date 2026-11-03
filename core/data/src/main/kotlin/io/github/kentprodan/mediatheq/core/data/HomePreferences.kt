package io.github.kentprodan.mediatheq.core.data

/** What a Home section lists. */
enum class HomeKind { Albums, Artists, Tracks, Playlists }

/** How a Home section is laid out (#127; chosen per section in #182). */
enum class HomeDisplay { Row, Grid, Carousel, List }

/** How one Home section looks; #182 lets the user change them, until then every section uses its defaults. */
data class HomeSectionSettings(
    val display: HomeDisplay = HomeDisplay.Row,
    /** Rows of a [HomeDisplay.Grid], 1-3. */
    val gridRows: Int = 2,
    val maxItems: Int = 16,
    val showHeader: Boolean = true,
    val moreIndicator: Boolean = true,
    val hideIfEmpty: Boolean = true,
)

/** The Home sections (#127), in their default order; [shownByDefault] rows make today's Home and more. */
enum class HomeSection(
    val kind: HomeKind,
    val shownByDefault: Boolean,
    val defaultSettings: HomeSectionSettings,
) {
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

/** Settings -> Interface -> Page settings -> Home page (#127): which sections show, in what order. */
data class HomePreferences(
    /** Every section, in the order Home shows them. */
    val order: List<HomeSection> = HomeSection.entries,
    val hidden: Set<HomeSection> = HomeSection.entries.filterNot { it.shownByDefault }.toSet(),
) {
    /** The sections Home shows, in order. */
    val shown: List<HomeSection> get() = order.filterNot { it in hidden }
}
