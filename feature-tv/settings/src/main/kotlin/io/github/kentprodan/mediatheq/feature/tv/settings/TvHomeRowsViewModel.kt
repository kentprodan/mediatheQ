package io.github.kentprodan.mediatheq.feature.tv.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.AppModeRepository
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.model.HomeSection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row of Settings -> Home rows on TV (#431). */
data class TvHomeRow(
    val section: HomeSection,
    val shown: Boolean,
)

sealed interface TvHomeRowsAction {
    data class Toggle(
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
@HiltViewModel
class TvHomeRowsViewModel
    @Inject
    constructor(
        private val preferences: LibraryPreferencesRepository,
        appMode: AppModeRepository,
    ) : ViewModel() {
        private val visible =
            combine(preferences.preferences, appMode.mode) { prefs, mode ->
                prefs.home.order
                    .filter { it.allowed(mode) }
                    .map { TvHomeRow(it, it !in prefs.home.hidden) }
            }

        /** `null` until the settings are read. */
        val rows: StateFlow<List<TvHomeRow>?> =
            visible.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
