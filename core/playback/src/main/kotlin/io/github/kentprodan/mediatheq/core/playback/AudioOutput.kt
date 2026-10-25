package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession

/**
 * Allows offload or not ([AudioPolicy.offload]). Only when settings or the route change: a change makes the player
 * select tracks again, which would drop the next item it buffered for a gapless transition.
 */
@UnstableApi
internal fun Player.applyOffload(allowed: Boolean) {
    val preferences =
        AudioOffloadPreferences
            .Builder()
            .setAudioOffloadMode(
                if (allowed) {
                    AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                } else {
                    AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                },
            )
            // Albums play gapless: formats with encoder delay offload only where the device keeps them gapless.
            .setIsGaplessSupportRequired(true)
            .setIsSpeedChangeSupportRequired(false)
            .build()
    trackSelectionParameters = trackSelectionParameters.buildUpon().setAudioOffloadPreferences(preferences).build()
}

/** Sets each track's normalization volume when it becomes current, and again when [apply] is called. */
internal class NormalizationListener(
    private val player: Player,
    private val settings: () -> AudioSettings,
    private val mode: () -> AudioMode,
) : Player.Listener {
    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) = apply()

    /** Sets the current track's volume now, e.g. after the mode changed (bit-perfect plays at full volume). */
    fun apply() {
        val gains = player.currentMediaItem?.gains() ?: ItemGains(null, null)
        player.volume = AudioPolicy.volume(gains, settings().normalization, mode())
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
    // The defaults Media3 1.11 gives the controller (never the deprecated onConnect result: it has no commands, #53).
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
