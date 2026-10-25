package io.github.kentprodan.mediatheq.core.playback

/**
 * A PCM output format as the platform describes it: [encoding] is an `AudioFormat.ENCODING_PCM_*` value (Media3's
 * `C.ENCODING_PCM_*` are the same numbers), [channelMask] an `AudioFormat.CHANNEL_OUT_*` mask.
 */
internal data class PcmFormat(
    val encoding: Int,
    val sampleRateHz: Int,
    val channelMask: Int,
)

/** One mixer attribute a USB device supports, as plain data (no `AudioMixerAttributes` outside the adapter). */
internal data class MixerCapability(
    val format: PcmFormat,
    val bitPerfect: Boolean,
)

/** Picks the bit-perfect mixer attribute for a track (media3 skill, section 6). Pure. */
internal object MixerAttributeSelector {
    /**
     * The bit-perfect capability whose format is exactly [output], or `null`: playback attaches to a bit-perfect
     * mixer only when encoding, sample rate and channel mask all match.
     */
    fun select(
        capabilities: List<MixerCapability>,
        output: PcmFormat,
    ): MixerCapability? = capabilities.firstOrNull { it.bitPerfect && it.format == output }

    /**
     * The format the player sends to the sink for a track with this [sampleRateHz] and [channels]: Media3's default
     * sink outputs 16-bit PCM (a 24-bit file loses its bit depth, not its sample rate). `null` when unknown or more
     * than stereo.
     */
    fun outputFormat(
        sampleRateHz: Int?,
        channels: Int?,
    ): PcmFormat? {
        if (sampleRateHz == null || sampleRateHz <= 0) return null
        val mask =
            when (channels) {
                1 -> CHANNEL_OUT_MONO
                2 -> CHANNEL_OUT_STEREO
                else -> return null
            }
        return PcmFormat(ENCODING_PCM_16BIT, sampleRateHz, mask)
    }

    // The platform's values (android.media.AudioFormat), kept here so the selector stays pure Kotlin.
    const val ENCODING_PCM_16BIT = 2
    const val CHANNEL_OUT_MONO = 4
    const val CHANNEL_OUT_STEREO = 12
}
