package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.SwipeAction
import io.github.kentprodan.mediatheq.core.data.TopButton
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * The commands every media list shares (#123): the header's top buttons, the selection toolbar and the swipe actions.
 * Next to [TrackTaps] because most of them queue; favorites and downloads go to their repositories as the screens'
 * own menus do.
 */
class TrackCommands
    @Inject
    constructor(
        private val playback: PlaybackConnection,
        private val userData: UserDataRepository,
        private val downloads: DownloadRepository,
        private val preferences: LibraryPreferencesRepository,
    ) {
        /** A top button (or the selection's Play) on [tracks], in their order; nothing for an empty list. */
        fun run(
            button: TopButton,
            tracks: List<Track>,
        ) {
            if (tracks.isEmpty()) return
            when (button) {
                TopButton.Play -> playback.play(tracks)
                TopButton.Shuffle -> playback.play(tracks, shuffle = true)
                TopButton.PlayNext -> playback.playNext(tracks)
                TopButton.AddToQueue -> playback.addToQueue(tracks)
            }
        }

        fun download(tracks: List<Track>) {
            if (tracks.isNotEmpty()) downloads.download(tracks)
        }

        /** Makes every one of [tracks] a favorite; when all already are, removes them all. */
        suspend fun toggleFavorite(tracks: List<Track>) {
            val favorite = !tracks.all { it.userData.isFavorite }
            tracks.filter { it.userData.isFavorite != favorite }.forEach { userData.setFavorite(it.id, favorite) }
        }

        suspend fun rate(
            track: Track,
            halfStars: Int?,
        ) {
            userData.setRating(track.id, halfStars)
        }

        /**
         * Runs the swipe chosen for the [right] (or left) side on [track] and returns it. Add to playlist and Rate open
         * a picker or dialog in the screen (`trackSwipes`), so they do nothing here.
         */
        suspend fun swipe(
            track: Track,
            right: Boolean,
        ): SwipeAction {
            val prefs = preferences.preferences.first().mediaLists
            val action = if (right) prefs.swipeRight else prefs.swipeLeft
            when (action) {
                SwipeAction.AddToQueue -> playback.addToQueue(listOf(track))
                SwipeAction.PlayNext -> playback.playNext(listOf(track))
                SwipeAction.Download -> downloads.download(listOf(track))
                SwipeAction.ToggleFavorite -> toggleFavorite(listOf(track))
                SwipeAction.Disabled, SwipeAction.AddToPlaylist, SwipeAction.Rate -> Unit
            }
            return action
        }
    }
