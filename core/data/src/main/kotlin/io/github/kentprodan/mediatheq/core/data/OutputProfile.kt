package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.OutputId

/** One output's audio settings (#140). A new output starts as a copy of the phone's. */
data class OutputProfile(
    val normalization: Normalization = Normalization.Album,
    /** No normalization and no equalizer on this output; bit-perfect USB is unaffected. */
    val bypass: Boolean = false,
    val equalizerOn: Boolean = false,
    val eqPreset: EqPreset = EqPreset.Flat,
    /** The [EqPreset.Custom] gains, kept while another preset is chosen. */
    val customGainsDb: List<Float> = List(AudioPreferences.EQ_BANDS_HZ.size) { 0f },
    /** Send audio unchanged to this USB DAC. Only shown for USB outputs; the phone's is what new DACs copy. */
    val bitPerfectUsb: Boolean = false,
    /**
     * Send Dolby Atmos (and other encodings the route takes) unchanged to an HDMI output (ADR 0039). Only HDMI outputs
     * use it; the phone's Output settings do not show it.
     */
    val atmosPassthrough: Boolean = true,
) {
    /** The gains the equalizer applies, one per band of [AudioPreferences.EQ_BANDS_HZ]. */
    val eqGainsDb: List<Float> get() = eqPreset.gainsDb ?: customGainsDb
}

/** A remembered output: [name] is the device's (empty for the phone, whose name comes from the UI). */
data class RememberedOutput(
    val id: OutputId,
    val name: String,
    val profile: OutputProfile,
)

/** Every remembered output, the phone first, and the [current] one (the phone when unknown). */
data class RememberedOutputs(
    val outputs: List<RememberedOutput> = listOf(RememberedOutput(OutputId.Phone, "", OutputProfile())),
    val current: OutputId = OutputId.Phone,
) {
    /** [id]'s profile; the phone's for an output that is not remembered. */
    fun profileOf(id: OutputId): OutputProfile =
        (outputs.firstOrNull { it.id == id } ?: outputs.firstOrNull { it.id == OutputId.Phone })?.profile
            ?: OutputProfile()

    val currentProfile: OutputProfile get() = profileOf(current)
}
