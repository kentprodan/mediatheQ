package io.github.kentprodan.mediatheq.core.playback

/**
 * What reaches the output for the playing track (#125): [known] once its stream is resolved (a fresh transition or a
 * restored queue is not); [convertedKbps] the bitrate of the server's conversion, `null` for the original file.
 */
internal data class PlayedStream(
    /** The item this describes ([androidx.media3.common.MediaItem.mediaId]); a track change makes it stale. */
    val itemId: String? = null,
    val known: Boolean = false,
    val convertedKbps: Int? = null,
)

/** What the output does now, for the player (offload, volume, EQ) and for screens (indicator, EQ reason). */
internal data class AudioOutputState(
    val route: AudioRoute = AudioRoute.Other,
    val offload: Boolean = true,
    val mode: AudioMode = AudioMode.Offload,
    /** The format sent bit-perfect to the DAC, when [mode] is [AudioMode.BitPerfect]. */
    val bitPerfectFormat: PcmFormat? = null,
    /** Bit-perfect was wanted for a known track format, but the DAC has no matching attribute or refused it. */
    val bitPerfectNoMatch: Boolean = false,
    /** The playing track is Dolby Atmos but plays as the server's stereo conversion ([AudioPolicy.atmosConverted]). */
    val atmosConverted: Boolean = false,
    /** How the playing track's stream reaches the output, for Now Playing's quality label (#125). */
    val played: PlayedStream = PlayedStream(),
    /**
     * The player opened a new AudioTrack at [bitPerfectFormat]'s rate after the preference was set, with no AudioTrack
     * of its own open before: only such a track reaches the bit-perfect output (#65, Pixel 9). One already open when
     * the preference is set (the setting turned on mid-track) stays on the normal mixer until it is released. Best
     * effort: another app on the DAC can still block it. Only then does the indicator say bit-perfect.
     */
    val bitPerfectConfirmed: Boolean = false,
    /** The device media plays on (#140): its profile sets the settings. */
    val output: OutputDevice = OutputDevice.Phone,
)

/**
 * Keeps the USB DAC's preferred mixer attribute in line with the settings, the route and the playing track (media3
 * skill, section 6), and reports the resulting [AudioOutputState] through [onChange]. Main thread only.
 */
