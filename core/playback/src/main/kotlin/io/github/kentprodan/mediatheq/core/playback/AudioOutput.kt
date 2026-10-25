package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession

/**
 * Applies [mode]'s offload preference. Only when settings change (once at start for now): a change makes the player
 * select tracks again, which would drop the next item it buffered for a gapless transition.
 */
@UnstableApi
internal fun Player.applyAudioMode(mode: AudioMode) {
    val preferences =
        AudioOffloadPreferences
            .Builder()
            .setAudioOffloadMode(
                when (mode) {
                    AudioMode.Offload -> AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                    AudioMode.Processed -> AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                },
            )
            // Albums play gapless: formats with encoder delay offload only where the device keeps them gapless.
            .setIsGaplessSupportRequired(true)
            .setIsSpeedChangeSupportRequired(false)
            .build()
    trackSelectionParameters = trackSelectionParameters.buildUpon().setAudioOffloadPreferences(preferences).build()
}

/** Sets each track's normalization volume when it becomes current. */
internal class NormalizationListener(
    private val player: Player,
    private val settings: () -> AudioSettings,
) : Player.Listener {
    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        player.volume = AudioPolicy.volume(mediaItem?.gains() ?: ItemGains(null, null), settings().normalization)
    }
}

/**
 * Accepts [controller] with the default commands for it, minus setting the player volume, which carries the
 * normalization gain (controllers change the device volume instead).
 */
@UnstableApi
internal fun acceptWithoutPlayerVolume(
    session: MediaSession,
    controller: MediaSession.ControllerInfo,
): MediaSession.ConnectionResult {
    // Not super.onConnect: since Media3 1.11 it returns a "not implemented" marker without any commands (#53).
    val defaults = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).build()
    return MediaSession.ConnectionResult
        .AcceptedResultBuilder(session, controller)
        .setAvailablePlayerCommands(
            defaults.availablePlayerCommands
                .buildUpon()
                .remove(Player.COMMAND_SET_VOLUME)
                .build(),
        ).build()
}
