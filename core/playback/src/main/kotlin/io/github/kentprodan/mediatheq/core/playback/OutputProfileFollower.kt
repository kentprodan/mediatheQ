package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.data.OutputProfile
import io.github.kentprodan.mediatheq.core.data.RememberedOutputs

/**
 * Follows the device media plays on (#140): remembers each one it sees ([remember]) and hands the settings of its
 * profile to [apply], again whenever the device or the remembered profiles change. A device not remembered yet plays
 * with the phone's profile, which is also what it is remembered with. Main thread only.
 */
internal class OutputProfileFollower(
    private val remember: (OutputDevice) -> Unit,
    private val apply: (OutputProfile) -> Unit,
) {
    private var outputs = RememberedOutputs()
    private var device: OutputDevice? = null
    private var applied: OutputProfile? = null

    fun onOutputs(outputs: RememberedOutputs) {
        this.outputs = outputs
        applyCurrent()
    }

    fun onDevice(device: OutputDevice) {
        if (device == this.device) return
        this.device = device
        remember(device)
        applyCurrent()
    }

    private fun applyCurrent() {
        val profile = outputs.profileOf((device ?: OutputDevice.Phone).id)
        if (profile == applied) return
        applied = profile
        apply(profile)
    }
}

/** What [AudioPolicy] needs of an output's profile. */
internal fun OutputProfile.toAudioSettings() =
    AudioSettings(
        normalization = normalization,
        equalizerOn = equalizerOn,
        bitPerfectUsb = bitPerfectUsb,
        bypass = bypass,
    )
