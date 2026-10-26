package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

internal class SkipFailedTrackListener(
    private val player: Player,
    private val onError: (PlaybackError) -> Unit,
) : Player.Listener {
    override fun onPlayerError(error: PlaybackException) {
    }
}
