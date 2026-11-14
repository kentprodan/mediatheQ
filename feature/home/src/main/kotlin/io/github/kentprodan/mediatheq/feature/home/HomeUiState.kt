package io.github.kentprodan.mediatheq.feature.home

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.HomeItems
import io.github.kentprodan.mediatheq.core.data.Mix
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.HomeSectionSettings
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track

/** What the Home tab shows. */
sealed interface HomeUiState {
    /** The local library is being read (a few milliseconds). */
    data object Loading : HomeUiState

    /** Nothing on the phone yet and the first sync is running. */
    data object FirstSync : HomeUiState

    /**
     * The library has music: the shown sections (#127) in the chosen order, empty ones left out when their settings say
     * so. No section at all (all hidden) is still Content: "Shuffle all" stays.
     */
    data class Content(
        val sections: List<HomeSectionContent> = emptyList(),
        /** Pull to refresh: a sync runs. */
        val refreshing: Boolean = false,
        /** Shuffle all is music: hidden in Video mode (#275). */
        val showShuffle: Boolean = true,
    ) : HomeUiState

    /** Synced, but the server has no music. */
    data object Empty : HomeUiState

    /** Video mode (ADR 0051) with nothing to continue, next up or newly added. */
    data object VideoMode : HomeUiState

    /** Offline mode, and nothing for Home is on the phone (#63). */
    data object NothingDownloaded : HomeUiState

    /** Nothing on the phone yet and the first sync failed ([MqError.Network]: the server cannot be reached). */
    data class Error(
        val error: MqError,
    ) : HomeUiState
}

/** One Home section: what it is, how it looks and its items. */
data class HomeSectionContent(
    val section: HomeSection,
    val settings: HomeSectionSettings,
    val items: HomeItems,
)

sealed interface HomeAction {
    /** Plays the whole library shuffled. */
    data object ShuffleAll : HomeAction

    /** A Shortcuts button (#128): queues the whole mix and plays it. */
    data class PlayMix(
        val mix: Mix,
    ) : HomeAction

    /** A cover's play button (#182): the album's tracks in order. */
    data class PlayAlbum(
        val id: MediaId,
    ) : HomeAction

    /** A cover's play button (#182): the playlist in its order. */
    data class PlayPlaylist(
        val id: MediaId,
    ) : HomeAction

    /** A picture's play button (#182): the artist's albums in order. */
    data class PlayArtist(
        val id: MediaId,
    ) : HomeAction

    /** A track of a track section: plays the section's tracks from [index] (the default track action decides). */
    data class PlayTracks(
        val tracks: List<Track>,
        val index: Int,
    ) : HomeAction

    data class PlayNext(
        val track: Track,
    ) : HomeAction

    data class AddToQueue(
        val track: Track,
    ) : HomeAction

    data class SetFavorite(
        val track: Track,
        val isFavorite: Boolean,
    ) : HomeAction

    data class Download(
        val track: Track,
    ) : HomeAction

    /** Sync now, from the empty and error states. */
    data object Sync : HomeAction

    /** Pull to refresh: new random sections, and a sync when online. */
    data object Refresh : HomeAction

    /** The mode toggle at the top (ADR 0051). */
    data class SetMode(
        val mode: AppMode,
    ) : HomeAction
}
