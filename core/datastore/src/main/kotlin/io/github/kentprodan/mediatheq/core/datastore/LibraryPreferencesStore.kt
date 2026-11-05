package io.github.kentprodan.mediatheq.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Raw Library preferences: names and numbers exactly as stored. `:core:data` validates them, so an old or unknown value
 * never reaches the UI. They survive sign-out (only the account is cleared).
 */
@Singleton
class LibraryPreferencesStore
    @Inject
    constructor(
        @param:LibraryPrefs private val dataStore: DataStore<Preferences>,
    ) {
        private val data = dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }

        /** Stored values; empty preferences when the file cannot be read. */
        val values: Flow<StoredLibraryPreferences> =
            data
                .map { prefs ->
                    StoredLibraryPreferences(
                        albumColumns = prefs[ALBUM_COLUMNS],
                        genresGrid = prefs[GENRES_GRID],
                        dynamicColor = prefs[DYNAMIC_COLOR],
                        theme =
                            StoredTheme(
                                mode = prefs[THEME_MODE],
                                colorSource = prefs[COLOR_SOURCE],
                                customSeed = prefs[CUSTOM_SEED],
                                nowPlayingColors = prefs[NOW_PLAYING_COLORS],
                                ignoreSurfaces = prefs[IGNORE_SURFACES],
                                instantColors = prefs[INSTANT_COLORS],
                                font = prefs[FONT],
                            ),
                        ratingMode = prefs[RATING_MODE],
                        navigation =
                            StoredNavigation(
                                entries = prefs[NAV_ENTRIES]?.split(LIST_SEPARATOR),
                                portraitStyle = prefs[NAV_PORTRAIT_STYLE],
                                landscapeStyle = prefs[NAV_LANDSCAPE_STYLE],
                                transparencyPercent = prefs[NAV_TRANSPARENCY],
                                slowStartMessages = prefs[SLOW_START_MESSAGES],
                                trackAction = prefs[TRACK_ACTION],
                                expandPlayer = prefs[EXPAND_PLAYER],
                            ),
                        mediaLists =
                            StoredMediaLists(
                                showItemCounts = prefs[ML_ITEM_COUNTS],
                                topButtonStyle = prefs[ML_TOP_BUTTON_STYLE],
                                hiddenTopButtons =
                                    prefs[ML_HIDDEN_TOP_BUTTONS]?.split(LIST_SEPARATOR)?.filter { it.isNotEmpty() },
                                invertedSortArrows = prefs[ML_INVERTED_SORT_ARROWS],
                                hideBackButton = prefs[ML_HIDE_BACK],
                                trackInfo = prefs[ML_TRACK_INFO],
                                secondTrackInfo = prefs[ML_SECOND_TRACK_INFO],
                                centerGridText = prefs[ML_CENTER_GRID_TEXT],
                                favoriteOverlay = prefs[ML_FAVORITE_OVERLAY],
                                offlineOverlay = prefs[ML_OFFLINE_OVERLAY],
                                swipeRight = prefs[ML_SWIPE_RIGHT],
                                swipeLeft = prefs[ML_SWIPE_LEFT],
                            ),
                        nowPlaying =
                            StoredNowPlaying(
                                floatingMiniPlayer = prefs[NP_FLOATING],
                                miniSwipeToSkip = prefs[NP_MINI_SWIPE],
                                miniProgress = prefs[NP_MINI_PROGRESS],
                                background = prefs[NP_BACKGROUND],
                                progress = prefs[NP_PROGRESS],
                                fallback = prefs[NP_FALLBACK],
                                showRating = prefs[NP_SHOW_RATING],
                                coverTap = prefs[NP_COVER_TAP],
                                coverSwipe = prefs[NP_COVER_SWIPE],
                                playButtonWhenPaused = prefs[NP_PLAY_BUTTON],
                            ),
                        home =
                            StoredHome(
                                order = prefs[HOME_ORDER]?.split(LIST_SEPARATOR)?.filter { it.isNotEmpty() },
                                hidden = prefs[HOME_HIDDEN]?.split(LIST_SEPARATOR)?.filter { it.isNotEmpty() },
                            ),
                        libraryPage =
                            StoredLibraryPage(
                                shortcuts = prefs[LP_SHORTCUTS]?.split(LIST_SEPARATOR)?.filter { it.isNotEmpty() },
                                style = prefs[LP_STYLE],
                                columns = prefs[LP_COLUMNS],
                                landscapeColumns = prefs[LP_LANDSCAPE_COLUMNS],
                                spacing = prefs[LP_SPACING],
                            ),
                        albumPage =
                            StoredAlbumPage(
                                headerBackground = prefs[AP_BACKGROUND],
                                showQuality = prefs[AP_QUALITY],
                                showRatingBar = prefs[AP_RATING],
                                hiddenButtons =
                                    prefs[AP_HIDDEN_BUTTONS]
                                        ?.split(
                                            LIST_SEPARATOR,
                                        )?.filter { it.isNotEmpty() },
                                sectionOrder =
                                    prefs[AP_SECTIONS]
                                        ?.split(
                                            LIST_SEPARATOR,
                                        )?.filter { it.isNotEmpty() },
                                showMoreFromArtist = prefs[AP_MORE],
                                moreAlbumsSort = prefs[AP_MORE_SORT],
                                alwaysShowTrackArtists = prefs[AP_TRACK_ARTISTS],
                                showTrackNumbers = prefs[AP_TRACK_NUMBERS],
                                showTrackThumbnails = prefs[AP_TRACK_THUMBNAILS],
                                multilineTrackTitles = prefs[AP_MULTILINE],
                                showAbout = prefs[AP_ABOUT],
                            ),
                        artistPage =
                            StoredArtistPage(
                                headerBackground = prefs[ARP_BACKGROUND],
                                hiddenButtons =
                                    prefs[ARP_HIDDEN_BUTTONS]
                                        ?.split(
                                            LIST_SEPARATOR,
                                        )?.filter { it.isNotEmpty() },
                                playAction = prefs[ARP_PLAY],
                                sectionOrder = prefs[ARP_SECTIONS]?.split(LIST_SEPARATOR)?.filter { it.isNotEmpty() },
                                albumsSort = prefs[ARP_SORT],
                                albumColumns = prefs[ARP_COLUMNS],
                                landscapeAlbumColumns = prefs[ARP_LANDSCAPE_COLUMNS],
                                showAppearsOn = prefs[ARP_APPEARS_ON],
                                showTopTracks = prefs[ARP_TOP_TRACKS],
                                topTracksCount = prefs[ARP_TOP_TRACKS_COUNT],
                                showAbout = prefs[ARP_ABOUT],
                                dynamicColors = prefs[ARP_DYNAMIC_COLORS],
                            ),
                        offlineMode = prefs[OFFLINE_MODE],
                        qualityDetail = prefs[QUALITY_DETAIL],
                        sorts =
                            prefs
                                .asMap()
                                .filterKeys { it.name.startsWith(SORT_PREFIX) }
                                .map { (key, value) -> key.name.removePrefix(SORT_PREFIX) to value.toString() }
                                .toMap(),
                    )
                }

        suspend fun setAlbumColumns(columns: Int) {
            dataStore.edit { it[ALBUM_COLUMNS] = columns }
        }

        suspend fun setGenresGrid(on: Boolean) {
            dataStore.edit { it[GENRES_GRID] = on }
        }

        /** Stores every theme value at once (null removes it); the old Material You flag is dropped with it. */
        suspend fun setTheme(theme: StoredTheme) {
            dataStore.edit { prefs ->
                prefs.remove(DYNAMIC_COLOR)
                prefs.put(THEME_MODE, theme.mode)
                prefs.put(COLOR_SOURCE, theme.colorSource)
                prefs.put(CUSTOM_SEED, theme.customSeed)
                prefs.put(NOW_PLAYING_COLORS, theme.nowPlayingColors)
                prefs.put(IGNORE_SURFACES, theme.ignoreSurfaces)
                prefs.put(INSTANT_COLORS, theme.instantColors)
                prefs.put(FONT, theme.font)
            }
        }

        /** Stores every navigation and media start value at once (#122); null removes it. */
        suspend fun setNavigation(navigation: StoredNavigation) {
            dataStore.edit { prefs ->
                prefs.put(NAV_ENTRIES, navigation.entries?.joinToString(LIST_SEPARATOR))
                prefs.put(NAV_PORTRAIT_STYLE, navigation.portraitStyle)
                prefs.put(NAV_LANDSCAPE_STYLE, navigation.landscapeStyle)
                prefs.put(NAV_TRANSPARENCY, navigation.transparencyPercent)
                prefs.put(SLOW_START_MESSAGES, navigation.slowStartMessages)
                prefs.put(TRACK_ACTION, navigation.trackAction)
                prefs.put(EXPAND_PLAYER, navigation.expandPlayer)
            }
        }

        /** Stores every Media lists value at once (#123); null removes it. */
        suspend fun setMediaLists(mediaLists: StoredMediaLists) {
            dataStore.edit { prefs ->
                prefs.put(ML_ITEM_COUNTS, mediaLists.showItemCounts)
                prefs.put(ML_TOP_BUTTON_STYLE, mediaLists.topButtonStyle)
                // None hidden is stored as an empty string, not removed: it is a choice, like any other.
                prefs.put(ML_HIDDEN_TOP_BUTTONS, mediaLists.hiddenTopButtons?.joinToString(LIST_SEPARATOR))
                prefs.put(ML_INVERTED_SORT_ARROWS, mediaLists.invertedSortArrows)
                prefs.put(ML_HIDE_BACK, mediaLists.hideBackButton)
                prefs.put(ML_TRACK_INFO, mediaLists.trackInfo)
                prefs.put(ML_SECOND_TRACK_INFO, mediaLists.secondTrackInfo)
                prefs.put(ML_CENTER_GRID_TEXT, mediaLists.centerGridText)
                prefs.put(ML_FAVORITE_OVERLAY, mediaLists.favoriteOverlay)
                prefs.put(ML_OFFLINE_OVERLAY, mediaLists.offlineOverlay)
                prefs.put(ML_SWIPE_RIGHT, mediaLists.swipeRight)
                prefs.put(ML_SWIPE_LEFT, mediaLists.swipeLeft)
            }
        }

        suspend fun setHomeOrder(names: List<String>) {
            dataStore.edit { it[HOME_ORDER] = names.joinToString(LIST_SEPARATOR) }
        }

        /** Nothing hidden is stored as an empty string, not removed: it is a choice, like any other. */
        suspend fun setHomeHidden(names: List<String>) {
            dataStore.edit { it[HOME_HIDDEN] = names.joinToString(LIST_SEPARATOR) }
        }

        /** Stores Home's rows at once (#127); null removes a value (back to default). */
        suspend fun setHome(home: StoredHome) {
            dataStore.edit { prefs ->
                prefs.put(HOME_ORDER, home.order?.joinToString(LIST_SEPARATOR))
                prefs.put(HOME_HIDDEN, home.hidden?.joinToString(LIST_SEPARATOR))
            }
        }

        /** Stores every Library page value at once (#129); null removes it. */
        suspend fun setLibraryPage(page: StoredLibraryPage) {
            dataStore.edit { prefs ->
                // The filter style of the old tabbed Library (ADR 0014) goes with it.
                prefs.remove(OLD_FILTER_STYLE)
                prefs.put(LP_SHORTCUTS, page.shortcuts?.joinToString(LIST_SEPARATOR))
                prefs.put(LP_STYLE, page.style)
                prefs.put(LP_COLUMNS, page.columns)
                prefs.put(LP_LANDSCAPE_COLUMNS, page.landscapeColumns)
                prefs.put(LP_SPACING, page.spacing)
            }
        }

        /** Stores every Album page value at once (#132); null removes it. */
        suspend fun setAlbumPage(page: StoredAlbumPage) {
            dataStore.edit { prefs ->
                prefs.put(AP_BACKGROUND, page.headerBackground)
                prefs.put(AP_QUALITY, page.showQuality)
                prefs.put(AP_RATING, page.showRatingBar)
                // An empty set is stored as an empty string: "none hidden" differs from "not set".
                prefs.put(AP_HIDDEN_BUTTONS, page.hiddenButtons?.joinToString(LIST_SEPARATOR))
                prefs.put(AP_SECTIONS, page.sectionOrder?.joinToString(LIST_SEPARATOR))
                prefs.put(AP_MORE, page.showMoreFromArtist)
                prefs.put(AP_MORE_SORT, page.moreAlbumsSort)
                prefs.put(AP_TRACK_ARTISTS, page.alwaysShowTrackArtists)
                prefs.put(AP_TRACK_NUMBERS, page.showTrackNumbers)
                prefs.put(AP_TRACK_THUMBNAILS, page.showTrackThumbnails)
                prefs.put(AP_MULTILINE, page.multilineTrackTitles)
                prefs.put(AP_ABOUT, page.showAbout)
            }
        }

        /** Stores every Artist page value at once (#133); null removes it. */
        suspend fun setArtistPage(page: StoredArtistPage) {
            dataStore.edit { prefs ->
                prefs.put(ARP_BACKGROUND, page.headerBackground)
                // An empty set is stored as an empty string: "none hidden" differs from "not set".
                prefs.put(ARP_HIDDEN_BUTTONS, page.hiddenButtons?.joinToString(LIST_SEPARATOR))
                prefs.put(ARP_PLAY, page.playAction)
                prefs.put(ARP_SECTIONS, page.sectionOrder?.joinToString(LIST_SEPARATOR))
                prefs.put(ARP_SORT, page.albumsSort)
                prefs.put(ARP_COLUMNS, page.albumColumns)
                prefs.put(ARP_LANDSCAPE_COLUMNS, page.landscapeAlbumColumns)
                prefs.put(ARP_APPEARS_ON, page.showAppearsOn)
                prefs.put(ARP_TOP_TRACKS, page.showTopTracks)
                prefs.put(ARP_TOP_TRACKS_COUNT, page.topTracksCount)
                prefs.put(ARP_ABOUT, page.showAbout)
                prefs.put(ARP_DYNAMIC_COLORS, page.dynamicColors)
            }
        }

        /** Stores every Now playing screens value at once (#126); null removes it. */
        suspend fun setNowPlaying(nowPlaying: StoredNowPlaying) {
            dataStore.edit { prefs ->
                prefs.put(NP_FLOATING, nowPlaying.floatingMiniPlayer)
                prefs.put(NP_MINI_SWIPE, nowPlaying.miniSwipeToSkip)
                prefs.put(NP_MINI_PROGRESS, nowPlaying.miniProgress)
                prefs.put(NP_BACKGROUND, nowPlaying.background)
                prefs.put(NP_PROGRESS, nowPlaying.progress)
                prefs.put(NP_FALLBACK, nowPlaying.fallback)
                prefs.put(NP_SHOW_RATING, nowPlaying.showRating)
                prefs.put(NP_COVER_TAP, nowPlaying.coverTap)
                prefs.put(NP_COVER_SWIPE, nowPlaying.coverSwipe)
                prefs.put(NP_PLAY_BUTTON, nowPlaying.playButtonWhenPaused)
            }
        }

        private fun <T> MutablePreferences.put(
            key: Preferences.Key<T>,
            value: T?,
        ) {
            if (value == null) remove(key) else set(key, value)
        }

        suspend fun setRatingMode(name: String) {
            dataStore.edit { it[RATING_MODE] = name }
        }

        suspend fun setQualityDetail(on: Boolean) {
            dataStore.edit { it[QUALITY_DETAIL] = on }
        }

        suspend fun setOfflineMode(on: Boolean) {
            dataStore.edit { it[OFFLINE_MODE] = on }
        }

        /** Stores the sort [name] for the Library tab [tab]. */
        suspend fun setSort(
            tab: String,
            name: String,
        ) {
            dataStore.edit { it[stringPreferencesKey(SORT_PREFIX + tab)] = name }
        }

        /** Recent searches in stored order; `:core:data` decides the order and the limit. */
        val recentSearches: Flow<List<String>> = data.map { it[RECENT_SEARCHES].toList() }

        /**
         * Replaces the recent searches with [transform] of the stored ones, atomically. Best effort: a failed write is
         * dropped, since a lost recent search is not worth crashing a tap.
         */
        suspend fun updateRecentSearches(transform: (List<String>) -> List<String>) {
            try {
                dataStore.edit { prefs ->
                    val searches = transform(prefs[RECENT_SEARCHES].toList())
                    if (searches.isEmpty()) {
                        prefs.remove(RECENT_SEARCHES)
                    } else {
                        prefs[RECENT_SEARCHES] = searches.joinToString(SEPARATOR)
                    }
                }
            } catch (_: IOException) {
            }
        }

        // One string, one search per line: callers keep searches on a single line.
        private fun String?.toList() = this?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

        private companion object {
            val OLD_FILTER_STYLE = stringPreferencesKey("filter_style")
            val LP_SHORTCUTS = stringPreferencesKey("lp_shortcuts")
            val LP_STYLE = stringPreferencesKey("lp_style")
            val LP_COLUMNS = intPreferencesKey("lp_columns")
            val LP_LANDSCAPE_COLUMNS = intPreferencesKey("lp_landscape_columns")
            val LP_SPACING = stringPreferencesKey("lp_spacing")
            val AP_BACKGROUND = stringPreferencesKey("ap_background")
            val AP_QUALITY = booleanPreferencesKey("ap_quality")
            val AP_RATING = booleanPreferencesKey("ap_rating")
            val AP_HIDDEN_BUTTONS = stringPreferencesKey("ap_hidden_buttons")
            val AP_SECTIONS = stringPreferencesKey("ap_sections")
            val AP_MORE = booleanPreferencesKey("ap_more")
            val AP_MORE_SORT = stringPreferencesKey("ap_more_sort")
            val AP_TRACK_ARTISTS = booleanPreferencesKey("ap_track_artists")
            val AP_TRACK_NUMBERS = booleanPreferencesKey("ap_track_numbers")
            val AP_TRACK_THUMBNAILS = booleanPreferencesKey("ap_track_thumbnails")
            val AP_MULTILINE = booleanPreferencesKey("ap_multiline")
            val AP_ABOUT = booleanPreferencesKey("ap_about")
            val ARP_BACKGROUND = stringPreferencesKey("arp_background")
            val ARP_HIDDEN_BUTTONS = stringPreferencesKey("arp_hidden_buttons")
            val ARP_PLAY = stringPreferencesKey("arp_play")
            val ARP_SECTIONS = stringPreferencesKey("arp_sections")
            val ARP_SORT = stringPreferencesKey("arp_sort")
            val ARP_COLUMNS = intPreferencesKey("arp_columns")
            val ARP_LANDSCAPE_COLUMNS = intPreferencesKey("arp_landscape_columns")
            val ARP_APPEARS_ON = booleanPreferencesKey("arp_appears_on")
            val ARP_TOP_TRACKS = booleanPreferencesKey("arp_top_tracks")
            val ARP_TOP_TRACKS_COUNT = intPreferencesKey("arp_top_tracks_count")
            val ARP_ABOUT = booleanPreferencesKey("arp_about")
            val ARP_DYNAMIC_COLORS = booleanPreferencesKey("arp_dynamic_colors")
            val ALBUM_COLUMNS = intPreferencesKey("album_columns")
            val GENRES_GRID = booleanPreferencesKey("genres_grid")
            val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
            val RATING_MODE = stringPreferencesKey("rating_mode")
            val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
            val QUALITY_DETAIL = booleanPreferencesKey("np_quality_detail")
            val THEME_MODE = stringPreferencesKey("theme_mode")
            val COLOR_SOURCE = stringPreferencesKey("color_source")
            val CUSTOM_SEED = intPreferencesKey("custom_seed")
            val NOW_PLAYING_COLORS = stringPreferencesKey("now_playing_colors")
            val IGNORE_SURFACES = booleanPreferencesKey("ignore_surfaces")
            val INSTANT_COLORS = booleanPreferencesKey("instant_colors")
            val FONT = stringPreferencesKey("font")
            val NAV_ENTRIES = stringPreferencesKey("nav_entries")
            val NAV_PORTRAIT_STYLE = stringPreferencesKey("nav_portrait_style")
            val NAV_LANDSCAPE_STYLE = stringPreferencesKey("nav_landscape_style")
            val NAV_TRANSPARENCY = intPreferencesKey("nav_transparency")
            val SLOW_START_MESSAGES = booleanPreferencesKey("slow_start_messages")
            val TRACK_ACTION = stringPreferencesKey("track_action")
            val EXPAND_PLAYER = booleanPreferencesKey("expand_player")
            val ML_ITEM_COUNTS = booleanPreferencesKey("ml_item_counts")
            val ML_TOP_BUTTON_STYLE = stringPreferencesKey("ml_top_button_style")
            val ML_HIDDEN_TOP_BUTTONS = stringPreferencesKey("ml_hidden_top_buttons")
            val ML_INVERTED_SORT_ARROWS = booleanPreferencesKey("ml_inverted_sort_arrows")
            val ML_HIDE_BACK = booleanPreferencesKey("ml_hide_back")
            val ML_TRACK_INFO = stringPreferencesKey("ml_track_info")
            val ML_SECOND_TRACK_INFO = stringPreferencesKey("ml_second_track_info")
            val ML_CENTER_GRID_TEXT = booleanPreferencesKey("ml_center_grid_text")
            val ML_FAVORITE_OVERLAY = booleanPreferencesKey("ml_favorite_overlay")
            val ML_OFFLINE_OVERLAY = booleanPreferencesKey("ml_offline_overlay")
            val ML_SWIPE_RIGHT = stringPreferencesKey("ml_swipe_right")
            val ML_SWIPE_LEFT = stringPreferencesKey("ml_swipe_left")
            val NP_FLOATING = booleanPreferencesKey("np_floating_mini_player")
            val HOME_ORDER = stringPreferencesKey("home_order")
            val HOME_HIDDEN = stringPreferencesKey("home_hidden")
            val NP_MINI_SWIPE = booleanPreferencesKey("np_mini_swipe_to_skip")
            val NP_MINI_PROGRESS = stringPreferencesKey("np_mini_progress")
            val NP_BACKGROUND = stringPreferencesKey("np_background")
            val NP_PROGRESS = stringPreferencesKey("np_progress")
            val NP_FALLBACK = stringPreferencesKey("np_fallback")
            val NP_SHOW_RATING = booleanPreferencesKey("np_show_rating")
            val NP_COVER_TAP = stringPreferencesKey("np_cover_tap")
            val NP_COVER_SWIPE = booleanPreferencesKey("np_cover_swipe")
            val NP_PLAY_BUTTON = booleanPreferencesKey("np_play_button_when_paused")
            const val LIST_SEPARATOR = ","
            const val SORT_PREFIX = "sort_"
            val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
            const val SEPARATOR = "\n"
        }
    }

