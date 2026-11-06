package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.TrackAction
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** A tap on a track in any list, done as Settings -> Navigation and media start -> Default track action says (#122). */
class TrackTaps
    @Inject
    constructor(
        private val playback: PlaybackConnection,
        private val preferences: LibraryPreferencesRepository,
    ) {
        /**
         * The user tapped [tracks]`[index]`; [tracks] is the list it was tapped in, from playlist [playlistId] if any.
         * [action] is a page's own choice (Search, #136); `null` = the Default track action.
         */
        suspend fun tap(
            tracks: List<Track>,
            index: Int,
            playlistId: MediaId? = null,
            action: TrackAction? = null,
        ) {
            playback.playTrack(
                tracks,
                index,
                action ?: preferences.preferences
                    .first()
                    .navigation.trackAction,
                playlistId,
            )
        }
    }

/** Plays [tracks]`[index]` as [action] says; an index outside [tracks] does nothing. */
fun PlaybackConnection.playTrack(
    tracks: List<Track>,
    index: Int,
    action: TrackAction,
    playlistId: MediaId? = null,
) {
    val track = tracks.getOrNull(index) ?: return
    when (action) {
        TrackAction.Play -> play(tracks, startIndex = index, playlistId = playlistId)
        TrackAction.PlayOnlyThis -> play(listOf(track), playlistId = playlistId)
        TrackAction.PlayNext -> playNext(listOf(track))
        TrackAction.AddToQueue -> addToQueue(listOf(track))
    }
}
