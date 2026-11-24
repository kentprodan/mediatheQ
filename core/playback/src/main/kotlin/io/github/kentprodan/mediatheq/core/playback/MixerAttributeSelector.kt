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
     * The bit-perfect capability for [output]'s sample rate and channel mask, or `null`: playback attaches to a
     * bit-perfect mixer only when encoding, sample rate and channel mask all match the AudioTrack, and
     * [BitPerfectOutputProvider] opens the AudioTrack in the chosen encoding. A wider integer container carries the
     * samples unchanged (#629), so the order is: the pipeline's own width first ([floatPipeline]: the sink writes float,
     * which holds 24-bit samples exactly, so 24 then 32 bits before 16), then wider, then 16 bits.
     */
    fun select(
        capabilities: List<MixerCapability>,
        output: PcmFormat,
        floatPipeline: Boolean = false,
    ): MixerCapability? {
        val order =
            if (floatPipeline) {
                listOf(ENCODING_PCM_24BIT_PACKED, ENCODING_PCM_32BIT, ENCODING_PCM_16BIT)
            } else {
                listOf(ENCODING_PCM_16BIT, ENCODING_PCM_24BIT_PACKED, ENCODING_PCM_32BIT)
            }
        return order.firstNotNullOfOrNull { encoding ->
            capabilities.firstOrNull { it.bitPerfect && it.format == output.copy(encoding = encoding) }
        }
    }

    /**
     * The format the player sends to the sink for a track with this [sampleRateHz] and [channels], as 16-bit PCM;
     * [select] picks the container the DAC takes for it. `null` when unknown or more than stereo.
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
    const val ENCODING_PCM_FLOAT = 4
    const val ENCODING_PCM_24BIT_PACKED = 21
    const val ENCODING_PCM_32BIT = 22
    const val CHANNEL_OUT_MONO = 4
    const val CHANNEL_OUT_STEREO = 12
}