/** Library preferences as stored; any field may be missing or out of date. */
data class StoredLibraryPreferences(
    val albumColumns: Int? = null,
    val genresGrid: Boolean? = null,
    /** Tab name -> sort name. */
    val sorts: Map<String, String> = emptyMap(),
    val dynamicColor: Boolean? = null,
    val ratingMode: String? = null,
    val offlineMode: Boolean? = null,
    val theme: StoredTheme = StoredTheme(),
    val navigation: StoredNavigation = StoredNavigation(),
    val mediaLists: StoredMediaLists = StoredMediaLists(),
    val nowPlaying: StoredNowPlaying = StoredNowPlaying(),
    val home: StoredHome = StoredHome(),
    val libraryPage: StoredLibraryPage = StoredLibraryPage(),
    val albumPage: StoredAlbumPage = StoredAlbumPage(),
    val artistPage: StoredArtistPage = StoredArtistPage(),
    val qualityDetail: Boolean? = null,
)

/** Album page values as stored (#132); `null` = not set. */
data class StoredAlbumPage(
    val headerBackground: String? = null,
    val showQuality: Boolean? = null,
    val showRatingBar: Boolean? = null,
    val hiddenButtons: List<String>? = null,
    val sectionOrder: List<String>? = null,
    val showMoreFromArtist: Boolean? = null,
    val moreAlbumsSort: String? = null,
    val alwaysShowTrackArtists: Boolean? = null,
    val showTrackNumbers: Boolean? = null,
    val showTrackThumbnails: Boolean? = null,
    val multilineTrackTitles: Boolean? = null,
    val showAbout: Boolean? = null,
)

