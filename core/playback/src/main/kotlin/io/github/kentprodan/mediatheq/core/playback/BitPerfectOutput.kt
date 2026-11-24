package io.github.kentprodan.mediatheq.core.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.ForwardingAudioOutput
import androidx.media3.exoplayer.audio.ForwardingAudioOutputProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToLong

/**
 * Whether this device really takes float PCM (#629): `getMinBufferSize`, then a real AudioTrack, since some devices
 * pass the first and fail the second (PixelPlayer, Rhythm). Asked once, before the player is built.
 */
internal object FloatOutputProbe {
    val supported: Boolean by lazy { probe() }

    private fun probe(): Boolean {
        val size =
            AudioTrack.getMinBufferSize(
                PROBE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_FLOAT,
            )
        if (size <= 0) return false
        return runCatching {
            val track =
                AudioTrack
                    .Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build(),
                    ).setAudioFormat(
                        AudioFormat
                            .Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(PROBE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build(),
                    ).setBufferSizeInBytes(size)
                    .build()
            val ok = track.state == AudioTrack.STATE_INITIALIZED
            track.release()
            ok
        }.getOrDefault(false)
    }

    private const val PROBE_RATE = 44_100
}

/**
 * The encoding to open the AudioTrack in for the bit-perfect mixer [target] (#629), or `null` to open it as the sink
 * configured it: only linear PCM the sink writes as 16-bit or float, at the target's rate and channel mask, going to a
 * 16 / 24 / 32-bit integer mixer in another encoding. Pure.
 */
internal fun bitPerfectEncoding(
    sinkEncoding: Int,
    sampleRateHz: Int,
    channelMask: Int,
    direct: Boolean,
    target: PcmFormat?,
): Int? =
    target
        ?.takeIf {
            direct &&
                sinkEncoding in WIDENABLE &&
                it.encoding in CONTAINERS &&
                it.encoding != sinkEncoding &&
                it.sampleRateHz == sampleRateHz &&
                it.channelMask == channelMask
        }?.encoding

private val WIDENABLE = setOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)
private val CONTAINERS = setOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT)

/** Bytes per sample of the encodings converted here ([bytesPerSample] knows them all). */
private fun sampleBytes(encoding: Int): Int =
    requireNotNull(bytesPerSample(encoding)) { "Unsupported encoding $encoding" }

/**
 * Opens the AudioTrack in the bit-perfect mixer's integer container (#629, ADR 0056): `DefaultAudioSink` writes only
 * 16-bit or float, while a USB DAC's bit-perfect mixer takes 16 / 24 / 32-bit integers, and an AudioTrack reaches it
 * only in the mixer's exact encoding. The sink keeps seeing its own encoding; [PcmWideningAudioOutput] converts on the
 * way in. [target] is the mixer attribute `AudioOutputController` set, read on the playback thread; this class never
 * sets or clears the preference itself.
 */
@UnstableApi
internal class BitPerfectOutputProvider(
    delegate: AudioOutputProvider,
    private val target: () -> PcmFormat?,
) : ForwardingAudioOutputProvider(delegate) {
    override fun getAudioOutput(config: AudioOutputProvider.OutputConfig): AudioOutput {
        val encoding =
            bitPerfectEncoding(
                config.encoding,
                config.sampleRate,
                config.channelMask,
                direct = !config.isOffload && !config.isTunneling,
                target = target(),
            ) ?: return super.getAudioOutput(config)
        val opened =
            config
                .buildUpon()
                .setEncoding(encoding)
                // The same duration in the wider frames.
                .setBufferSize(config.bufferSize / sampleBytes(config.encoding) * sampleBytes(encoding))
                .build()
        return PcmWideningAudioOutput(super.getAudioOutput(opened), config.encoding, encoding)
    }
}

// PcmWideningAudioOutput and pcmToContainer are adapted from PixelPlayer's PcmWideningAudioOutput
// (github.com/PixelPlayerHQ/PixelPlayerOSS, GPL-3.0; see THIRD_PARTY.md): a ForwardingAudioOutput, and a 16-bit target.

/**
 * Feeds [output], an AudioTrack in a 16 / 24 / 32-bit integer [outputEncoding], with PCM the sink writes in 16-bit or
 * float [inputEncoding]. Every 16- and 24-bit sample value survives unchanged; only the container changes.
 */
