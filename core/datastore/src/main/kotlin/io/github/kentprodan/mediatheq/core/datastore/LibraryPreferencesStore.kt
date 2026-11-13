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
                .map(::read)

        /** What [prefs] hold; also run on a restored backup before it is written (#150). */
        internal fun read(prefs: Preferences) =
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
                appMode = prefs[APP_MODE],
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
                        sections =
                            prefs
                                .asMap()
                                .filterKeys { it.name.startsWith(HOME_SECTION_PREFIX) }
                                .map { (key, value) -> key.name.removePrefix(HOME_SECTION_PREFIX) to value.toString() }
                                .toMap(),
                    ),
                libraryPage =
                    StoredLibraryPage(
                        shortcuts = prefs[LP_SHORTCUTS]?.split(LIST_SEPARATOR)?.filter { it.isNotEmpty() },
                        style = prefs[LP_STYLE],
                        columns = prefs[LP_COLUMNS],
                        landscapeColumns = prefs[LP_LANDSCAPE_COLUMNS],
                        spacing = prefs[LP_SPACING],
                        knownShortcuts = prefs[LP_KNOWN_SHORTCUTS]?.split(LIST_SEPARATOR)?.filter { it.isNotEmpty() },
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
                genrePage =
                    StoredGenrePage(
                        headerBackground = prefs[GP_BACKGROUND],
                        hiddenButtons =
                            prefs[GP_HIDDEN_BUTTONS]
                                ?.split(
                                    LIST_SEPARATOR,
                                )?.filter { it.isNotEmpty() },
                        sectionOrder =
                            prefs[GP_SECTIONS]
                                ?.split(
                                    LIST_SEPARATOR,
                                )?.filter { it.isNotEmpty() },
                        showAlbums = prefs[GP_ALBUMS],
                        showArtists = prefs[GP_ARTISTS],
                        showAlbumArtists = prefs[GP_ALBUM_ARTISTS],
                        showTopTracks = prefs[GP_TOP_TRACKS],
                        topTracksCount = prefs[GP_TOP_TRACKS_COUNT],
                        dynamicColors = prefs[GP_DYNAMIC_COLORS],
                    ),
                playlistPage =
                    StoredPlaylistPage(
                        headerBackground = prefs[PP_BACKGROUND],
                        hiddenButtons =
                            prefs[PP_HIDDEN_BUTTONS]
                                ?.split(
                                    LIST_SEPARATOR,
                                )?.filter { it.isNotEmpty() },
                        dynamicColors = prefs[PP_DYNAMIC_COLORS],
                    ),
                searchPage =
                    StoredSearchPage(
                        singleLineChips = prefs[SP_SINGLE_LINE_CHIPS],
                        showFavorite = prefs[SP_SHOW_FAVORITE],
                        trackAction = prefs[SP_TRACK_ACTION],
                        sort = prefs[SP_SORT],
                    ),
                advanced =
                    StoredAdvanced(
                        keepScreenOn = prefs[ADV_KEEP_SCREEN_ON],
                        orientation = prefs[ADV_ORIENTATION],
                        corners = prefs[ADV_CORNERS],
                        showTrackNumber = prefs[ADV_TRACK_NUMBER],
                        hideStatusBar = prefs[ADV_HIDE_STATUS_BAR],
                    ),
                offlineMode = prefs[OFFLINE_MODE],
                autoOffline = prefs[AUTO_OFFLINE],
                imagePersistent = prefs[IMG_PERSISTENT],
                imageWifiOnly = prefs[IMG_WIFI_ONLY],
                imageHighQuality = prefs[IMG_HIGH_QUALITY],
                qualityDetail = prefs[QUALITY_DETAIL],
                sorts =
                    prefs
                        .asMap()
                        .filterKeys { it.name.startsWith(SORT_PREFIX) }
                        .map { (key, value) -> key.name.removePrefix(SORT_PREFIX) to value.toString() }
                        .toMap(),
            )

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

        /**
         * Stores Home's rows at once (#127); null removes a value (back to default). The sections' own settings (#182)
         * stay, unless [clearSections].
         */
        suspend fun setHome(
            home: StoredHome,
            clearSections: Boolean = false,
        ) {
            dataStore.edit { prefs ->
                prefs.put(HOME_ORDER, home.order?.joinToString(LIST_SEPARATOR))
                prefs.put(HOME_HIDDEN, home.hidden?.joinToString(LIST_SEPARATOR))
                if (clearSections) {
                    prefs
                        .asMap()
                        .keys
                        .filter { it.name.startsWith(HOME_SECTION_PREFIX) }
                        .forEach { prefs.remove(it) }
                }
            }
        }

        /**
         * Stores Home sections' own settings (#182), each section one value (its settings encoded by the caller), so a
         * copy, an apply to all and a restore are one write; a null value removes it (the section's defaults).
         */
        suspend fun setHomeSections(sections: Map<String, String?>) {
            dataStore.edit { prefs ->
                sections.forEach { (name, value) -> prefs.put(stringPreferencesKey(HOME_SECTION_PREFIX + name), value) }
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
                prefs.put(LP_KNOWN_SHORTCUTS, page.knownShortcuts?.joinToString(LIST_SEPARATOR))
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

        /** Stores every Playlist page value at once (#135); null removes it. */
        suspend fun setPlaylistPage(page: StoredPlaylistPage) {
            dataStore.edit { prefs ->
                prefs.put(PP_BACKGROUND, page.headerBackground)
                // An empty set is stored as an empty string: "none hidden" differs from "not set".
                prefs.put(PP_HIDDEN_BUTTONS, page.hiddenButtons?.joinToString(LIST_SEPARATOR))
                prefs.put(PP_DYNAMIC_COLORS, page.dynamicColors)
            }
        }

        /** Stores every Advanced settings value at once (#137); null removes it. */
        suspend fun setAdvanced(advanced: StoredAdvanced) {
            dataStore.edit { prefs ->
                prefs.put(ADV_KEEP_SCREEN_ON, advanced.keepScreenOn)
                prefs.put(ADV_ORIENTATION, advanced.orientation)
                prefs.put(ADV_CORNERS, advanced.corners)
                prefs.put(ADV_TRACK_NUMBER, advanced.showTrackNumber)
                prefs.put(ADV_HIDE_STATUS_BAR, advanced.hideStatusBar)
            }
        }

        /** Stores every Search page value at once (#136); null removes it. */
        suspend fun setSearchPage(page: StoredSearchPage) {
            dataStore.edit { prefs ->
                prefs.put(SP_SINGLE_LINE_CHIPS, page.singleLineChips)
                prefs.put(SP_SHOW_FAVORITE, page.showFavorite)
                prefs.put(SP_TRACK_ACTION, page.trackAction)
                prefs.put(SP_SORT, page.sort)
            }
        }

        /** Stores every Genre page value at once (#134); null removes it. */
        suspend fun setGenrePage(page: StoredGenrePage) {
            dataStore.edit { prefs ->
                prefs.put(GP_BACKGROUND, page.headerBackground)
                // An empty set is stored as an empty string: "none hidden" differs from "not set".
                prefs.put(GP_HIDDEN_BUTTONS, page.hiddenButtons?.joinToString(LIST_SEPARATOR))
                prefs.put(GP_SECTIONS, page.sectionOrder?.joinToString(LIST_SEPARATOR))
                prefs.put(GP_ALBUMS, page.showAlbums)
                prefs.put(GP_ARTISTS, page.showArtists)
                prefs.put(GP_ALBUM_ARTISTS, page.showAlbumArtists)
                prefs.put(GP_TOP_TRACKS, page.showTopTracks)
                prefs.put(GP_TOP_TRACKS_COUNT, page.topTracksCount)
                prefs.put(GP_DYNAMIC_COLORS, page.dynamicColors)
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

        /** App mode (ADR 0051), as its name. */
        suspend fun setAppMode(name: String) {
            dataStore.edit { it[APP_MODE] = name }
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

        /** Automatic offline mode (#144), as its name. */
        suspend fun setAutoOffline(name: String) {
            dataStore.edit { it[AUTO_OFFLINE] = name }
        }

        /** Removes offline mode and automatic offline mode (#144): the defaults apply again. */
        suspend fun clearOffline() {
            dataStore.edit {
                it.remove(OFFLINE_MODE)
                it.remove(AUTO_OFFLINE)
            }
        }

        /** Images (#144): every value at once; null removes it (the default). */
        suspend fun setImages(
            persistent: Boolean?,
            wifiOnly: Boolean?,
            highQuality: Boolean?,
        ) {
            dataStore.edit { prefs ->
                fun put(
                    key: Preferences.Key<Boolean>,
                    value: Boolean?,
                ) {
                    if (value == null) prefs.remove(key) else prefs[key] = value
                }
                put(IMG_PERSISTENT, persistent)
                put(IMG_WIFI_ONLY, wifiOnly)
                put(IMG_HIGH_QUALITY, highQuality)
            }
        }

        /** Stores the sort [name] for the Library tab [tab]. */
        suspend fun setSort(
            tab: String,
            name: String,
        ) {
            dataStore.edit { it[stringPreferencesKey(SORT_PREFIX + tab)] = name }
        }

        /** Replaces the stored sort of [tab] with [change] of it, in one edit (#187: two quick taps both count). */
        suspend fun updateSort(
            tab: String,
            change: (String?) -> String,
        ) {
            val key = stringPreferencesKey(SORT_PREFIX + tab)
            dataStore.edit { it[key] = change(it[key]) }
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
            val LP_KNOWN_SHORTCUTS = stringPreferencesKey("lp_known_shortcuts")
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
            val GP_BACKGROUND = stringPreferencesKey("gp_background")
            val GP_HIDDEN_BUTTONS = stringPreferencesKey("gp_hidden_buttons")
            val GP_SECTIONS = stringPreferencesKey("gp_sections")
            val GP_ALBUMS = booleanPreferencesKey("gp_albums")
            val GP_ARTISTS = booleanPreferencesKey("gp_artists")
            val GP_ALBUM_ARTISTS = booleanPreferencesKey("gp_album_artists")
            val GP_TOP_TRACKS = booleanPreferencesKey("gp_top_tracks")
            val GP_TOP_TRACKS_COUNT = intPreferencesKey("gp_top_tracks_count")
            val GP_DYNAMIC_COLORS = booleanPreferencesKey("gp_dynamic_colors")
            val PP_BACKGROUND = stringPreferencesKey("pp_background")
            val PP_HIDDEN_BUTTONS = stringPreferencesKey("pp_hidden_buttons")
            val PP_DYNAMIC_COLORS = booleanPreferencesKey("pp_dynamic_colors")
            val SP_SINGLE_LINE_CHIPS = booleanPreferencesKey("sp_single_line_chips")
            val SP_SHOW_FAVORITE = booleanPreferencesKey("sp_show_favorite")
            val SP_TRACK_ACTION = stringPreferencesKey("sp_track_action")
            val SP_SORT = stringPreferencesKey("sp_sort")
            val ADV_KEEP_SCREEN_ON = stringPreferencesKey("adv_keep_screen_on")
            val ADV_ORIENTATION = stringPreferencesKey("adv_orientation")
            val ADV_CORNERS = stringPreferencesKey("adv_corners")
            val ADV_TRACK_NUMBER = booleanPreferencesKey("adv_track_number")
            val ADV_HIDE_STATUS_BAR = booleanPreferencesKey("adv_hide_status_bar")
            val ALBUM_COLUMNS = intPreferencesKey("album_columns")
            val GENRES_GRID = booleanPreferencesKey("genres_grid")
            val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
            val RATING_MODE = stringPreferencesKey("rating_mode")
            val APP_MODE = stringPreferencesKey("app_mode")
            val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
            val AUTO_OFFLINE = stringPreferencesKey("auto_offline")
            val IMG_PERSISTENT = booleanPreferencesKey("img_persistent")
            val IMG_WIFI_ONLY = booleanPreferencesKey("img_wifi_only")
            val IMG_HIGH_QUALITY = booleanPreferencesKey("img_high_quality")
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
            const val HOME_SECTION_PREFIX = "home_section_"
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
    /** `null` = never chosen (an install from before ADR 0051, or onboarding not done). */
    val appMode: String? = null,
    val offlineMode: Boolean? = null,
    val autoOffline: String? = null,
    val imagePersistent: Boolean? = null,
    val imageWifiOnly: Boolean? = null,
    val imageHighQuality: Boolean? = null,
    val theme: StoredTheme = StoredTheme(),
    val navigation: StoredNavigation = StoredNavigation(),
    val mediaLists: StoredMediaLists = StoredMediaLists(),
    val nowPlaying: StoredNowPlaying = StoredNowPlaying(),
    val home: StoredHome = StoredHome(),
    val libraryPage: StoredLibraryPage = StoredLibraryPage(),
    val albumPage: StoredAlbumPage = StoredAlbumPage(),
    val artistPage: StoredArtistPage = StoredArtistPage(),
    val genrePage: StoredGenrePage = StoredGenrePage(),
    val playlistPage: StoredPlaylistPage = StoredPlaylistPage(),
    val searchPage: StoredSearchPage = StoredSearchPage(),
    val advanced: StoredAdvanced = StoredAdvanced(),
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

/** Playlist page values as stored (#135); `null` = not set. */
data class StoredPlaylistPage(
    val headerBackground: String? = null,
    val hiddenButtons: List<String>? = null,
    val dynamicColors: Boolean? = null,
)

/** Advanced settings as stored (#137); `null` = not set. */
data class StoredAdvanced(
    val keepScreenOn: String? = null,
    val orientation: String? = null,
    val corners: String? = null,
    val showTrackNumber: Boolean? = null,
    val hideStatusBar: Boolean? = null,
)

/** Search page values as stored (#136); `null` = not set (a `null` track action = Same as default). */
data class StoredSearchPage(
    val singleLineChips: Boolean? = null,
    val showFavorite: Boolean? = null,
    val trackAction: String? = null,
    val sort: String? = null,
)

/** Genre page values as stored (#134); `null` = not set. */
data class StoredGenrePage(
    val headerBackground: String? = null,
    val hiddenButtons: List<String>? = null,
    val sectionOrder: List<String>? = null,
    val showAlbums: Boolean? = null,
    val showArtists: Boolean? = null,
    val showAlbumArtists: Boolean? = null,
    val showTopTracks: Boolean? = null,
    val topTracksCount: Int? = null,
    val dynamicColors: Boolean? = null,
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
    /** Every tile the app had when [shortcuts] was saved: a tile missing from both is new and gets appended. */
    val knownShortcuts: List<String>? = null,
)

/** Home's rows as stored (#127): section names; `null` = not set (defaults). */
data class StoredHome(
    val order: List<String>? = null,
    val hidden: List<String>? = null,
    /** Each section's own settings by section name (#182), as the repository encoded them. */
    val sections: Map<String, String> = emptyMap(),
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