/** Artist page values as stored (#133); `null` = not set. */
data class StoredArtistPage(
    val headerBackground: String? = null,
    val hiddenButtons: List<String>? = null,
    val playAction: String? = null,
    val sectionOrder: List<String>? = null,
    val albumsSort: String? = null,
    val albumColumns: Int? = null,
    val landscapeAlbumColumns: Int? = null,
    val showAppearsOn: Boolean? = null,
    val showTopTracks: Boolean? = null,
    val topTracksCount: Int? = null,
    val showAbout: Boolean? = null,
    val dynamicColors: Boolean? = null,
)

/** Library page values as stored (#129); `null` = not set. */
data class StoredLibraryPage(
    /** Tile names in order. */
    val shortcuts: List<String>? = null,
    val style: String? = null,
    val columns: Int? = null,
    val landscapeColumns: Int? = null,
    val spacing: String? = null,
)

/** Home's rows as stored (#127): section names; `null` = not set (defaults). */
data class StoredHome(
    val order: List<String>? = null,
    val hidden: List<String>? = null,
)

/** Now playing screens values as stored (#126); `null` = not set. */
data class StoredNowPlaying(
    val floatingMiniPlayer: Boolean? = null,
    val miniSwipeToSkip: Boolean? = null,
    val miniProgress: String? = null,
    val background: String? = null,
    val progress: String? = null,
    val fallback: String? = null,
    val showRating: Boolean? = null,
    val coverTap: String? = null,
    val coverSwipe: Boolean? = null,
    val playButtonWhenPaused: Boolean? = null,
)

