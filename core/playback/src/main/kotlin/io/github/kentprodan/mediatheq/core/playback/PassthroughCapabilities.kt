package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioCapabilitiesReceiver

/**
 * The MIME types the current route takes unchanged (ADR 0039): `audio/eac3-joc`, `audio/eac3`, `audio/ac3`,
 * `audio/true-hd`. Empty where nothing passes through (the phone, Bluetooth).
 */
internal fun interface PassthroughCapabilities {
    fun mimeTypes(): Set<String>
}

/** The passthrough MIME types an [AudioCapabilities]-like [supportsEncoding] answers yes for. Pure. */
internal fun passthroughMimes(supportsEncoding: (Int) -> Boolean): Set<String> =
    PASSTHROUGH_ENCODINGS.filterKeys(supportsEncoding).values.toSet()

/**
 * Whether the AudioTrack the sink opened carries Dolby Atmos untouched (ADR 0039): E-AC-3 JOC, or TrueHD for a file
 * that is Atmos ([isAtmosFile]; plain 7.1 TrueHD is not).
 */
internal fun atmosPassthrough(
    encoding: Int?,
    isAtmosFile: Boolean,
): Boolean = encoding == C.ENCODING_E_AC3_JOC || (encoding == C.ENCODING_DOLBY_TRUEHD && isAtmosFile)

/** Whether an opened AudioTrack carries an encoded stream (passthrough), not PCM. */
internal fun isPassthroughEncoding(encoding: Int?): Boolean = encoding in PASSTHROUGH_ENCODINGS

/** The encodings the sink can pass through, as AudioTrack and Media3 name them. */
private val PASSTHROUGH_ENCODINGS =
    mapOf(
        C.ENCODING_E_AC3_JOC to MimeTypes.AUDIO_E_AC3_JOC,
        C.ENCODING_E_AC3 to MimeTypes.AUDIO_E_AC3,
        C.ENCODING_AC3 to MimeTypes.AUDIO_AC3,
        C.ENCODING_DOLBY_TRUEHD to MimeTypes.AUDIO_TRUEHD,
    )

/**
 * [PassthroughCapabilities] from Media3's [AudioCapabilities] for the routed device: the same answer the sink decides
 * passthrough from. Registers an [AudioCapabilitiesReceiver] (HDMI plug, the TV's surround setting, routing) that calls
 * [onChange] on the main thread, until [release]. The only user of these unstable APIs (checked at each Media3 bump).
 */
@UnstableApi
internal class Media3PassthroughCapabilities(
    private val context: Context,
    onChange: () -> Unit,
) : PassthroughCapabilities {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var routed: Int? = currentDevice()?.id

    // No routed device given: Media3 asks for the device media plays on itself (API 33+), at each broadcast.
    private val receiver =
        AudioCapabilitiesReceiver(
            context,
            {
                current = it
                onChange()
            },
            MEDIA,
            null,
        )
    private var current: AudioCapabilities = receiver.register()

    override fun mimeTypes(): Set<String> {
        // The route can change without a capabilities broadcast (the output switcher): read them again, quietly (this
        // runs inside a decision, which must not start another one).
        val device = currentDevice()?.id
        if (device != routed) {
            routed = device
            current = AudioCapabilities.getCapabilities(context, MEDIA, null, emptyList())
        }
        return passthroughMimes(current::supportsEncoding)
    }

    fun release() = receiver.unregister()

    private fun currentDevice(): AudioDeviceInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            audioManager.getAudioDevicesForAttributes(MEDIA.platformAudioAttributes).firstOrNull()
        } else {
            null
        }

    private companion object {
        val MEDIA: AudioAttributes =
            AudioAttributes
                .Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
    }
}
