package io.github.kentprodan.mediatheq.core.playback

import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi

/** The platform side of bit-perfect USB (media3 skill, section 6), behind plain data so the decisions stay testable. */
internal interface UsbAudioPort {
    /** The USB DAC media is routed to: its mixer attributes, or `null` when there is none (or no way to tell). */
    fun dacCapabilities(): List<MixerCapability>?

    /** Prefers [capability] for media on the DAC; `false` when the platform refused. */
    fun setPreferred(capability: MixerCapability): Boolean

    /** Drops the preference set by [setPreferred]. */
    fun clearPreferred()

    /**
     * Calls [onChange] on the main thread when an output device is added or removed, and [onPreferenceDropped] when
     * the platform drops the preference set by [setPreferred] (not when we clear it), until [release].
     */
    fun watch(
        onChange: () -> Unit,
        onPreferenceDropped: () -> Unit,
    )

    fun release()

    companion object {
        /** Mixer attributes need Android 14; below it, never a DAC to play bit-perfect to. */
        fun create(audioManager: AudioManager): UsbAudioPort =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                AndroidUsbAudioPort(audioManager)
            } else {
                NoUsbAudioPort
            }
    }
}

private object NoUsbAudioPort : UsbAudioPort {
    override fun dacCapabilities(): List<MixerCapability>? = null

    override fun setPreferred(capability: MixerCapability) = false

    override fun clearPreferred() = Unit

    override fun watch(
        onChange: () -> Unit,
        onPreferenceDropped: () -> Unit,
    ) = Unit

    override fun release() = Unit
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private class AndroidUsbAudioPort(
    private val audioManager: AudioManager,
) : UsbAudioPort {
    private var dac: AudioDeviceInfo? = null
    private var attributes: Map<MixerCapability, AudioMixerAttributes> = emptyMap()
    private var preferredOn: AudioDeviceInfo? = null
    private var preferredMixer: AudioMixerAttributes? = null
    private var callback: AudioDeviceCallback? = null
    private var preferenceListener: AudioManager.OnPreferredMixerAttributesChangedListener? = null

    override fun dacCapabilities(): List<MixerCapability>? {
        // The device media plays on now: a DAC that is plugged in while music goes to Bluetooth does not count.
        val device =
            audioManager.getAudioDevicesForAttributes(MEDIA).firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
        dac = device
        attributes =
            device
                ?.let(audioManager::getSupportedMixerAttributes)
                .orEmpty()
                .associateBy {
                    MixerCapability(
                        PcmFormat(it.format.encoding, it.format.sampleRate, it.format.channelMask),
                        bitPerfect = it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT,
                    )
                }
        return device?.let { attributes.keys.toList() }
    }

    override fun setPreferred(capability: MixerCapability): Boolean {
        val device = dac ?: return false
        val mixer = attributes[capability] ?: return false
        return audioManager.setPreferredMixerAttributes(MEDIA, device, mixer).also {
            if (it) {
                preferredOn = device
                preferredMixer = mixer
            }
        }
    }

    override fun clearPreferred() {
        // Forgotten first: the listener then knows this clear is ours.
        val device = preferredOn
        preferredOn = null
        preferredMixer = null
        device?.let { audioManager.clearPreferredMixerAttributes(MEDIA, it) }
    }

    override fun watch(
        onChange: () -> Unit,
        onPreferenceDropped: () -> Unit,
    ) {
        val main = Handler(Looper.getMainLooper())
        preferenceListener =
            AudioManager
                .OnPreferredMixerAttributesChangedListener { attributes, device, mixer ->
                    val ours = preferredOn
                    if (attributes.usage == MEDIA.usage && ours != null && device.id == ours.id &&
                        mixer != preferredMixer
                    ) {
                        preferredOn = null
                        preferredMixer = null
                        onPreferenceDropped()
                    }
                }.also { audioManager.addOnPreferredMixerAttributesChangedListener(main::post, it) }
        callback =
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = onChange()

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = onChange()
            }.also { audioManager.registerAudioDeviceCallback(it, Handler(Looper.getMainLooper())) }
    }

    override fun release() {
        callback?.let(audioManager::unregisterAudioDeviceCallback)
        callback = null
        preferenceListener?.let(audioManager::removeOnPreferredMixerAttributesChangedListener)
        preferenceListener = null
        clearPreferred()
    }

    private companion object {
        // Bit-perfect mixer attributes apply to media playback only.
        val MEDIA: AudioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
    }
}
