package io.github.kentprodan.mediatheq.core.data

/** Equalizer presets: gains in dB for the bands of [AudioPreferences.EQ_BANDS_HZ]. [Custom] uses the user's own. */
enum class EqPreset(
    val gainsDb: List<Float>?,
) {
    Flat(listOf(0f, 0f, 0f, 0f, 0f)),
    BassBoost(listOf(6f, 4f, 0f, 0f, 0f)),
    TrebleBoost(listOf(0f, 0f, 0f, 4f, 6f)),
    Vocal(listOf(-2f, 0f, 4f, 3f, 0f)),
    Custom(null),
}

/** Which ReplayGain-style gain sets the volume of a track (media3 skill, section 7). */
enum class Normalization { Album, Track, Off }

/** Streaming quality on one kind of network: the original file, or Opus at [bitrate] bits per second. */
enum class StreamingQuality(
    val bitrate: Int?,
) {
    Original(null),
    Kbps256(256_000),
    Kbps128(128_000),
}

/** Validated audio, streaming and storage preferences (media3 skill, sections 3, 6, 7, 9 and 10). */
data class AudioPreferences(
    /** Send audio unchanged to a USB DAC when one is connected. Off by default: phone volume stops working. */
    val bitPerfectUsb: Boolean = false,
    val equalizerOn: Boolean = false,
    val eqPreset: EqPreset = EqPreset.Flat,
    /** The [EqPreset.Custom] gains, kept while another preset is chosen. */
    val customGainsDb: List<Float> = FLAT_GAINS_DB,
    val normalization: Normalization = Normalization.Album,
    /** Quality on unmetered networks (Wi-Fi, Ethernet). */
    val unmeteredQuality: StreamingQuality = StreamingQuality.Original,
    /** Quality on metered networks (mobile data). */
    val meteredQuality: StreamingQuality = StreamingQuality.Kbps256,
    /** The streaming cache size, one of [CACHE_SIZES_GB]. */
    val streamingCacheGb: Int = DEFAULT_CACHE_GB,
    /** Downloads wait for an unmetered network; off = any network. */
    val downloadsUnmeteredOnly: Boolean = true,
) {
    /** The streaming quality for a network that is [metered] or not. */
    fun qualityFor(metered: Boolean): StreamingQuality = if (metered) meteredQuality else unmeteredQuality

    /** The gains the equalizer applies, one per band of [EQ_BANDS_HZ]. */
    val eqGainsDb: List<Float> get() = eqPreset.gainsDb ?: customGainsDb

    companion object {
        /** Band centers in Hz (simple 5-band equalizer, no parametric editor). */
        val EQ_BANDS_HZ = listOf(60, 230, 910, 3_600, 14_000)

        /** The gain range of one band, in dB. */
        val EQ_GAIN_DB = -12f..12f

        private val FLAT_GAINS_DB = List(EQ_BANDS_HZ.size) { 0f }

        val CACHE_SIZES_GB = listOf(1, 2, 4, 8)
        const val DEFAULT_CACHE_GB = 2
    }
}
