package io.github.kentprodan.mediatheq.feature.tv.home

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.GenreItem
import io.github.kentprodan.mediatheq.core.data.GenreRow
import io.github.kentprodan.mediatheq.core.data.HomeItems
import io.github.kentprodan.mediatheq.core.data.Mix
import io.github.kentprodan.mediatheq.core.data.TvCardStyle
import io.github.kentprodan.mediatheq.core.data.TvHomeHero
import io.github.kentprodan.mediatheq.core.data.genreRows
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.MediaId
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
        /** The genres picked in Settings -> Home rows (#431), shown after the video rows. */
        val genreRows: List<GenreRow> = emptyList(),
        /** Settings -> Display -> Home header (#504). */
        val hero: TvHomeHero = TvHomeHero.None,
        /** Settings -> Home rows (#518): a row's card variant; the rest keep their defaults. */
        val cardStyles: Map<HomeSection, TvCardStyle> = emptyMap(),
    ) : TvHomeUiState

    /** Synced, but the server has no music. */
    data object Empty : TvHomeUiState

    /** Nothing on the TV yet and the first sync failed. */
    data class Error(
        val error: MqError,
    ) : TvHomeUiState
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
