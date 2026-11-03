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
                        filterStyle = prefs[FILTER_STYLE],
                        albumColumns = prefs[ALBUM_COLUMNS],
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

        suspend fun setFilterStyle(name: String) {
            dataStore.edit { it[FILTER_STYLE] = name }
        }

        suspend fun setAlbumColumns(columns: Int) {
            dataStore.edit { it[ALBUM_COLUMNS] = columns }
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
            val FILTER_STYLE = stringPreferencesKey("filter_style")
            val ALBUM_COLUMNS = intPreferencesKey("album_columns")
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
            const val LIST_SEPARATOR = ","
            const val SORT_PREFIX = "sort_"
            val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
            const val SEPARATOR = "\n"
        }
    }

/** Library preferences as stored; any field may be missing or out of date. */
data class StoredLibraryPreferences(
    val filterStyle: String? = null,
    val albumColumns: Int? = null,
    /** Tab name -> sort name. */
    val sorts: Map<String, String> = emptyMap(),
    val dynamicColor: Boolean? = null,
    val ratingMode: String? = null,
    val offlineMode: Boolean? = null,
    val theme: StoredTheme = StoredTheme(),
    val navigation: StoredNavigation = StoredNavigation(),
    val mediaLists: StoredMediaLists = StoredMediaLists(),
    val qualityDetail: Boolean? = null,
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
