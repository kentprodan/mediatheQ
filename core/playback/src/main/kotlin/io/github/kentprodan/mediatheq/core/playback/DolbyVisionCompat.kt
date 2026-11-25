package io.github.kentprodan.mediatheq.core.playback

import android.net.Uri
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ForwardingExtractor
import androidx.media3.extractor.ForwardingExtractorOutput
import androidx.media3.extractor.ForwardingTrackOutput
import androidx.media3.extractor.TrackOutput
import com.suyashbelekar.exoplayerhdrutils.extractor.BitstreamTransformingExtractorsFactory
import com.suyashbelekar.exoplayerhdrutils.video.transformers.DoviStrategy
import com.suyashbelekar.exoplayerhdrutils.video.transformers.Hdr10PlusStrategy
import com.suyashbelekar.exoplayerhdrutils.video.transformers.TransformStrategy

/**
 * What happens to a Dolby Vision file's bitstream on this device (#385). Profile 7 (Blu-ray, a base layer, an
 * enhancement layer and the RPU): a decoder that takes profile 7 gets it as it is; one that takes only profile 8 gets the
 * RPU rewritten to 8.1 without the enhancement layer (libdovi), so the TV still enters Dolby Vision; without a Dolby
 * Vision decoder and display the Dolby Vision layer goes and the HDR10 base layer plays on the HEVC decoder. Nothing is
 * re-encoded and the server never converts it. HDR10+ inside a Dolby Vision file goes whenever Dolby Vision plays:
 * some decoders (MediaTek) latch on to it and show black.
 */
data class DolbyVisionCompat(
    val profile7: DoviStrategy,
    val hdr10Plus: Hdr10PlusStrategy,
) {
    val strategy: TransformStrategy get() = TransformStrategy(profile7, profile7, hdr10Plus)

    /**
     * The track as the decoder must see it: Media3 picks the decoder by `Format.codecs`, so a converted profile 7 says
     * profile 8 (`dvhe.07.06` -> `dvhe.08.06`) and a stripped one is plain HEVC. Encrypted tracks reach the decoder
     * untouched, so they stay as they are.
     */
    @OptIn(UnstableApi::class)
    fun advertise(format: Format): Format {
        val codecs = format.codecs
        val profile7 =
            format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION && codecs != null && PROFILE_7.matches(codecs)
        if (!profile7 || format.drmInitData != null) return format
        return when (this.profile7) {
            DoviStrategy.KEEP -> {
                format
            }

            DoviStrategy.CONVERT_TO_P8 -> {
                format.buildUpon().setCodecs(codecs!!.replaceRange(5, 7, "08")).build()
            }

            DoviStrategy.DISCARD -> {
                format
                    .buildUpon()
                    .setSampleMimeType(MimeTypes.VIDEO_H265)
                    .setCodecs(null)
                    .build()
            }
        }
    }

    private companion object {
        val PROFILE_7 = Regex("""dv(he|h1)\.07.*""")
    }
}

/** The handling for these [decoders] and [display] HDR types (rules in [DolbyVisionCompat]). Pure. */
fun dolbyVisionCompat(
    decoders: VideoHdrDecoders,
    display: Set<Int>,
): DolbyVisionCompat {
    val dvDisplay = Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in display
    val profile7 =
        when {
            dvDisplay && decoders.dolbyVisionProfile7 -> DoviStrategy.KEEP
            dvDisplay && decoders.dolbyVisionProfile8 -> DoviStrategy.CONVERT_TO_P8
            else -> DoviStrategy.DISCARD
        }
    val dolbyVisionPlays = dvDisplay && decoders.dolbyVision
    return DolbyVisionCompat(profile7, if (dolbyVisionPlays) Hdr10PlusStrategy.DISCARD else Hdr10PlusStrategy.KEEP)
}

/**
 * The extractors of a video file (#385): libdovi's bitstream rewrite ([BitstreamTransformingExtractorsFactory]) inside,
 * the track format [DolbyVisionCompat.advertise]d outside, so the rewrite still sees the Dolby Vision track it acts on.
 * [compat] is read for each new file.
 */
@UnstableApi
internal class DolbyVisionExtractorsFactory(
    private val compat: () -> DolbyVisionCompat,
    private val base: ExtractorsFactory = DefaultExtractorsFactory(),
) : ExtractorsFactory {
    override fun createExtractors(): Array<Extractor> = wrap { it.createExtractors() }

    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>,
    ): Array<Extractor> = wrap { it.createExtractors(uri, responseHeaders) }

    private fun wrap(create: (ExtractorsFactory) -> Array<Extractor>): Array<Extractor> {
        val compat = compat()
        return create(BitstreamTransformingExtractorsFactory(compat.strategy, base))
            .map { AdvertisingExtractor(it, compat) }
            .toTypedArray()
    }

    private class AdvertisingExtractor(
        delegate: Extractor,
        private val compat: DolbyVisionCompat,
    ) : ForwardingExtractor(delegate) {
        override fun init(output: ExtractorOutput) {
            super.init(
                object : ForwardingExtractorOutput(output) {
                    override fun track(
                        id: Int,
                        type: Int,
                    ): TrackOutput =
                        object : ForwardingTrackOutput(super.track(id, type)) {
                            override fun format(format: Format) = super.format(compat.advertise(format))
                        }
                },
            )
        }
    }
}
