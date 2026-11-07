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

/**
 * The platform side of the audio output: which device media plays on (#140) and bit-perfect USB (media3 skill, section
 * 6), behind plain data so the decisions stay testable.
 */
internal interface AudioOutputPort {
    /** The device media plays on now; the phone when there is no way to tell. */
    fun currentOutput(): OutputDevice

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
        /**
         * Mixer attributes need Android 14; below it, never a DAC to play bit-perfect to.
         * shortcut: below Android 14 the output is always the phone (no profiles per device), though Android 13 could
         * tell it with getAudioDevicesForAttributes; add it if a device below 14 matters.
         */
        fun create(audioManager: AudioManager): AudioOutputPort =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                AndroidAudioOutputPort(audioManager)
            } else {
                NoAudioOutputPort
            }
    }
}

private object NoAudioOutputPort : AudioOutputPort {
    override fun currentOutput() = OutputDevice.Phone

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
private class AndroidAudioOutputPort(
    private val audioManager: AudioManager,
) : AudioOutputPort {
    private var dac: AudioDeviceInfo? = null
    private var attributes: Map<MixerCapability, AudioMixerAttributes> = emptyMap()
    private var preferredOn: AudioDeviceInfo? = null
    private var preferredMixer: AudioMixerAttributes? = null

    // Our own changes come back through the listener later, possibly after the next one: never "dropped".
    private val ownMixers = mutableSetOf<AudioMixerAttributes>()
    private var ownClearsPending = 0
    private var callback: AudioDeviceCallback? = null
    private var preferenceListener: AudioManager.OnPreferredMixerAttributesChangedListener? = null

    override fun currentOutput(): OutputDevice {
        val device = audioManager.getAudioDevicesForAttributes(MEDIA).firstOrNull() ?: return OutputDevice.Phone
        return OutputDevice(outputKind(device.type), device.productName.toString(), device.address)
    }

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
                ownMixers += mixer
            }
        }
    }

    override fun clearPreferred() {
        // Forgotten first: the listener then knows this clear is ours.
        val device = preferredOn
        preferredOn = null
        preferredMixer = null
        device?.let {
            ownClearsPending++
            audioManager.clearPreferredMixerAttributes(MEDIA, it)
        }
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
                    val ownEcho =
                        if (mixer == null) {
                            (ownClearsPending > 0).also { if (it) ownClearsPending-- }
                        } else {
                            mixer in ownMixers
                        }
                    if (attributes.usage == MEDIA.usage && ours != null && device.id == ours.id && !ownEcho) {
                        preferredOn = null
                        preferredMixer = null
                        onPreferenceDropped()
                    }
                }.also { audioManager.addOnPreferredMixerAttributesChangedListener(main::post, it) }
        // shortcut: only devices added or removed decide again at once; picking another output in the system output
        // switcher while both stay connected applies at the next track or playback state change. Add a routing listener if
        // that matters on the phone (#140).
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