/** Media lists values as stored (#123); `null` = not set. */
data class StoredMediaLists(
    val showItemCounts: Boolean? = null,
    val topButtonStyle: String? = null,
    /** Button names; empty = none hidden. */
    val hiddenTopButtons: List<String>? = null,
    val invertedSortArrows: Boolean? = null,
    val hideBackButton: Boolean? = null,
    val trackInfo: String? = null,
    val secondTrackInfo: String? = null,
    val centerGridText: Boolean? = null,
    val favoriteOverlay: Boolean? = null,
    val offlineOverlay: Boolean? = null,
    val swipeRight: String? = null,
    val swipeLeft: String? = null,
)

/** Navigation and media start values as stored (#122); `null` = not set. */
data class StoredNavigation(
    /** Entry names in order. */
    val entries: List<String>? = null,
    val portraitStyle: String? = null,
    val landscapeStyle: String? = null,
    val transparencyPercent: Int? = null,
    val slowStartMessages: Boolean? = null,
    val trackAction: String? = null,
    val expandPlayer: Boolean? = null,
)

/** Theme, font and color values as stored (#121); `null` = not set. */
data class StoredTheme(
    val mode: String? = null,
    val colorSource: String? = null,
    val customSeed: Int? = null,
    val nowPlayingColors: String? = null,
    val ignoreSurfaces: Boolean? = null,
    val instantColors: Boolean? = null,
    val font: String? = null,
)
