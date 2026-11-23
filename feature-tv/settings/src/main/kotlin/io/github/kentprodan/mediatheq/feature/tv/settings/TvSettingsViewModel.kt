package io.github.kentprodan.mediatheq.feature.tv.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.AccountRepository
import io.github.kentprodan.mediatheq.core.data.AppModeRepository
import io.github.kentprodan.mediatheq.core.data.AudioOutputRepository
import io.github.kentprodan.mediatheq.core.data.AudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.DefaultTvCarouselSources
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibrarySyncRepository
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.RememberedOutputs
import io.github.kentprodan.mediatheq.core.data.TvCarousel
import io.github.kentprodan.mediatheq.core.data.TvMenuStyle
import io.github.kentprodan.mediatheq.core.data.VideoPreferencesRepository
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.model.HomeKind
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.OutputKind
import io.github.kentprodan.mediatheq.core.model.TvTitleStyle
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/** Settings on TV (#243): the HDMI output's passthrough, the streaming quality, Sync now and the account. */
@HiltViewModel
class TvSettingsViewModel
    @Inject
    constructor(
        private val outputs: AudioOutputRepository,
        private val audioPreferences: AudioPreferencesRepository,
        private val sync: LibrarySyncRepository,
        private val accounts: AccountRepository,
        private val playback: PlaybackConnection,
        private val appMode: AppModeRepository,
        private val videoPreferences: VideoPreferencesRepository = VideoPreferencesRepository.Defaults,
        private val libraryPreferences: LibraryPreferencesRepository? = null,
    ) : ViewModel() {
        private val simple = libraryPreferences?.preferences?.map { it.navigation } ?: flowOf(NavigationPreferences())
        private val dialog = MutableStateFlow<TvSettingsDialog?>(null)

        val uiState: StateFlow<TvSettingsUiState> =
            combine(
                outputs.outputs,
                audioPreferences.preferences,
                sync.status,
                accounts.accounts,
                combine(dialog, appMode.mode, videoPreferences.preferences, simple) { open, mode, video, plain ->
                    TvSettingsExtras(open, mode, video, plain)
                },
            ) { remembered, preferences, status, list, (open, mode, video, plain) ->
                val active = list.firstOrNull { it.active }
                TvSettingsUiState.Content(
                    hdmi = hdmiOutput(remembered),
                    quality = preferences.unmeteredQuality,
                    sync = status,
                    serverName = active?.serverName,
                    userName = active?.userName,
                    dialog = open,
                    appMode = mode.takeIf { appMode.videoAvailable },
                    subtitleStyle = video.subtitleStyle,
                    displayMatch = video.displayMatch,
                    simpleTransitions = plain.simpleTransitions,
                    carousel = plain.tvCarousel,
                    // What Home mixes (#575): the chosen sections the mode has, else the default.
                    carouselPicked = carouselPicked(plain.tvCarouselSources, carouselSources(mode)),
                    carouselSkipWatched = plain.tvCarouselSkipWatched,
                    carouselSources = carouselSources(mode),
                    menuStyle = plain.tvMenuStyle,
                    titleStyle = plain.tvTitle,
                    posterAccent = plain.tvPosterAccent,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TvSettingsUiState.Loading)

        private fun updateNavigation(change: (NavigationPreferences) -> NavigationPreferences) {
            val repo = libraryPreferences ?: return
            viewModelScope.launch { repo.setNavigation(change(repo.preferences.first().navigation)) }
        }

        fun onAction(action: TvSettingsAction) {
            when (action) {
                TvSettingsAction.TogglePassthrough -> {
                    val id = (uiState.value as? TvSettingsUiState.Content)?.hdmi?.id ?: return
                    viewModelScope.launch {
                        outputs.updateProfile(id) { it.copy(atmosPassthrough = !it.atmosPassthrough) }
                    }
                }

                TvSettingsAction.ChooseQuality -> {
                    dialog.value = TvSettingsDialog.Quality
                }

                is TvSettingsAction.SetQuality -> {
                    dialog.value = null
                    viewModelScope.launch { audioPreferences.setStreamingQuality(metered = false, action.quality) }
                }

                // The sync runs in its own job: leaving the screen does not stop it.
                TvSettingsAction.SyncNow -> {
                    viewModelScope.launch { sync.sync() }
                }

                TvSettingsAction.ChooseAppMode -> {
                    dialog.value = TvSettingsDialog.AppMode
                }

                // A kind turned on syncs by itself (LibrarySyncRepository watches the mode).
                is TvSettingsAction.SetAppMode -> {
                    dialog.value = null
                    viewModelScope.launch { appMode.setMode(action.mode) }
                }

                TvSettingsAction.NextCarousel -> {
                    updateNavigation {
                        val all = TvCarousel.entries
                        it.copy(tvCarousel = all[(it.tvCarousel.ordinal + 1) % all.size])
                    }
                }

                TvSettingsAction.NextMenuStyle -> {
                    val repo = libraryPreferences ?: return
                    viewModelScope.launch {
                        val navigation = repo.preferences.first().navigation
                        val all = TvMenuStyle.entries
                        repo.setNavigation(
                            navigation.copy(tvMenuStyle = all[(navigation.tvMenuStyle.ordinal + 1) % all.size]),
                        )
                    }
                }

                TvSettingsAction.TogglePosterAccent -> {
                    updateNavigation { it.copy(tvPosterAccent = !it.tvPosterAccent) }
                }

                TvSettingsAction.NextTitleStyle -> {
                    val repo = libraryPreferences ?: return
                    viewModelScope.launch {
                        val navigation = repo.preferences.first().navigation
                        val all = TvTitleStyle.entries
                        repo.setNavigation(navigation.copy(tvTitle = all[(navigation.tvTitle.ordinal + 1) % all.size]))
                    }
                }

                TvSettingsAction.ChooseCarouselSource -> {
                    dialog.value = TvSettingsDialog.CarouselSource
                }

                is TvSettingsAction.ToggleCarouselSource -> {
                    val sources = (uiState.value as? TvSettingsUiState.Content)?.carouselSources ?: return
                    updateNavigation { navigation ->
                        val picked = carouselPicked(navigation.tvCarouselSources, sources)
                        val next = if (action.source in picked) picked - action.source else picked + action.source
                        // The panel stays open; the last section cannot go off. Sections this mode does not show stay
                        // stored (review: toggling in Music mode kept wiping the video ones).
                        val hidden = navigation.tvCarouselSources.orEmpty().filter { it !in sources }
                        if (next.isEmpty()) {
                            navigation
                        } else {
                            navigation.copy(
                                tvCarouselSources =
                                    hidden + sources.filter { it in next },
                            )
                        }
                    }
                }

                TvSettingsAction.ToggleCarouselSkipWatched -> {
                    updateNavigation { it.copy(tvCarouselSkipWatched = !it.tvCarouselSkipWatched) }
                }

                TvSettingsAction.ToggleSimpleTransitions -> {
                    val repo = libraryPreferences ?: return
                    viewModelScope.launch {
                        val navigation = repo.preferences.first().navigation
                        repo.setNavigation(navigation.copy(simpleTransitions = !navigation.simpleTransitions))
                    }
                }

                is TvSettingsAction.ChooseSubtitle -> {
                    dialog.value = action.dialog
                }

                is TvSettingsAction.SetDisplayMatch -> {
                    dialog.value = null
                    viewModelScope.launch { videoPreferences.update { it.copy(displayMatch = action.match) } }
                }

                is TvSettingsAction.SetSubtitleStyle -> {
                    dialog.value = null
                    viewModelScope.launch { videoPreferences.update { it.copy(subtitleStyle = action.style) } }
                }

                TvSettingsAction.AskSignOut -> {
                    dialog.value = TvSettingsDialog.SignOut
                }

                TvSettingsAction.SignOut -> {
                    dialog.value = null
                    viewModelScope.launch { signOut() }
                }

                TvSettingsAction.Dismiss -> {
                    dialog.value = null
                }
            }
        }

        /**
         * Forgets the active account as the phone does (ADR 0036): another account becomes active (a restart), or the
         * app returns to the Server screen when it was the last. Music pauses first so the saved queue is current.
         */
        private suspend fun signOut() {
            val active = accounts.accounts.first().firstOrNull { it.active } ?: return
            if (playback.state.value.isPlaying) {
                playback.togglePlayPause()
                delay(QUEUE_SAVE_WAIT)
            }
            // The TV keeps no downloads of its own (TV 1); whatever the cache holds goes with the account.
            accounts.remove(active.id, deleteDownloads = true)
        }

        private companion object {
            val QUEUE_SAVE_WAIT = 1_500.milliseconds
        }
    }

/**
 * The HDMI output the passthrough switch edits: the current output when it is HDMI, else the last HDMI output
 * remembered (the player remembers it on its first play over HDMI, #241); `null` before that.
 */
internal fun hdmiOutput(remembered: RememberedOutputs): TvHdmiOutput? {
    val hdmi = remembered.outputs.filter { it.id.kind == OutputKind.Hdmi }
    val output = hdmi.firstOrNull { it.id == remembered.current } ?: hdmi.lastOrNull() ?: return null
    return TvHdmiOutput(output.id, output.name, output.profile.atmosPassthrough)
}

/** The settings read beside the main ones (combine takes five flows). */
private data class TvSettingsExtras(
    val dialog: TvSettingsDialog?,
    val mode: AppMode,
    val video: io.github.kentprodan.mediatheq.core.data.VideoPreferences,
    val simpleTransitions: NavigationPreferences,
)

/** The sections the carousel mixes (#575): the chosen ones among [sources], else the default ones there. */
internal fun carouselPicked(
    chosen: List<HomeSection>?,
    sources: List<HomeSection>,
): List<HomeSection> =
    chosen?.filter { it in sources }?.ifEmpty { null }
        // As Home: new albums when none of the default is shown in this mode.
        ?: DefaultTvCarouselSources.filter { it in sources }.ifEmpty {
            listOf(HomeSection.RecentlyAddedAlbums).filter { it in sources }.ifEmpty { sources.take(1) }
        }

/** What the Home carousel can show (#521): the sections of videos, albums and playlists the [mode] has. */
internal fun carouselSources(mode: AppMode?): List<HomeSection> =
    HomeSection.entries.filter {
        it.kind in CAROUSEL_KINDS && (mode == null || it.allowed(mode))
    }

private val CAROUSEL_KINDS = setOf(HomeKind.Videos, HomeKind.Albums, HomeKind.Playlists)
