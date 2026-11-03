package io.github.kentprodan.mediatheq.core.playback

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.inspector.MediaExtractorCompat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** A track's whole file on the device: the cache holding it, its key there and its size (#124). */
@UnstableApi
internal data class LocalFile(
    val cache: Cache,
    val key: String,
    val sizeBytes: Long,
)

/** What decoding a file for its waveform gave. */
internal sealed interface Decoded {
    data class Levels(
        val levels: List<Float>,
    ) : Decoded

    /** No decoder on this device for the file's format (E-AC-3 JOC, TrueHD on a phone): never worth a retry. */
    data object Unsupported : Decoded

    /** Anything else (evicted mid-read, no free codec, a broken file): tried again the next time it is queued. */
    data object Failed : Decoded
}

/** Decodes a file to PCM and folds it into a waveform, off the player (#124). */
@UnstableApi
internal fun interface PcmDecoder {
    suspend fun decode(file: LocalFile): Decoded
}

/**
 * The device's own decoders (ADR 0027): Media3's extractors read the file from its cache only (no network: a missing
 * span fails at once instead of waiting for a writer), the platform `MediaCodec` decodes it to float PCM. Blocking:
 * run on the analyzer's background thread; cancellation is checked between buffers.
 */
@UnstableApi
internal class MediaCodecPcmDecoder : PcmDecoder {
    override suspend fun decode(file: LocalFile): Decoded {
        val source =
            CacheDataSource
                .Factory()
                .setCache(file.cache)
                .setCacheKeyFactory { file.key }
                // Read only: no upstream (a hole throws), no writes.
                .setCacheWriteDataSinkFactory(null)
        val extractor = MediaExtractorCompat(DefaultExtractorsFactory(), source)
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource("mediatheq://waveform/${file.key}".toUri(), 0)
            val track =
                (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: return Decoded.Unsupported
            val format = extractor.getTrackFormat(track)
            val name =
                MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format) ?: return Decoded.Unsupported
            extractor.selectTrack(track)
            // For a compressed track the key asks the decoder for float output; for raw PCM (WAV) it describes the
            // input, which must stay as the extractor read it.
            if (format.getString(MediaFormat.KEY_MIME) != MimeTypes.AUDIO_RAW) {
                format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT)
            }
            codec =
                MediaCodec.createByCodecName(name).apply {
                    configure(format, null, null, 0)
                    start()
                }
            drain(extractor, codec)?.let { Decoded.Levels(it) } ?: Decoded.Unsupported
        } catch (_: UnrecognizedInputFormatException) {
            // No extractor for this container: like a missing decoder, never worth a retry.
            Decoded.Unsupported
        } catch (e: IOException) {
            Log.w(TAG, "Cannot read ${file.key}", e)
            Decoded.Failed
        } catch (e: IllegalStateException) {
            // MediaCodec.CodecException included: no free codec instance, or a decoder error.
            Log.w(TAG, "Cannot decode ${file.key}", e)
            Decoded.Failed
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Cannot decode ${file.key}", e)
            Decoded.Failed
        } finally {
            codec?.release()
            extractor.release()
        }
    }

    /** Feeds the whole file through [codec]; `null` when it outputs a PCM encoding [toFloats] cannot read. */
    private suspend fun drain(
        extractor: MediaExtractorCompat,
        codec: MediaCodec,
    ): List<Float>? {
        val info = MediaCodec.BufferInfo()
        val buckets = WaveformBuckets()
        var inputDone = false
        // The decoder's real output, read again whenever it changes (it may ignore the float request, or switch
        // from mono to stereo mid-stream).
        var output: MediaFormat? = null
        var scratch = FloatArray(0)
        while (true) {
            currentCoroutineContext().ensureActive()
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    val size = extractor.readSampleData(codec.getInputBuffer(index)!!, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val out = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) output = codec.outputFormat
            if (out < 0) continue
            val format = output ?: codec.outputFormat.also { output = it }
            val encoding =
                if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                } else {
                    AudioFormat.ENCODING_PCM_16BIT
                }
            val pcm = codec.getOutputBuffer(out)!!.order(ByteOrder.LITTLE_ENDIAN) as ByteBuffer
            pcm.position(info.offset).limit(info.offset + info.size)
            val count = pcm.remaining() / (bytesPerSample(encoding) ?: return null)
            if (scratch.size < count) scratch = FloatArray(count)
            toFloats(pcm, encoding, scratch, count)
            buckets.add(FloatBuffer.wrap(scratch, 0, count), format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            codec.releaseOutputBuffer(out, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return buckets.levels()
        }
    }

    private companion object {
        const val TAG = "MqWaveform"
        const val TIMEOUT_US = 10_000L
    }
}

/** Bytes per sample of the PCM [encoding]s a waveform reads; `null` for any other. */
internal fun bytesPerSample(encoding: Int): Int? =
    when (encoding) {
        AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
        AudioFormat.ENCODING_PCM_16BIT -> 2
        AudioFormat.ENCODING_PCM_8BIT -> 1
        else -> null
    }

/** Reads [count] little-endian samples of [encoding] from [pcm] into [into] as -1..1 floats. */
internal fun toFloats(
    pcm: ByteBuffer,
    encoding: Int,
    into: FloatArray,
    count: Int,
) {
    for (i in 0 until count) {
        into[i] =
            when (encoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> {
                    pcm.float
                }

                AudioFormat.ENCODING_PCM_32BIT -> {
                    pcm.int / INT32_SCALE
                }

                AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                    val low = pcm.get().toInt() and BYTE_MASK
                    val mid = pcm.get().toInt() and BYTE_MASK
                    // The high byte keeps its sign.
                    ((pcm.get().toInt() shl 16) or (mid shl 8) or low) / INT24_SCALE
                }

                AudioFormat.ENCODING_PCM_16BIT -> {
                    pcm.short / INT16_SCALE
                }

                // 8-bit PCM is unsigned around 128.
                else -> {
                    ((pcm.get().toInt() and BYTE_MASK) - UINT8_ZERO) / UINT8_ZERO.toFloat()
                }
            }
    }
}

private const val INT32_SCALE = 2_147_483_648f
private const val INT24_SCALE = 8_388_608f
private const val INT16_SCALE = 32_768f
private const val UINT8_ZERO = 128
private const val BYTE_MASK = 0xFF
