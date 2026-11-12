package io.github.kentprodan.mediatheq.core.playback

/** [AudioOutputPort] in memory: a DAC with [capabilities] (none: `null`), accepting a preference unless [accepts] is off. */
internal class FakeUsbAudioPort : AudioOutputPort {
    var output = OutputDevice.Phone

    override fun currentOutput() = output

    var capabilities: List<MixerCapability>? = null
    var accepts = true
    var preferred: MixerCapability? = null
    var setCalls = 0

    override fun dacCapabilities() = capabilities

    override fun setPreferred(capability: MixerCapability): Boolean {
        setCalls++
        if (accepts) preferred = capability
        return accepts
    }

    override fun clearPreferred() {
        preferred = null
    }

    override fun watch(
        onChange: () -> Unit,
        onPreferenceDropped: () -> Unit,
    ) = Unit

    override fun release() = Unit
}
