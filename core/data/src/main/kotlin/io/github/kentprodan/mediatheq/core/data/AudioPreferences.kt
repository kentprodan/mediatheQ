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

/** Validated audio preferences (media3 skill, sections 6 and 7). */
data class AudioPreferences(
    /** Send audio unchanged to a USB DAC when one is connected. Off by default: phone volume stops working. */
    val bitPerfectUsb: Boolean = false,
    val equalizerOn: Boolean = false,
    val eqPreset: EqPreset = EqPreset.Flat,
    /** The [EqPreset.Custom] gains, kept while another preset is chosen. */
    val customGainsDb: List<Float> = FLAT_GAINS_DB,
) {
    /** The gains the equalizer applies, one per band of [EQ_BANDS_HZ]. */
    val eqGainsDb: List<Float> get() = eqPreset.gainsDb ?: customGainsDb

    companion object {
        /** Band centers in Hz (simple 5-band equalizer, no parametric editor). */
        val EQ_BANDS_HZ = listOf(60, 230, 910, 3_600, 14_000)

        /** The gain range of one band, in dB. */
        val EQ_GAIN_DB = -12f..12f

        private val FLAT_GAINS_DB = List(EQ_BANDS_HZ.size) { 0f }
    }
}
