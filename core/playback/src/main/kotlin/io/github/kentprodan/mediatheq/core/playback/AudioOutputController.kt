package io.github.kentprodan.mediatheq.core.playback

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
    /**
     * The player opened a new AudioTrack at [bitPerfectFormat]'s rate after the preference was set, with no AudioTrack
     * of its own open before: only such a track reaches the bit-perfect output (#65, Pixel 9). One already open when
     * the preference is set (the setting turned on mid-track) stays on the normal mixer until it is released. Best
     * effort: another app on the DAC can still block it. Only then does the indicator say bit-perfect.
     */
    val bitPerfectConfirmed: Boolean = false,
)

/**
 * Keeps the USB DAC's preferred mixer attribute in line with the settings, the route and the playing track (media3
 * skill, section 6), and reports the resulting [AudioOutputState] through [onChange]. Main thread only.
 */
internal class AudioOutputController(
    private val port: UsbAudioPort,
    /** The playing track's output format; `null` when stopped or unknown. */
    private val currentFormat: () -> PcmFormat?,
    private val onChange: (AudioOutputState) -> Unit,
    /** Whether the playing track is a converted Dolby Atmos track; asked on every refresh. */
    private val atmosConverted: () -> Boolean,
    /** One line per decision, for logcat (#65): what bit-perfect does on a real DAC is only seen on the device. */
    private val log: (String) -> Unit = {},
) {
    private var settings = AudioSettings()
    private var preferred: MixerCapability? = null

    /** The sample rate of the player's open AudioTrack, `null` when none is open. */
    private var openedRateHz: Int? = null
    private var confirmed = false

    /** Bit-perfect failed on this DAC ([onBitPerfectFailed]): normal playback until the device or the setting changes. */
    private var suspended = false

    var state = AudioOutputState()
        private set

    fun setSettings(settings: AudioSettings) {
        if (settings.bitPerfectUsb != this.settings.bitPerfectUsb) suspended = false
        this.settings = settings
        refresh("settings")
    }

    /** An output device was added or removed. */
    fun onDeviceChanged() {
        suspended = false
        refresh("device")
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

    /** Decides again: call on a track change, a playback state change and a device change; [reason] is logged. */
    fun refresh(reason: String) {
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
                bitPerfectConfirmed = confirmed && mode == AudioMode.BitPerfect,
            ),
        )
    }

    /** The player opened an AudioTrack at [sampleRateHz]. */
    fun onAudioTrackOpened(sampleRateHz: Int) {
        confirmed = openedRateHz == null && preferred?.format?.sampleRateHz == sampleRateHz
        openedRateHz = sampleRateHz
        log("AudioTrack opened at $sampleRateHz Hz, bit-perfect=$confirmed")
        publish(state.copy(bitPerfectConfirmed = confirmed && state.mode == AudioMode.BitPerfect))
    }

    fun onAudioTrackReleased() {
        openedRateHz = null
        confirmed = false
        publish(state.copy(bitPerfectConfirmed = false))
    }

    /** The platform dropped our preference (DAC re-enumerated, audio server restarted): set it again. */
    fun onPreferenceDropped() {
        if (preferred == null) return
        preferred = null
        refresh("dropped")
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
