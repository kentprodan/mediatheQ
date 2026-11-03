package io.github.kentprodan.mediatheq.core.playback

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
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
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT)
            codec =
                MediaCodec.createByCodecName(name).apply {
                    configure(format, null, null, 0)
                    start()
                }
            drain(extractor, codec)?.let { Decoded.Levels(it) } ?: Decoded.Unsupported
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

    /** Feeds the whole file through [codec]; `null` when it outputs a PCM encoding other than float or 16-bit. */
    private suspend fun drain(
        extractor: MediaExtractorCompat,
        codec: MediaCodec,
    ): List<Float>? {
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var buckets: WaveformBuckets? = null
        var float = true
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
            if (out < 0) continue
            if (buckets == null) {
                // The decoder's real output, not the file's format: it may ignore the float request.
                val output = codec.outputFormat
                val encoding =
                    if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        output.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    } else {
                        AudioFormat.ENCODING_PCM_16BIT
                    }
                float = encoding == AudioFormat.ENCODING_PCM_FLOAT
                if (!float && encoding != AudioFormat.ENCODING_PCM_16BIT) return null
                buckets = WaveformBuckets(output.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            }
            val pcm = codec.getOutputBuffer(out)!!.order(ByteOrder.nativeOrder()) as ByteBuffer
            pcm.position(info.offset).limit(info.offset + info.size)
            if (float) {
                buckets.add(pcm.asFloatBuffer())
            } else {
                val shorts = pcm.asShortBuffer()
                if (scratch.size < shorts.remaining()) scratch = FloatArray(shorts.remaining())
                val count = shorts.remaining()
                for (i in 0 until count) scratch[i] = shorts.get() / SHORT_SCALE
                buckets.add(FloatBuffer.wrap(scratch, 0, count))
            }
            codec.releaseOutputBuffer(out, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return buckets.levels()
        }
    }

    private companion object {
        const val TAG = "MqWaveform"
        const val TIMEOUT_US = 10_000L
        const val SHORT_SCALE = 32_768f
    }
}