@UnstableApi
internal class PcmWideningAudioOutput(
    output: AudioOutput,
    private val inputEncoding: Int,
    private val outputEncoding: Int,
) : ForwardingAudioOutput(output) {
    private val inputBytes = sampleBytes(inputEncoding)
    private val outputBytes = sampleBytes(outputEncoding)
    private var converted: ByteBuffer = ByteBuffer.allocateDirect(0)

    // The sink hands a partly written buffer back until it is consumed; its converted copy is kept meanwhile.
    private var pending: ByteBuffer? = null
    private var pendingStart = 0

    override fun write(
        buffer: ByteBuffer,
        encodedAccessUnitCount: Int,
        presentationTimeUs: Long,
    ): Boolean {
        if (buffer !== pending) {
            val needed = buffer.remaining() / inputBytes * outputBytes
            if (converted.capacity() < needed) converted = ByteBuffer.allocateDirect(needed)
            converted.clear()
            pcmToContainer(buffer, inputEncoding, converted, outputEncoding)
            converted.flip()
            pending = buffer
            pendingStart = buffer.position()
        }
        val fullyWritten = super.write(converted, encodedAccessUnitCount, presentationTimeUs)
        buffer.position(pendingStart + converted.position() / outputBytes * inputBytes)
        if (fullyWritten) pending = null
        return fullyWritten
    }

    override fun flush() {
        pending = null
        super.flush()
    }

    override fun release() {
        pending = null
        super.release()
    }

    // A reused output must keep the same conversion.
    override fun canReuseAudioOutput(
        oldConfig: AudioOutputProvider.OutputConfig,
        newFormatConfig: AudioOutputProvider.FormatConfig,
        newConfig: AudioOutputProvider.OutputConfig,
    ): Boolean = newConfig == oldConfig
}

/**
 * Writes [input]'s samples from its position to its limit (16-bit or float, native order) into [output] as
 * [outputEncoding] (16 / 24 / 32-bit integer, native order), without moving [input]. Pure.
 */
internal fun pcmToContainer(
    input: ByteBuffer,
    inputEncoding: Int,
    output: ByteBuffer,
    outputEncoding: Int,
) {
    val source = input.duplicate().order(ByteOrder.nativeOrder())
    output.order(ByteOrder.nativeOrder())
    val outputBits = sampleBytes(outputEncoding) * Byte.SIZE_BITS
    while (source.remaining() >= sampleBytes(inputEncoding)) {
        val value: Long =
            if (inputEncoding == C.ENCODING_PCM_FLOAT) {
                (source.getFloat() * (1L shl (outputBits - 1))).roundToLong()
            } else {
                source.getShort().toLong() shl (outputBits - Short.SIZE_BITS)
            }
        val max = (1L shl (outputBits - 1)) - 1
        val sample = value.coerceIn(-max - 1, max).toInt()
        when (outputEncoding) {
            C.ENCODING_PCM_16BIT -> {
                output.putShort(sample.toShort())
            }

            C.ENCODING_PCM_24BIT -> {
                // Packed 24-bit is little-endian on every Android ABI.
                output.put(sample.toByte())
                output.put((sample shr 8).toByte())
                output.put((sample shr 16).toByte())
            }

            else -> {
                output.putInt(sample)
            }
        }
    }
}

/**
 * Halves PCM above 192 kHz before the sink (#629, PixelPlayer): some device paths hang at 352.8 / 384 kHz. Sonic
 * resamples; it runs only in the 16-bit path, so a bit-perfect float track (whose mixer took the rate) is never touched.
 */
@UnstableApi
internal class HiResRateCap private constructor(
    private val sonic: SonicAudioProcessor,
) : AudioProcessor by sonic {
    constructor() : this(SonicAudioProcessor())

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sonic.setOutputSampleRateHz(
            cappedRate(inputAudioFormat.sampleRate) ?: SonicAudioProcessor.SAMPLE_RATE_NO_CHANGE,
        )
        return sonic.configure(inputAudioFormat)
    }
}

/** The rate [sampleRateHz] is halved to, as often as needed to reach 192 kHz at most, or `null` when it is fine. Pure. */
internal fun cappedRate(sampleRateHz: Int): Int? {
    var rate = sampleRateHz
    while (rate > MAX_RATE_HZ) rate /= 2
    return rate.takeIf { it != sampleRateHz }
}

private const val MAX_RATE_HZ = 192_000
