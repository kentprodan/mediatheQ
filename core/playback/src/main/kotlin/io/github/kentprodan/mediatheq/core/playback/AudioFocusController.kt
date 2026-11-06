package io.github.kentprodan.mediatheq.core.playback

import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioFocusRequestCompat
import androidx.media3.common.audio.AudioManagerCompat
import androidx.media3.common.util.UnstableApi
import io.github.kentprodan.mediatheq.core.data.AudioFocusPreferences
import io.github.kentprodan.mediatheq.core.data.ShortFocusAction

/** What a focus change does to playback (#139). */
internal sealed interface FocusReaction {
    /** Lower the volume ([DUCK_VOLUME]) and play on. */
    data object Duck : FocusReaction

    /** Pause; [resume] = play again when the focus comes back. */
    data class Pause(
        val resume: Boolean,
    ) : FocusReaction

    /** The focus is back: full volume, and play again what [Pause] paused to resume. */
    data object Regain : FocusReaction

    data object Nothing : FocusReaction
}

/**
 * Settings -> Playback -> Audio focus: a short interruption (navigation, a notification) ducks, pauses or does nothing
 * (bit-perfect has no volume to lower, so it pauses); a long one (a call) pauses and plays on afterwards unless
 * [AudioFocusPreferences.noResumeAfterLongLoss]; losing the focus for good (another player) pauses.
 */
internal fun focusReaction(
    change: Int,
    settings: AudioFocusPreferences,
    bitPerfect: Boolean,
): FocusReaction =
    when (change) {
        AudioManager.AUDIOFOCUS_GAIN -> {
            FocusReaction.Regain
        }

        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
            when (settings.shortAction) {
                ShortFocusAction.Duck -> if (bitPerfect) FocusReaction.Pause(resume = true) else FocusReaction.Duck
                ShortFocusAction.Pause -> FocusReaction.Pause(resume = true)
                ShortFocusAction.Nothing -> FocusReaction.Nothing
            }
        }

        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
            FocusReaction.Pause(resume = !settings.noResumeAfterLongLoss)
        }

        AudioManager.AUDIOFOCUS_LOSS -> {
            FocusReaction.Pause(resume = false)
        }

        else -> {
            FocusReaction.Nothing
        }
    }

/** The volume while ducked, of the normalized one (Media3's own value). */
internal const val DUCK_VOLUME = 0.2f

/**
 * The service's own audio focus (#139, ADR 0031): ExoPlayer runs with `handleAudioFocus = false`, so a short
 * interruption can pause or be ignored instead of ducking. Focus is asked for whenever [player] is meant to play (also
 * a play the session or the system starts); refused, playback does not start; delayed (a call is on), it starts when
 * the focus comes. A pause or play by anyone but us during an interruption cancels its resume.
 */
@UnstableApi
internal class AudioFocusController(
    private val audioManager: AudioManager,
    private val player: Player,
    private val settings: () -> AudioFocusPreferences,
    private val bitPerfect: () -> Boolean,
    /** Ducked or not: the volume is set again. */
    private val onDuck: (Boolean) -> Unit,
) : Player.Listener {
    private var holding = false
    private var resumeOnGain = false
    private var ducked = false

    // Our own pause or play, as the listener will see it: not the user's. (A change made inside the listener reaches it
    // only after the listener returns.)
    private var expected: Boolean? = null

    // Media3's request (as ExoPlayer's own focus handling uses), built from Media3 attributes.
    private val request =
        AudioFocusRequestCompat
            .Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
            )
            // Without it the system ducks by itself and never tells us: Pause and Nothing would not work.
            .setWillPauseWhenDucked(true)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(::onFocusChange, Handler(Looper.getMainLooper()))
            .build()

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) {
        if (expected == playWhenReady) {
            expected = null
            return
        }
        expected = null
        resumeOnGain = false
        if (playWhenReady) requestFocus() else abandon()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
            if (!player.playWhenReady || playbackState == Player.STATE_ENDED) abandon()
        }
    }

    private fun requestFocus() {
        if (holding) return
        when (AudioManagerCompat.requestAudioFocus(audioManager, request)) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                holding = true
            }

            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                holding = true
                resumeOnGain = true
                ours(play = false)
            }

            else -> {
                ours(play = false)
            }
        }
    }

    private fun abandon() {
        if (!holding) return
        holding = false
        setDucked(false)
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, request)
    }

    private fun onFocusChange(change: Int) {
        when (val reaction = focusReaction(change, settings(), bitPerfect())) {
            FocusReaction.Duck -> {
                setDucked(true)
            }

            is FocusReaction.Pause -> {
                setDucked(false)
                // Only a pause of what was playing can resume.
                resumeOnGain = reaction.resume && (player.playWhenReady || resumeOnGain)
                ours(play = false)
                if (change == AudioManager.AUDIOFOCUS_LOSS) abandon()
            }

            FocusReaction.Regain -> {
                setDucked(false)
                if (resumeOnGain) {
                    resumeOnGain = false
                    ours(play = true)
                }
            }

            FocusReaction.Nothing -> {
                Unit
            }
        }
    }

    private fun setDucked(on: Boolean) {
        if (ducked == on) return
        ducked = on
        onDuck(on)
    }

    private fun ours(play: Boolean) {
        if (player.playWhenReady == play) return
        expected = play
        player.playWhenReady = play
    }
}
