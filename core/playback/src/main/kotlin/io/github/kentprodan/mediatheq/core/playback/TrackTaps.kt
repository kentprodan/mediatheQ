package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.TrackAction
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
        /** The user tapped [tracks]`[index]`; [tracks] is the list it was tapped in. */
        suspend fun tap(
            tracks: List<Track>,
            index: Int,
        ) {
            playback.playTrack(
                tracks,
                index,
                preferences.preferences
                    .first()
                    .navigation.trackAction,
            )
        }
    }

/** Plays [tracks]`[index]` as [action] says; an index outside [tracks] does nothing. */
fun PlaybackConnection.playTrack(
    tracks: List<Track>,
    index: Int,
    action: TrackAction,
) {
    val track = tracks.getOrNull(index) ?: return
    when (action) {
        TrackAction.Play -> play(tracks, startIndex = index)
        TrackAction.PlayOnlyThis -> play(listOf(track))
        TrackAction.PlayNext -> playNext(listOf(track))
        TrackAction.AddToQueue -> addToQueue(listOf(track))
    }
}