internal class AudioOutputController(
    private val port: AudioOutputPort,
    /** The playing track's output format; `null` when stopped or unknown. */
    private val currentFormat: () -> PcmFormat?,
    private val onChange: (AudioOutputState) -> Unit,
    /** Whether the playing track is a converted Dolby Atmos track; asked on every refresh. */
    private val atmosConverted: () -> Boolean,
    /** One line per decision, for logcat (#65): what bit-perfect does on a real DAC is only seen on the device. */
    private val log: (String) -> Unit = {},
    /** How the playing track's stream plays; asked on every refresh, like [atmosConverted]. */
    private val played: () -> PlayedStream = { PlayedStream() },
    /**
     * The preference was just set while an AudioTrack of ours is open (the setting turned on mid-track, or set again
     * after the platform dropped it): that track stays on the normal mixer, so the player must open a new one (#165).
     */
    private val reopen: () -> Unit = {},
    /** The last AudioTrack of the player was released: a reopen waiting for it can go on. */
    private val tracksReleased: () -> Unit = {},
) {
    private var settings = AudioSettings()
    private var preferred: MixerCapability? = null

    /** The player's open AudioTracks: an old one is released in the background, often after the next one opened. */
    private var openTracks = 0
    private var confirmed = false

    /** Bit-perfect failed on this DAC ([onBitPerfectFailed]): normal playback until the device or the setting changes. */
    private var suspended = false

    var state = AudioOutputState()
        private set

    fun setSettings(settings: AudioSettings) {
        val turnedOn = settings.bitPerfectUsb && !this.settings.bitPerfectUsb
        if (settings.bitPerfectUsb != this.settings.bitPerfectUsb) suspended = false
        this.settings = settings
        refresh("settings", reopenIfOpen = turnedOn)
    }

    /** An output device was added or removed. */
    fun onDeviceChanged() {
        suspended = false
        // A DAC plugged in mid-track with bit-perfect already on: the open track went to it through the mixer.
        refresh("device", reopenIfOpen = true)
    }

    /**
     * The player failed to play on the bit-perfect output (seen on a Pixel 9: every AudioTrack start refused while the
     * USB device re-enumerated). Drops the preference so playback goes on normally; `false` when none was set, so the
     * error is not ours to handle.
     */
    fun onBitPerfectFailed(): Boolean {
        if (preferred == null) return false
        suspended = true
        refresh("bit-perfect failed")
        return true
    }

    /**
     * Decides again: call on a track change, a playback state change and a device change; [reason] is logged. Only a
     * caller that knows the open AudioTrack predates the preference passes [reopenIfOpen]: on a transition the old track
     * is still draining and the next one opens after it, which already reaches the bit-perfect output.
     */
    fun refresh(
        reason: String,
        reopenIfOpen: Boolean = false,
    ) {
        val capabilities = port.dacCapabilities()
        val route = if (capabilities != null) AudioRoute.UsbDac else AudioRoute.Other
        val format = if (!suspended && AudioPolicy.wantsBitPerfect(settings, route)) currentFormat() else null
        val wanted = format?.let { MixerAttributeSelector.select(capabilities.orEmpty(), it) }
        val chosen = wanted?.takeIf { it == preferred || port.setPreferred(it) }
        val cleared = chosen == null && preferred != null
        if (cleared) port.clearPreferred()
        log("$reason: route=$route format=$format chosen=${chosen?.format} cleared=$cleared")
        val newlySet = chosen != null && chosen != preferred
        preferred = chosen
        if (chosen == null || newlySet) confirmed = false
        val mode = AudioPolicy.decide(settings, route, bitPerfectFormat = chosen != null)
        publish(
            AudioOutputState(
                route = route,
                offload = AudioPolicy.offload(settings, route),
                mode = mode,
                bitPerfectFormat = chosen?.format,
                bitPerfectNoMatch = format != null && chosen == null,
                atmosConverted = atmosConverted(),
                played = played(),
                bitPerfectConfirmed = confirmed && mode == AudioMode.BitPerfect,
                output = port.currentOutput(),
            ),
        )
        // After publishing: the offload change of this decision is applied first, then the track is reopened.
        // Still ours: the output's profile applied in publish may have changed the decision since.
        if (reopenIfOpen && newlySet && openTracks > 0 && preferred == chosen) {
            log("reopen: an AudioTrack predates the preference")
            reopen()
        }
    }

    /** The player opened an AudioTrack at [sampleRateHz]. */
    fun onAudioTrackOpened(sampleRateHz: Int) {
        confirmed = openTracks == 0 && preferred?.format?.sampleRateHz == sampleRateHz
        openTracks++
        log("AudioTrack opened at $sampleRateHz Hz, bit-perfect=$confirmed")
        publish(state.copy(bitPerfectConfirmed = confirmed && state.mode == AudioMode.BitPerfect))
    }

    fun onAudioTrackReleased() {
        openTracks = (openTracks - 1).coerceAtLeast(0)
        // A confirmed track was opened with none before it, so it is the last one open.
        if (openTracks == 0) confirmed = false
        publish(state.copy(bitPerfectConfirmed = confirmed && state.mode == AudioMode.BitPerfect))
        if (openTracks == 0) tracksReleased()
    }

    /** The platform dropped our preference (DAC re-enumerated, audio server restarted): set it again. */
    fun onPreferenceDropped() {
        if (preferred == null) return
        preferred = null
        refresh("dropped", reopenIfOpen = true)
    }

    fun release() {
        if (preferred != null) port.clearPreferred()
        preferred = null
    }

    private fun publish(next: AudioOutputState) {
        if (next != state) {
            state = next
            onChange(next)
        }
    }
}
