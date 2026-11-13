package io.github.kentprodan.mediatheq.feature.tv.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.DefaultDispatcher
import io.github.kentprodan.mediatheq.core.data.HomeShuffle
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.LibrarySyncRepository
import io.github.kentprodan.mediatheq.core.data.PlaylistRepository
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.data.homeItems
import io.github.kentprodan.mediatheq.core.data.homeLibrary
import io.github.kentprodan.mediatheq.core.data.mixTracks
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Clock

/**
 * Home on TV (#236): the phone's Home sections (`homeItems`, the same order and settings) from the local library, as
 * rows. No download sections and no offline mode on TV 1.
 */
@HiltViewModel
class TvHomeViewModel
    @Inject
    constructor(
        private val library: LibraryRepository,
        private val sync: LibrarySyncRepository,
        private val playback: PlaybackConnection,
        private val trackTaps: TrackTaps,
        private val playlists: PlaylistRepository,
        preferences: LibraryPreferencesRepository,
        private val shuffle: HomeShuffle,
        private val clock: Clock,
        @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        /** `null` while the library is empty (the sync decides what shows). */
        private val rows =
            combine(
                library.homeLibrary(),
                preferences.preferences.map { it.home }.distinctUntilChanged(),
                shuffle.seed,
            ) { library, home, seed ->
                if (library.albums.isEmpty() && library.tracks.isEmpty()) return@combine null
                val now = clock.now()
                home.shown.mapNotNull { section ->
                    val settings = home.settingsOf(section)
                    val items = homeItems(section, library, settings.maxItems, seed, now)
                    TvHomeRow(section, items).takeUnless { items.isEmpty && settings.hideIfEmpty }
                }
            }.flowOn(defaultDispatcher)

        /** The status, and whether a full sync ever completed (then an empty library is really empty). */
        private val status =
            sync.status.scan(null as Pair<SyncStatus, Boolean>?) { previous, status ->
                status to (previous?.second == true || status.lastSyncedAt != null)
            }

        val uiState: StateFlow<TvHomeUiState> =
            combine(rows, status) { rows, status ->
                val (current, everSynced) = status ?: return@combine TvHomeUiState.Loading
                when {
                    rows != null -> TvHomeUiState.Content(rows)
                    everSynced -> TvHomeUiState.Empty
                    current is SyncStatus.Failed -> TvHomeUiState.Error(current.error)
                    current is SyncStatus.Running -> TvHomeUiState.Syncing(current.fetched, current.total)
                    else -> TvHomeUiState.Syncing()
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TvHomeUiState.Loading)

        fun onAction(action: TvHomeAction) {
            viewModelScope.launch {
                when (action) {
                    is TvHomeAction.Play -> {
                        val tracks = tracksOf(action.target)
                        if (tracks.isNotEmpty()) playback.play(tracks, shuffle = action.shuffle)
                    }

                    is TvHomeAction.AddToQueue -> {
                        val tracks = tracksOf(action.target)
                        if (tracks.isNotEmpty()) playback.addToQueue(tracks)
                    }

                    is TvHomeAction.PlayTracks -> {
                        trackTaps.tap(action.tracks, action.index)
                    }

                    is TvHomeAction.PlayMix -> {
                        val queue =
                            withContext(defaultDispatcher) {
                                mixTracks(
                                    action.mix,
                                    library.tracks().first(),
                                    library.albums().first(),
                                    shuffle.mixRandom(),
                                    clock.now(),
                                )
                            }
                        if (queue.isNotEmpty()) playback.play(queue)
                    }

                    TvHomeAction.Sync -> {
                        sync.sync()
                    }
                }
            }
        }

        private suspend fun tracksOf(target: TvHomeTarget): List<Track> =
            when (target) {
                is TvHomeTarget.Album -> {
                    library.albumTracks(target.id).first()
                }

                is TvHomeTarget.Playlist -> {
                    playlists.items(target.id).first().map { it.track }
                }

                is TvHomeTarget.Song -> {
                    listOf(target.track)
                }

                is TvHomeTarget.Artist -> {
                    library.artistAlbums(target.id).first().flatMap { library.albumTracks(it.id).first() }
                }
            }
    }
