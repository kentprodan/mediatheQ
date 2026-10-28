package io.github.kentprodan.mediatheq.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.AudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.SessionRepository
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Settings and its Audio page: every value comes from its repository, so a change shows (and applies) at once. */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val session: SessionRepository,
        private val audio: AudioPreferencesRepository,
        private val library: LibraryPreferencesRepository,
        playback: PlaybackConnection,
    ) : ViewModel() {
        val uiState: StateFlow<SettingsUiState> =
            combine(
                session.session,
                audio.preferences,
                library.preferences,
                playback.state.map { it.output }.distinctUntilChanged(),
            ) { session, audio, library, output ->
                // Signed out: the app leaves for onboarding, nothing to show meanwhile.
                (session as? SessionState.LoggedIn)?.let {
                    SettingsUiState.Content(it.serverName, it.userName, audio, library, output)
                } ?: SettingsUiState.Loading
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

        fun onAction(action: SettingsAction) {
            viewModelScope.launch {
                when (action) {
                    SettingsAction.LogOut -> session.logout()
                    is SettingsAction.SetStreamingQuality -> audio.setStreamingQuality(action.metered, action.quality)
                    is SettingsAction.SetNormalization -> audio.setNormalization(action.normalization)
                    is SettingsAction.SetStreamingCacheGb -> audio.setStreamingCacheGb(action.gb)
                    is SettingsAction.SetDownloadsUnmeteredOnly -> audio.setDownloadsUnmeteredOnly(action.on)
                    is SettingsAction.SetTheme -> library.setTheme(action.theme)
                    SettingsAction.RestoreThemeDefaults -> library.restoreThemeDefaults()
                    is SettingsAction.SetOfflineMode -> library.setOfflineMode(action.on)
                    is SettingsAction.SetAlbumColumns -> library.setAlbumColumns(action.columns)
                    is SettingsAction.SetRatingMode -> library.setRatingMode(action.mode)
                    is SettingsAction.SetBitPerfectUsb -> audio.setBitPerfectUsb(action.on)
                    is SettingsAction.SetEqualizerOn -> audio.setEqualizerOn(action.on)
                    is SettingsAction.SetEqPreset -> audio.setEqPreset(action.preset)
                    is SettingsAction.SetEqBand -> audio.setEqBand(action.index, action.gainDb)
                }
            }
        }
    }
