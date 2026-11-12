package io.github.kentprodan.mediatheq.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.DefaultDispatcher
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.homeItems
import io.github.kentprodan.mediatheq.core.model.HomeSection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Settings -> Home page -> a section's gear (#182): the section as Home shows it (its real items), and its settings,
 * each change stored at once (Home follows).
 */
@HiltViewModel(assistedFactory = HomeSectionSettingsViewModel.Factory::class)
class HomeSectionSettingsViewModel
    @AssistedInject
    constructor(
        @Assisted private val section: HomeSection,
        libraryRepository: LibraryRepository,
        private val preferences: LibraryPreferencesRepository,
        shuffle: HomeShuffle,
        clock: Clock,
        @param:DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        val uiState: StateFlow<HomeSectionSettingsUiState> =
            combine(
                libraryRepository.homeLibrary(),
                preferences.preferences.map { it.home.settingsOf(section) }.distinctUntilChanged(),
                shuffle.seed,
            ) { library, settings, seed ->
                val items = homeItems(section, library, settings.maxItems, seed, clock.now())
                HomeSectionSettingsUiState.Content(
                    HomeSectionContent(section, settings, items),
                    copyFrom = HomeSection.entries.filter { it.kind == section.kind && it != section },
                )
            }.flowOn(defaultDispatcher)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeSectionSettingsUiState.Loading)

        fun onAction(action: HomeSectionSettingsAction) {
            viewModelScope.launch {
                when (action) {
                    is HomeSectionSettingsAction.Set -> {
                        preferences.setHomeSectionSettings(section, action.settings)
                    }

                    is HomeSectionSettingsAction.CopyFrom -> {
                        val home = preferences.preferences.first().home
                        preferences.setHomeSectionSettings(section, home.settingsOf(action.from))
                    }

                    HomeSectionSettingsAction.ApplyToAll -> {
                        val home = preferences.preferences.first().home
                        preferences.applyHomeSectionSettings(section.kind, home.settingsOf(section))
                    }

                    HomeSectionSettingsAction.Restore -> {
                        preferences.restoreHomeSection(section)
                    }
                }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(section: HomeSection): HomeSectionSettingsViewModel
        }
    }
