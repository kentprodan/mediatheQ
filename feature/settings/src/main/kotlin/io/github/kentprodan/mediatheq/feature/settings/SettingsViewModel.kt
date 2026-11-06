package io.github.kentprodan.mediatheq.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.data.AlbumSection
import io.github.kentprodan.mediatheq.core.data.ArtistSection
import io.github.kentprodan.mediatheq.core.data.AudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.GenreSection
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.SessionRepository
import io.github.kentprodan.mediatheq.core.data.SessionState
import io.github.kentprodan.mediatheq.core.model.LibraryShortcut
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
        private val entriesDraft = MutableStateFlow<NavEntriesDraft?>(null)
        private val shortcutsDraft = MutableStateFlow<OrganizeDraft<LibraryShortcut>?>(null)
        private val sectionsDraft = MutableStateFlow<OrganizeDraft<AlbumSection>?>(null)
        private val artistSectionsDraft = MutableStateFlow<OrganizeDraft<ArtistSection>?>(null)
        private val genreSectionsDraft = MutableStateFlow<OrganizeDraft<GenreSection>?>(null)

        val uiState: StateFlow<SettingsUiState> =
            combine(
                session.session,
                audio.preferences,
                library.preferences,
                playback.state.map { it.output }.distinctUntilChanged(),
                combine(entriesDraft, shortcutsDraft, sectionsDraft, artistSectionsDraft, genreSectionsDraft, ::Drafts),
            ) { session, audio, library, output, (draft, shortcuts, sections, artistSections, genreSections) ->
                // Signed out: the app leaves for onboarding, nothing to show meanwhile.
                (session as? SessionState.LoggedIn)?.let {
                    SettingsUiState.Content(
                        it.serverName,
                        it.userName,
                        audio,
                        library,
                        output,
                        draft,
                        shortcuts,
                        sections,
                        artistSections,
                        genreSections,
                    )
                } ?: SettingsUiState.Loading
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

        fun onAction(action: SettingsAction) {
            viewModelScope.launch {
                when (action) {
                    SettingsAction.LogOut -> {
                        session.logout()
                    }

                    is SettingsAction.SetStreamingQuality -> {
                        audio.setStreamingQuality(action.metered, action.quality)
                    }

                    is SettingsAction.SetNormalization -> {
                        audio.setNormalization(action.normalization)
                    }

                    is SettingsAction.SetStreamingCacheGb -> {
                        audio.setStreamingCacheGb(action.gb)
                    }

                    is SettingsAction.SetDownloadsUnmeteredOnly -> {
                        audio.setDownloadsUnmeteredOnly(action.on)
                    }

                    is SettingsAction.SetTheme -> {
                        library.setTheme(action.theme)
                    }

                    SettingsAction.RestoreThemeDefaults -> {
                        library.restoreThemeDefaults()
                    }

                    is SettingsAction.SetNavigation -> {
                        library.setNavigation(action.navigation)
                    }

                    SettingsAction.RestoreNavigationDefaults -> {
                        library.restoreNavigationDefaults()
                    }

                    is SettingsAction.SetMediaLists -> {
                        library.setMediaLists(action.mediaLists)
                    }

                    SettingsAction.RestoreMediaListsDefaults -> {
                        library.restoreMediaListsDefaults()
                    }

                    is SettingsAction.SetNowPlaying -> {
                        library.setNowPlaying(action.nowPlaying)
                    }

                    SettingsAction.RestoreNowPlayingDefaults -> {
                        library.restoreNowPlayingDefaults()
                    }

                    is SettingsAction.SetHomeOrder -> {
                        library.setHomeOrder(action.order)
                    }

                    is SettingsAction.SetHomeHidden -> {
                        library.setHomeHidden(action.hidden)
                    }

                    SettingsAction.ResetHomeRows -> {
                        library.resetHomeRows()
                    }

                    SettingsAction.RestoreHomeDefaults -> {
                        library.restoreHomeDefaults()
                    }

                    SettingsAction.OpenNavEntries -> {
                        entriesDraft.value =
                            navEntriesDraft(
                                library.preferences
                                    .first()
                                    .navigation.entries,
                            )
                    }

                    is SettingsAction.ToggleNavEntry -> {
                        entriesDraft.update { it?.toggle(action.entry) }
                    }

                    is SettingsAction.MoveNavEntry -> {
                        entriesDraft.update { it?.move(action.from, action.to) }
                    }

                    SettingsAction.ConfirmNavEntries -> {
                        entriesDraft.value?.let { draft ->
                            val navigation = library.preferences.first().navigation
                            library.setNavigation(navigation.copy(entries = draft.entries))
                        }
                        entriesDraft.value = null
                    }

                    SettingsAction.DismissNavEntries -> {
                        entriesDraft.value = null
                    }

                    is SettingsAction.SetLibraryPage -> {
                        library.setLibraryPage(action.page)
                    }

                    SettingsAction.RestoreLibraryPageDefaults -> {
                        library.restoreLibraryPageDefaults()
                    }

                    SettingsAction.OpenLibraryShortcuts -> {
                        val stored =
                            library.preferences
                                .first()
                                .libraryPage.shortcuts
                        shortcutsDraft.value = OrganizeDraft.of(stored, LibraryShortcut.entries)
                    }

                    is SettingsAction.ToggleLibraryShortcut -> {
                        shortcutsDraft.update { it?.toggle(action.shortcut) }
                    }

                    is SettingsAction.MoveLibraryShortcut -> {
                        shortcutsDraft.update { it?.move(action.from, action.to) }
                    }

                    SettingsAction.ConfirmLibraryShortcuts -> {
                        shortcutsDraft.value?.let { draft ->
                            val page = library.preferences.first().libraryPage
                            library.setLibraryPage(page.copy(shortcuts = draft.entries))
                        }
                        shortcutsDraft.value = null
                    }

                    SettingsAction.DismissLibraryShortcuts -> {
                        shortcutsDraft.value = null
                    }

                    is SettingsAction.SetAlbumPage -> {
                        library.setAlbumPage(action.page)
                    }

                    SettingsAction.RestoreAlbumPageDefaults -> {
                        library.restoreAlbumPageDefaults()
                    }

                    SettingsAction.OpenAlbumSections -> {
                        sectionsDraft.value =
                            albumSectionsDraft(
                                library.preferences
                                    .first()
                                    .albumPage.sectionOrder,
                            )
                    }

                    is SettingsAction.MoveAlbumSection -> {
                        sectionsDraft.update { it?.move(action.from, action.to) }
                    }

                    SettingsAction.ConfirmAlbumSections -> {
                        sectionsDraft.value?.let { draft ->
                            val page = library.preferences.first().albumPage
                            library.setAlbumPage(page.copy(sectionOrder = draft.order))
                        }
                        sectionsDraft.value = null
                    }

                    SettingsAction.DismissAlbumSections -> {
                        sectionsDraft.value = null
                    }

                    is SettingsAction.SetArtistPage -> {
                        library.setArtistPage(action.page)
                    }

                    SettingsAction.RestoreArtistPageDefaults -> {
                        library.restoreArtistPageDefaults()
                    }

                    SettingsAction.OpenArtistSections -> {
                        artistSectionsDraft.value =
                            artistSectionsDraft(
                                library.preferences
                                    .first()
                                    .artistPage.sectionOrder,
                            )
                    }

                    is SettingsAction.MoveArtistSection -> {
                        artistSectionsDraft.update { it?.move(action.from, action.to) }
                    }

                    SettingsAction.ConfirmArtistSections -> {
                        artistSectionsDraft.value?.let { draft ->
                            val page = library.preferences.first().artistPage
                            library.setArtistPage(page.copy(sectionOrder = draft.order))
                        }
                        artistSectionsDraft.value = null
                    }

                    SettingsAction.DismissArtistSections -> {
                        artistSectionsDraft.value = null
                    }

                    is SettingsAction.SetGenrePage -> {
                        library.setGenrePage(action.page)
                    }

                    SettingsAction.RestoreGenrePageDefaults -> {
                        library.restoreGenrePageDefaults()
                    }

                    is SettingsAction.SetPlaylistPage -> {
                        library.setPlaylistPage(action.page)
                    }

                    SettingsAction.RestorePlaylistPageDefaults -> {
                        library.restorePlaylistPageDefaults()
                    }

                    SettingsAction.OpenGenreSections -> {
                        genreSectionsDraft.value =
                            genreSectionsDraft(
                                library.preferences
                                    .first()
                                    .genrePage.sectionOrder,
                            )
                    }

                    is SettingsAction.MoveGenreSection -> {
                        genreSectionsDraft.update { it?.move(action.from, action.to) }
                    }

                    SettingsAction.ConfirmGenreSections -> {
                        genreSectionsDraft.value?.let { draft ->
                            val page = library.preferences.first().genrePage
                            library.setGenrePage(page.copy(sectionOrder = draft.order))
                        }
                        genreSectionsDraft.value = null
                    }

                    SettingsAction.DismissGenreSections -> {
                        genreSectionsDraft.value = null
                    }

                    is SettingsAction.SetOfflineMode -> {
                        library.setOfflineMode(action.on)
                    }

                    is SettingsAction.SetAlbumColumns -> {
                        library.setAlbumColumns(action.columns)
                    }

                    is SettingsAction.SetRatingMode -> {
                        library.setRatingMode(action.mode)
                    }

                    is SettingsAction.SetBitPerfectUsb -> {
                        audio.setBitPerfectUsb(action.on)
                    }

                    is SettingsAction.SetEqualizerOn -> {
                        audio.setEqualizerOn(action.on)
                    }

                    is SettingsAction.SetEqPreset -> {
                        audio.setEqPreset(action.preset)
                    }

                    is SettingsAction.SetEqBand -> {
                        audio.setEqBand(action.index, action.gainDb)
                    }
                }
            }
        }
    }

/** The organize dialogs' drafts, one value for the state combine. */
private data class Drafts(
    val entries: NavEntriesDraft?,
    val shortcuts: OrganizeDraft<LibraryShortcut>?,
    val albumSections: OrganizeDraft<AlbumSection>?,
    val artistSections: OrganizeDraft<ArtistSection>?,
    val genreSections: OrganizeDraft<GenreSection>?,
)
