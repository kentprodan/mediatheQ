// The extractor hook and the codec adapter are adapted from Moonfin (github.com/Moonfin-Client/Moonfin-Core,
// packages/moonfin_native_video: CadenceObservingExtractorsFactory, VsyncPacingAdapter), GPL-3.0. See THIRD_PARTY.md.
package io.github.kentprodan.mediatheq.core.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.ForwardingMediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ForwardingExtractor
import androidx.media3.extractor.ForwardingExtractorOutput
import androidx.media3.extractor.ForwardingExtractorsFactory
import androidx.media3.extractor.ForwardingTrackOutput
import androidx.media3.extractor.TrackOutput

/**
 * The refresh rate a video's frame timing asks for (#633): the coarsest standard grid every frame interval lands on. A
 * 3:2 pulldown transfer stated as 23.976 asks for 59.94; `null` when no grid fits.
 */
internal fun FrameCadence.displayRate(): Float? = gridHz?.toFloat()

/**
 * Reads the frame timestamps of a video file as the extractor hands them over, a buffer ahead of playback (#633), and
 * tells [onRate] the rate they ask for ([displayRate]) with the file's URI, once it settles and whenever it changes. A
 * seek starts the reading over. Files opened without a URI are not read.
 */
@UnstableApi
internal class CadenceExtractorsFactory(
    delegate: ExtractorsFactory,
    private val onRate: (uri: Uri, rate: Float) -> Unit,
) : ForwardingExtractorsFactory(delegate) {
    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>,
    ): Array<Extractor> = super.createExtractors(uri, responseHeaders).map { Observing(it, uri) }.toTypedArray()

    private inner class Observing(
        delegate: Extractor,
        private val uri: Uri,
    ) : ForwardingExtractor(delegate) {
        private val tracks = mutableListOf<CadenceTrackOutput>()

        override fun init(output: ExtractorOutput) {
            super.init(
                object : ForwardingExtractorOutput(output) {
                    override fun track(
                        id: Int,
                        type: Int,
                    ): TrackOutput {
                        val track = super.track(id, type)
                        if (type != C.TRACK_TYPE_VIDEO) return track
                        return CadenceTrackOutput(track) { onRate(uri, it) }.also(tracks::add)
                    }
                },
            )
        }

        override fun seek(
            position: Long,
            timeUs: Long,
        ) {
            tracks.forEach { it.reset() }
            super.seek(position, timeUs)
        }
    }
}

/** Feeds each video sample's timestamp to a [FrameCadenceObserver]; never costs a frame when it fails. */
@UnstableApi
internal class CadenceTrackOutput(
    delegate: TrackOutput,
    private val onRate: (Float) -> Unit,
) : ForwardingTrackOutput(delegate) {
    private val observer = FrameCadenceObserver(reorderDepth = REORDER_DEPTH)
    private var reported: Float? = null

    override fun sampleMetadata(
        timeUs: Long,
        flags: Int,
        size: Int,
        offset: Int,
        cryptoData: TrackOutput.CryptoData?,
    ) {
        super.sampleMetadata(timeUs, flags, size, offset, cryptoData)
        val rate = runCatching { observer.onFrame(timeUs)?.displayRate() }.getOrNull() ?: return
        if (rate != reported) {
            reported = rate
            runCatching { onRate(rate) }
        }
    }

    fun reset() {
        observer.reset()
    }

    private companion object {
        // Decode order back to presentation order for B-frame reordering shallower than this.
        const val REORDER_DEPTH = 16
    }
}

/**
 * Wraps each video codec so every frame gets its own vsync ([VsyncPacer], #633); [vsyncNs] is the display's current
 * vsync period, 0 when unknown. Audio codecs are left alone.
 */
@UnstableApi
internal class VsyncPacingAdapterFactory(
    private val delegate: MediaCodecAdapter.Factory,
    private val vsyncNs: () -> Long,
) : MediaCodecAdapter.Factory {
    override fun createAdapter(configuration: MediaCodecAdapter.Configuration): MediaCodecAdapter {
        val adapter = delegate.createAdapter(configuration)
        val video = configuration.format.sampleMimeType?.startsWith("video/") == true
        return if (video) Pacing(adapter, vsyncNs) else adapter
    }

    private class Pacing(
        delegate: MediaCodecAdapter,
        private val vsyncNs: () -> Long,
    ) : ForwardingMediaCodecAdapter(delegate) {
        private val pacer = VsyncPacer()

        override fun releaseOutputBuffer(
            index: Int,
            renderTimeStampNs: Long,
        ) = super.releaseOutputBuffer(index, pacer.pace(renderTimeStampNs, vsyncNs()))
    }
}
