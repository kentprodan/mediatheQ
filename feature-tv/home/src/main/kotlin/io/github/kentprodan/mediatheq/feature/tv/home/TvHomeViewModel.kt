package io.github.kentprodan.mediatheq.feature.tv.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kentprodan.mediatheq.core.common.DefaultDispatcher
import io.github.kentprodan.mediatheq.core.data.AppModeRepository
import io.github.kentprodan.mediatheq.core.data.GenreItem
import io.github.kentprodan.mediatheq.core.data.GenreRow
import io.github.kentprodan.mediatheq.core.data.HomeItems
import io.github.kentprodan.mediatheq.core.data.HomeShuffle
import io.github.kentprodan.mediatheq.core.data.HomeVideos
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import io.github.kentprodan.mediatheq.core.data.LibrarySyncRepository
import io.github.kentprodan.mediatheq.core.data.PlaylistRepository
import io.github.kentprodan.mediatheq.core.data.SyncStatus
import io.github.kentprodan.mediatheq.core.data.TvCarousel
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.data.VideoRepository
import io.github.kentprodan.mediatheq.core.data.VideoSort
import io.github.kentprodan.mediatheq.core.data.genreRows
import io.github.kentprodan.mediatheq.core.data.homeItems
import io.github.kentprodan.mediatheq.core.data.homeLibrary
import io.github.kentprodan.mediatheq.core.data.mixTracks
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.playback.PlaybackConnection
import io.github.kentprodan.mediatheq.core.playback.TrackTaps
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
@OptIn(ExperimentalCoroutinesApi::class)
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
        private val videos: VideoRepository? = null,
        private val appMode: AppModeRepository? = null,
        private val userData: UserDataRepository? = null,
    ) : ViewModel() {
        private val mode = appMode?.mode ?: flowOf(AppMode.Music)

        /** The video sections' items (#431), read only while the TV's mode has video. */
        private val homeVideos: Flow<HomeVideos> =
            mode.map { it.video }.distinctUntilChanged().flatMapLatest { video ->
                val repo = videos
                if (!video || repo == null) {
                    flowOf(HomeVideos())
                } else {
                    combine(
                        repo.continueWatching(VIDEO_LIMIT),
                        repo.nextUpEpisodes(VIDEO_LIMIT),
                        repo.recentlyAdded(VideoKind.Movie, VIDEO_LIMIT),
                        repo.recentlyAdded(VideoKind.Episode, VIDEO_LIMIT * EPISODES_PER_SERIES),
                        ::HomeVideos,
                    )
                }
            }

        /** The rows and the carousel's items; `null` while the library is empty (the sync decides what shows). */
        private val rows =
            combine(
                combine(library.homeLibrary(), homeVideos) { music, video -> music.copy(videos = video) },
                preferences.preferences
                    .map {
                        it.home to
                            Triple(
                                it.navigation.tvCarousel,
                                it.navigation.tvCarouselSource,
                                it.navigation.tvCarouselSkipWatched,
                            )
                    }.distinctUntilChanged(),
                shuffle.seed,
                mode,
            ) { library, (home, carousel), seed, mode ->
                val (style, chosen, skipWatched) = carousel
                val noMusic = library.albums.isEmpty() && library.tracks.isEmpty()
                if (noMusic && (!mode.video || library.videos == HomeVideos())) return@combine null
                val now = clock.now()

                // The carousel (#521) reads its section whether or not that row shows.
                fun slides(section: HomeSection) =
                    if (style == TvCarousel.Off) {
                        emptyList()
                    } else {
                        featured(
                            tvItems(section, homeItems(section, library, FEATURED_FETCH, seed, now), FEATURED_FETCH),
                            skipWatched,
                        )
                    }
                var source = carouselSource(chosen, mode)
                var featured = slides(source)
                // Automatic: new albums when no new movie is left to show.
                if (featured.isEmpty() && source != chosen && HomeSection.RecentlyAddedAlbums.allowed(mode)) {
                    source = HomeSection.RecentlyAddedAlbums
                    featured = slides(source)
                }
                val shownRows =
                    // The order and the shown sections from Settings -> Home rows (#431), music and video together.
                    home.shown
                        .filter { it.allowed(mode) }
                        .mapNotNull { section ->
                            val settings = home.settingsOf(section)
                            val items =
                                tvItems(
                                    section,
                                    homeItems(section, library, settings.maxItems, seed, now),
                                    settings.maxItems,
                                )
                            val counts =
                                if (section == HomeSection.RecentlyAddedEpisodes) {
                                    newEpisodeCounts(library.videos.recentEpisodes)
                                } else {
                                    emptyMap()
                                }
                            TvHomeRow(section, items, counts).takeUnless { items.isEmpty && settings.hideIfEmpty }
                        }
                HomeData(shownRows, source, featured)
            }.flowOn(defaultDispatcher)

        /** The genre rows (#431): read only with video and genres picked. */
        private val genreRows: Flow<List<GenreRow>> =
            combine(mode.map { it.video }.distinctUntilChanged(), preferences.homeGenres) { video, genres ->
                genres.takeIf { video }.orEmpty()
            }.distinctUntilChanged()
                .flatMapLatest { genres ->
                    val repo = videos
                    if (genres.isEmpty() || repo == null) {
                        flowOf(emptyList())
                    } else {
                        combine(
                            repo.movies(VideoSort.DateAdded, unwatched = false),
                            repo.series(VideoSort.DateAdded, unwatched = false),
                        ) { movies, series -> genreRows(genres, movies, series, VIDEO_LIMIT) }
                    }
                }.flowOn(defaultDispatcher)

        /** Settings -> Display and Home rows (#518, #521): how Home draws. */
        private val looks =
            preferences.preferences
                .map { Triple(it.navigation.tvCarousel, it.navigation.tvCardStyles, it.navigation.tvImmersiveRows) }
                .distinctUntilChanged()

        /** The status, and whether a full sync ever completed (then an empty library is really empty). */
        private val status =
            sync.status.scan(null as Pair<SyncStatus, Boolean>?) { previous, status ->
                status to (previous?.second == true || status.lastSyncedAt != null)
            }

        val uiState: StateFlow<TvHomeUiState> =
            combine(rows, genreRows, status, looks) { data, genreRows, status, (carousel, cards, immersive) ->
                val (current, everSynced) = status ?: return@combine TvHomeUiState.Loading
                when {
                    data != null -> {
                        TvHomeUiState.Content(
                            data.rows,
                            genreRows,
                            cards,
                            carousel,
                            data.featuredSection,
                            data.featured,
                            immersive,
                        )
                    }

                    everSynced -> {
                        TvHomeUiState.Empty
                    }

                    current is SyncStatus.Failed -> {
                        TvHomeUiState.Error(current.error)
                    }

                    current is SyncStatus.Running -> {
                        TvHomeUiState.Syncing(current.fetched, current.total)
                    }

                    else -> {
                        TvHomeUiState.Syncing()
                    }
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

                    is TvHomeAction.RemoveFromResume -> {
                        userData?.removeFromResume(action.id)
                    }

                    TvHomeAction.Sync -> {
                        sync.sync()
                    }
                }
            }
        }

        private companion object {
            const val VIDEO_LIMIT = 20

            /** Read for the carousel before watched items are left out (#521). */
            const val FEATURED_FETCH = 20

            /** Episodes read per series shown in Recently added episodes, so a season drop still fills the row. */
            const val EPISODES_PER_SERIES = 5
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

/**
 * The TV's take on a section's items (#431): Recently added episodes keep one card per series, the newest episode,
 * with how many arrived ([TvHomeRow.newEpisodes]).
 */
internal fun tvItems(
    section: HomeSection,
    items: HomeItems,
    limit: Int,
): HomeItems =
    if (section == HomeSection.RecentlyAddedEpisodes && items is HomeItems.Videos) {
        items.copy(videos = items.videos.distinctBy { it.seriesId ?: it.id }.take(limit))
    } else {
        items
    }

private data class HomeData(
    val rows: List<TvHomeRow>,
    val featuredSection: HomeSection,
    val featured: List<Featured>,
)

/** The carousel's section (#521): the chosen one while the [mode] has it, else new movies with video, new albums. */
internal fun carouselSource(
    chosen: HomeSection?,
    mode: AppMode,
): HomeSection =
    chosen?.takeIf { it.allowed(mode) }
        ?: if (mode.video) HomeSection.RecentlyAddedMovies else HomeSection.RecentlyAddedAlbums

/** The carousel's slides (#521): up to [FEATURED_COUNT] videos, albums or playlists; watched videos left out on demand. */
internal fun featured(
    items: HomeItems,
    skipWatched: Boolean,
): List<Featured> =
    when (items) {
        is HomeItems.Videos -> {
            items.videos.filterNot { skipWatched && it.userData.played }.map(Featured::Movie)
        }

        is HomeItems.Albums -> {
            items.albums.map(Featured::AlbumItem)
        }

        is HomeItems.Playlists -> {
            items.playlists.map(Featured::PlaylistItem)
        }

        else -> {
            emptyList()
        }
    }.take(FEATURED_COUNT)

/** The guideline's 5 to 8 slides. */
internal const val FEATURED_COUNT = 6

/** How many of [episodes] each series has, for "5 new episodes". */
internal fun newEpisodeCounts(episodes: List<Video>): Map<MediaId, Int> =
    episodes.mapNotNull { it.seriesId }.groupingBy { it }.eachCount()
