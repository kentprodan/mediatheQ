package io.github.kentprodan.mediatheq.feature.tv.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.AppModeRepository
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.TvCardStyle
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.VideoSort
import io.github.kentprodan.mediatheq.core.data.genreCounts
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

/** One row of Settings -> Home rows on TV (#431). */
data class TvHomeRow(
    val section: HomeSection,
    val shown: Boolean,
    /** `null`: the row's own default (#518). */
    val cardStyle: TvCardStyle? = null,
    /** Shown as an immersive list (#521). */
    val immersive: Boolean = false,
)

/** Settings -> Home rows: the sections, then the library's genres (#431) with whether each has a row. */
data class TvHomeRowsState(
    val rows: List<TvHomeRow>,
    val genres: List<Pair<String, Boolean>> = emptyList(),
)

sealed interface TvHomeRowsAction {
    /** A genre's row on or off; a new one goes last. */
    data class ToggleGenre(
        val genre: String,
    ) : TvHomeRowsAction

    data class Toggle(
        val section: HomeSection,
    ) : TvHomeRowsAction

    /** The row's card variant (#518); `null` goes back to its default. */
    data class SetCardStyle(
        val section: HomeSection,
        val style: TvCardStyle?,
    ) : TvHomeRowsAction

    /** The row as an immersive list on or off (#521). */
    data class ToggleImmersive(
        val section: HomeSection,
    ) : TvHomeRowsAction

    data class Move(
        val section: HomeSection,
        val up: Boolean,
    ) : TvHomeRowsAction
}

/**
 * Settings -> Home rows on TV (#431): the Home sections of the TV's mode (music and video together) in their order,
 * each shown or hidden; the same stored order and hidden set as the phone's Home page settings.
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
            combine(preferences.preferences, appMode.mode) { prefs, mode ->
                prefs.home.order
                    .filter { it.allowed(mode) }
                    .map {
                        TvHomeRow(
                            it,
                            it !in prefs.home.hidden,
                            prefs.navigation.tvCardStyles[it],
                            it in prefs.navigation.tvImmersiveRows,
                        )
                    }
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

        fun onAction(action: TvHomeRowsAction) {
            viewModelScope.launch {
                val home = preferences.preferences.first().home
                when (action) {
                    is TvHomeRowsAction.Toggle -> {
                        val hidden = home.hidden
                        preferences.setHomeHidden(
                            if (action.section in hidden) hidden - action.section else hidden + action.section,
                        )
                    }

                    is TvHomeRowsAction.ToggleGenre -> {
                        val picked = preferences.homeGenres.first()
                        preferences.setHomeGenres(
                            if (action.genre in picked) picked - action.genre else picked + action.genre,
                        )
                    }

                    is TvHomeRowsAction.SetCardStyle -> {
                        val navigation = preferences.preferences.first().navigation
                        val styles = navigation.tvCardStyles - action.section
                        preferences.setNavigation(
                            navigation.copy(
                                tvCardStyles = action.style?.let { styles + (action.section to it) } ?: styles,
                            ),
                        )
                    }

                    is TvHomeRowsAction.ToggleImmersive -> {
                        val navigation = preferences.preferences.first().navigation
                        val rows = navigation.tvImmersiveRows
                        preferences.setNavigation(
                            navigation.copy(
                                tvImmersiveRows =
                                    if (action.section in rows) rows - action.section else rows + action.section,
                            ),
                        )
                    }

                    is TvHomeRowsAction.Move -> {
                        val shownHere = visible.first().map { it.section }
                        preferences.setHomeOrder(moveSection(home.order, shownHere, action.section, action.up))
                    }
                }
            }
        }
    }

/**
 * [section] moved one place up or down among [visible] (the sections this mode lists) within the whole [order]: it
 * swaps with its visible neighbor, so sections of the other mode keep their places. At an end nothing changes.
 */
internal fun moveSection(
    order: List<HomeSection>,
    visible: List<HomeSection>,
    section: HomeSection,
    up: Boolean,
): List<HomeSection> {
    val at = visible.indexOf(section).takeIf { it >= 0 } ?: return order
    val neighbor = visible.getOrNull(if (up) at - 1 else at + 1) ?: return order
    val a = order.indexOf(section)
    val b = order.indexOf(neighbor)
    return order.toMutableList().apply {
        this[a] = neighbor
        this[b] = section
    }
}
