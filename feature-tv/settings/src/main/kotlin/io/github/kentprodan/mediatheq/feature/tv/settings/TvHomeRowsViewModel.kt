package io.github.kentprodan.mediatheq.feature.tv.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.AppModeRepository
import io.github.kentprodan.mediatheq.core.data.GENRE_ROW
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.TvCardStyle
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.VideoSort
import io.github.kentprodan.mediatheq.core.data.genreCounts
import io.github.kentprodan.mediatheq.core.data.tvRowId
import io.github.kentprodan.mediatheq.core.data.tvRowIds
import io.github.kentprodan.mediatheq.core.model.HomeSection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row of Settings -> Home rows on TV (#431): a Home section, or a genre's row (#621). */
data class TvHomeRow(
    val section: HomeSection?,
    val shown: Boolean,
    /** `null`: the row's own default (#518). */
    val cardStyle: TvCardStyle? = null,
    /** Shown as an immersive list (#521); sections only. */
    val immersive: Boolean = false,
    /** How many cards fit on screen (#604); `null`: the variant's default. */
    val perScreen: Int? = null,
    /** The genre of a genre row (#621). */
    val genre: String? = null,
) {
    /** The row's id in Home's order (`tvRowId`). */
    val id: String get() = section?.let(::tvRowId) ?: tvRowId(genre.orEmpty())
}

/**
 * Settings -> Home rows: the sections and the genre rows picked (#621) in Home's order, then the library's genres (#431)
 * with whether each has a row.
 */
data class TvHomeRowsState(
    val rows: List<TvHomeRow>,
    val genres: List<Pair<String, Boolean>> = emptyList(),
)

sealed interface TvHomeRowsAction {
    /** A genre's row on or off; a new one goes last. */
    data class ToggleGenre(
        val genre: String,
    ) : TvHomeRowsAction

    /** A row on Home or not: a section shows or hides, a genre's row goes (#621). */
    data class Toggle(
        val id: String,
    ) : TvHomeRowsAction

    /** The row's card variant (#518, #621); `null` goes back to its default. */
    data class SetCardStyle(
        val id: String,
        val style: TvCardStyle?,
    ) : TvHomeRowsAction

    /** How many of the row's cards fit on screen (#604, #621); `null` goes back to the default. */
    data class SetPerScreen(
        val id: String,
        val count: Int?,
    ) : TvHomeRowsAction

    /** The row as an immersive list on or off (#521). */
    data class ToggleImmersive(
        val section: HomeSection,
    ) : TvHomeRowsAction

    /** One place up or down among the rows listed (#621). */
    data class Move(
        val id: String,
        val up: Boolean,
    ) : TvHomeRowsAction
}

/**
 * Settings -> Home rows on TV (#431, #621): the Home sections of the TV's mode (music and video together) and the genre
 * rows picked, in Home's order, each shown or hidden, with its design and size.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TvHomeRowsViewModel
    @Inject
    constructor(
        private val preferences: LibraryPreferencesRepository,
        appMode: AppModeRepository,
        videos: VideoRepository? = null,
    ) : ViewModel() {
        private val visible =
            combine(preferences.preferences, appMode.mode, preferences.homeGenres) { prefs, mode, genres ->
                val nav = prefs.navigation
                val sections = prefs.home.order.filter { it.allowed(mode) }
                // Genre rows show with video only, as on Home.
                val picked = genres.takeIf { mode.video }.orEmpty()
                val bySection =
                    sections.associate { section ->
                        tvRowId(section) to
                            TvHomeRow(
                                section,
                                section !in prefs.home.hidden,
                                nav.tvCardStyles[section],
                                section in nav.tvImmersiveRows,
                                nav.tvCardsPerScreen[section],
                            )
                    }
                val byGenre =
                    picked.associate { genre ->
                        tvRowId(genre) to
                            TvHomeRow(
                                null,
                                shown = true,
                                cardStyle = nav.tvGenreCardStyles[genre],
                                perScreen = nav.tvGenreCardsPerScreen[genre],
                                genre = genre,
                            )
                    }
                tvRowIds(sections, picked, nav.tvRowOrder).mapNotNull { bySection[it] ?: byGenre[it] }
            }

        /** The library's genres, the most common first; none without video. */
        private val libraryGenres: Flow<List<String>> =
            appMode.mode
                .map { it.video }
                .distinctUntilChanged()
                .flatMapLatest { video ->
                    if (!video || videos == null) {
                        flowOf(emptyList())
                    } else {
                        combine(
                            videos.movies(VideoSort.Name, unwatched = false),
                            videos.series(VideoSort.Name, unwatched = false),
                        ) { movies, series ->
                            genreCounts(
                                movies.map { it.genres } + series.map { it.genres },
                            ).map { it.first }
                        }
                    }
                }

        /** `null` until the settings are read. */
        val state: StateFlow<TvHomeRowsState?> =
            combine(visible, libraryGenres, preferences.homeGenres) { rows, genres, picked ->
                TvHomeRowsState(rows, genres.map { it to (it in picked) })
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        private suspend fun updateNavigation(change: (NavigationPreferences) -> NavigationPreferences) =
            preferences.setNavigation(change(preferences.preferences.first().navigation))

        private suspend fun toggleGenre(genre: String) {
            val picked = preferences.homeGenres.first()
            preferences.setHomeGenres(if (genre in picked) picked - genre else picked + genre)
        }

        fun onAction(action: TvHomeRowsAction) {
            viewModelScope.launch {
                val home = preferences.preferences.first().home
                when (action) {
                    is TvHomeRowsAction.Toggle -> {
                        val section = HomeSection.entries.firstOrNull { tvRowId(it) == action.id }
                        if (section == null) {
                            toggleGenre(action.id.removePrefix(GENRE_ROW))
                        } else {
                            val hidden = home.hidden
                            preferences.setHomeHidden(if (section in hidden) hidden - section else hidden + section)
                        }
                    }

                    is TvHomeRowsAction.ToggleGenre -> {
                        toggleGenre(action.genre)
                    }

                    is TvHomeRowsAction.SetCardStyle -> {
                        val section = HomeSection.entries.firstOrNull { tvRowId(it) == action.id }
                        updateNavigation { nav ->
                            if (section != null) {
                                nav.copy(tvCardStyles = nav.tvCardStyles.with(section, action.style))
                            } else {
                                val genre = action.id.removePrefix(GENRE_ROW)
                                nav.copy(tvGenreCardStyles = nav.tvGenreCardStyles.with(genre, action.style))
                            }
                        }
                    }

                    is TvHomeRowsAction.SetPerScreen -> {
                        val section = HomeSection.entries.firstOrNull { tvRowId(it) == action.id }
                        updateNavigation { nav ->
                            if (section != null) {
                                nav.copy(tvCardsPerScreen = nav.tvCardsPerScreen.with(section, action.count))
                            } else {
                                val genre = action.id.removePrefix(GENRE_ROW)
                                nav.copy(tvGenreCardsPerScreen = nav.tvGenreCardsPerScreen.with(genre, action.count))
                            }
                        }
                    }

                    is TvHomeRowsAction.ToggleImmersive -> {
                        updateNavigation { nav ->
                            val rows = nav.tvImmersiveRows
                            nav.copy(
                                tvImmersiveRows =
                                    if (action.section in rows) rows - action.section else rows + action.section,
                            )
                        }
                    }

                    // The whole order is kept (#621): rows of the other mode or genres off keep their places.
                    is TvHomeRowsAction.Move -> {
                        val listed = visible.first().map { it.id }
                        val genres = preferences.homeGenres.first()
                        updateNavigation { nav ->
                            val all = tvRowIds(home.order, genres, nav.tvRowOrder)
                            nav.copy(tvRowOrder = moveRow(all, listed, action.id, action.up))
                        }
                    }
                }
            }
        }
    }

/** [this] with [key] set to [value], or without it for `null`. */
private fun <K, V> Map<K, V>.with(
    key: K,
    value: V?,
): Map<K, V> = value?.let { this + (key to it) } ?: (this - key)

/**
 * [row] moved one place up or down among [visible] (the rows listed) within the whole [order]: it swaps with its
 * visible neighbor, so the rows not listed keep their places. At an end nothing changes.
 */
internal fun <T> moveRow(
    order: List<T>,
    visible: List<T>,
    row: T,
    up: Boolean,
): List<T> {
    val at = visible.indexOf(row).takeIf { it >= 0 } ?: return order
    val neighbor = visible.getOrNull(if (up) at - 1 else at + 1) ?: return order
    val a = order.indexOf(row)
    val b = order.indexOf(neighbor)
    if (a < 0 || b < 0) return order
    return order.toMutableList().apply {
        this[a] = neighbor
        this[b] = row
    }
}
