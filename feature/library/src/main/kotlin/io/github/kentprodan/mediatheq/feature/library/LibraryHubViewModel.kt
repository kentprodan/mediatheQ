package io.github.kentprodan.mediatheq.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The Library hub (#129): its tiles follow Settings -> Library page at once. */
@HiltViewModel
class LibraryHubViewModel
    @Inject
    constructor(
        preferences: LibraryPreferencesRepository,
    ) : ViewModel() {
        val uiState: StateFlow<LibraryHubUiState> =
            preferences.preferences
                .map { it.libraryPage }
                .distinctUntilChanged()
                .map<_, LibraryHubUiState> { LibraryHubUiState.Content(it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryHubUiState.Loading)
    }
