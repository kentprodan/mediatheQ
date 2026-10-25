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
) {
    private var settings = AudioSettings()
    private var preferred: MixerCapability? = null

    var state = AudioOutputState()
        private set

    fun setSettings(settings: AudioSettings) {
        this.settings = settings
        refresh()
    }

    /** Decides again: call on a track change, a playback stop and a device change. */
    fun refresh() {
        val capabilities = port.dacCapabilities()
        val route = if (capabilities != null) AudioRoute.UsbDac else AudioRoute.Other
        val format = if (AudioPolicy.wantsBitPerfect(settings, route)) currentFormat() else null
        val wanted = format?.let { MixerAttributeSelector.select(capabilities.orEmpty(), it) }
        val chosen = wanted?.takeIf { it == preferred || port.setPreferred(it) }
        if (chosen == null && preferred != null) port.clearPreferred()
        preferred = chosen
        val next =
            AudioOutputState(
                route = route,
                offload = AudioPolicy.offload(settings, route),
                mode = AudioPolicy.decide(settings, route, bitPerfectFormat = chosen != null),
                bitPerfectFormat = chosen?.format,
                bitPerfectNoMatch = format != null && chosen == null,
            )
        if (next != state) {
            state = next
            onChange(next)
        }
    }

    fun release() {
        if (preferred != null) port.clearPreferred()
        preferred = null
    }
}
