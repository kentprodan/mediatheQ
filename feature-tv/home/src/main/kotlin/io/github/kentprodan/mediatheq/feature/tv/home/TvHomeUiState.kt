package io.github.kentprodan.mediatheq.feature.tv.home

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.GenreItem
import io.github.kentprodan.mediatheq.core.data.GenreRow
import io.github.kentprodan.mediatheq.core.data.HomeItems
import io.github.kentprodan.mediatheq.core.data.Mix
import io.github.kentprodan.mediatheq.core.data.TvCardStyle
import io.github.kentprodan.mediatheq.core.data.TvCarousel
import io.github.kentprodan.mediatheq.core.data.genreRows
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.Video

/** What Home on TV shows (#236). */
sealed interface TvHomeUiState {
    /** The local library is being read. */
    data object Loading : TvHomeUiState

    /** Nothing on the TV yet and the first sync runs: [fetched] of [total] items of its current step (0 of 0 at first). */
    data class Syncing(
        val fetched: Int = 0,
        val total: Int = 0,
    ) : TvHomeUiState

    /** The phone's Home sections in its order, each a row; empty ones left out when their settings say so. */
    data class Content(
        val rows: List<TvHomeRow>,
        /** The genres picked in Settings -> Home rows (#431), placed by [rowOrder]. */
        val genreRows: List<GenreRow> = emptyList(),
        /** Settings -> Home rows (#518): a row's card variant; the rest keep their defaults. */
        val cardStyles: Map<HomeSection, TvCardStyle> = emptyMap(),
        /** The featured carousel (#521): its style, the section it shows and its items (none hides it). */
        val carousel: TvCarousel = TvCarousel.Off,
        val featuredSection: HomeSection = HomeSection.RecentlyAddedMovies,
        val featured: List<Featured> = emptyList(),
        /** Each slide's own section when they mix several (#575); a slide not listed uses [featuredSection]. */
        val slideSections: Map<MediaId, HomeSection> = emptyMap(),
        /** Rows shown as immersive lists (#521). */
        val immersiveRows: Set<HomeSection> = emptySet(),
        /** Settings -> Home rows (#604): how many cards of a row fit on screen; the rest keep their defaults. */
        val cardsPerScreen: Map<HomeSection, Int> = emptyMap(),
        /** Settings -> Home rows (#621): the rows' order, sections and genres together (`tvRowIds`). */
        val rowOrder: List<String> = emptyList(),
        /** Settings -> Home rows (#621): a genre row's card variant and how many cards fit on screen. */
        val genreCardStyles: Map<String, TvCardStyle> = emptyMap(),
        val genreCardsPerScreen: Map<String, Int> = emptyMap(),
    ) : TvHomeUiState

    /** Synced, but the server has no music. */
    data object Empty : TvHomeUiState

    /** Nothing on the TV yet and the first sync failed. */
    data class Error(
        val error: MqError,
    ) : TvHomeUiState
}

/** One slide of the featured carousel (#521). */
sealed interface Featured {
    val id: MediaId

    data class Movie(
        val video: Video,
    ) : Featured {
        override val id get() = video.id
    }

    data class AlbumItem(
        val album: Album,
    ) : Featured {
        override val id get() = album.id
    }

    data class PlaylistItem(
        val playlist: Playlist,
    ) : Featured {
        override val id get() = playlist.id
    }
}

data class TvHomeRow(
    val section: HomeSection,
    val items: HomeItems,
    /** Recently added episodes (#431): how many new episodes each series has. */
    val newEpisodes: Map<MediaId, Int> = emptyMap(),
)

/** What a cover's menu acts on. */
sealed interface TvHomeTarget {
    data class Album(
        val id: MediaId,
    ) : TvHomeTarget

    data class Artist(
        val id: MediaId,
    ) : TvHomeTarget

    data class Playlist(
        val id: MediaId,
    ) : TvHomeTarget

    data class Song(
        val track: Track,
    ) : TvHomeTarget
}

sealed interface TvHomeAction {
    /** The menu's Play and Shuffle: the target's tracks in order, or shuffled. */
    data class Play(
        val target: TvHomeTarget,
        val shuffle: Boolean = false,
    ) : TvHomeAction

    data class AddToQueue(
        val target: TvHomeTarget,
    ) : TvHomeAction

    /** Select on a track of a row: the Default track action on the row's tracks (as on the phone). */
    data class PlayTracks(
        val tracks: List<Track>,
        val index: Int,
    ) : TvHomeAction

    /** A mix card (#128). */
    data class PlayMix(
        val mix: Mix,
    ) : TvHomeAction

    /** A Continue watching card's menu (#431): the video leaves the row, on the server too. */
    data class RemoveFromResume(
        val id: MediaId,
    ) : TvHomeAction

    /** Retry from the error and empty states. */
    data object Sync : TvHomeAction
}
