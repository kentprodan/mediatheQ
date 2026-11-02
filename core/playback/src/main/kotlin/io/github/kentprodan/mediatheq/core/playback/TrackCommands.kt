package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.data.DownloadRepository
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.data.SwipeAction
import io.github.kentprodan.mediatheq.core.data.TopButton
import io.github.kentprodan.mediatheq.core.data.UserDataRepository
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** What a swipe leaves to the screen: a dialog or a page of its own. */
enum class SwipeFollowUp { Rate, AddToPlaylist }

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
         * The swipe chosen for the [right] (or left) side on [track]; returns what the screen still has to open, or
         * `null`. Rate does nothing while ratings are off.
         */
        suspend fun swipe(
            track: Track,
            right: Boolean,
        ): SwipeFollowUp? {
            val prefs = preferences.preferences.first()
            val action = if (right) prefs.mediaLists.swipeRight else prefs.mediaLists.swipeLeft
            return when (action) {
                SwipeAction.Disabled -> null
                SwipeAction.AddToQueue -> null.also { playback.addToQueue(listOf(track)) }
                SwipeAction.PlayNext -> null.also { playback.playNext(listOf(track)) }
                SwipeAction.Download -> null.also { downloads.download(listOf(track)) }
                SwipeAction.ToggleFavorite -> null.also { toggleFavorite(listOf(track)) }
                SwipeAction.AddToPlaylist -> SwipeFollowUp.AddToPlaylist
                SwipeAction.Rate -> SwipeFollowUp.Rate.takeIf { prefs.ratingMode != RatingMode.Off }
            }
        }
    }
