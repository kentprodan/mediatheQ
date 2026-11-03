package io.github.kentprodan.mediatheq.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.kentprodan.mediatheq.core.designsystem.component.MqRatingDialog
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track

/**
 * What a list does with a swipe on a track row ([TrackRow]'s `onSwipe`, #123): Add to playlist opens the playlist
 * picker ([onAddToPlaylist]), Rate opens a rating dialog here ([onRate] on a choice), every other action goes to
 * [onCommand] (the screen's ViewModel runs it).
 */
@Composable
fun trackSwipes(
    onAddToPlaylist: (List<MediaId>) -> Unit,
    onRate: (Track, Int?) -> Unit,
    onCommand: (Track, right: Boolean) -> Unit,
): (Track, right: Boolean) -> Unit {
    val style = LocalMediaListStyle.current
    var rating by remember { mutableStateOf<Track?>(null) }
    rating?.let { track ->
        MqRatingDialog(
            track.title,
            track.userData.halfStars,
            allowHalf = style.halfStars == true,
            onRate = {
                onRate(track, it)
                rating = null
            },
            onDismiss = { rating = null },
        )
    }
    return { track, right ->
        when (if (right) style.swipeRight else style.swipeLeft) {
            TrackSwipe.AddToPlaylist -> {
                onAddToPlaylist(listOf(track.id))
            }

            TrackSwipe.Rate -> {
                rating = track
            }

            null -> {}

            else -> {
                onCommand(track, right)
            }
        }
    }
}
