package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** What the video decoders can do for HDR (#276), from their profile levels. */
data class VideoHdrDecoders(
    /** HEVC Main 10 (10-bit, the HDR10 / HLG base). */
    val hevcMain10: Boolean = false,
    /** HEVC Main 10 HDR10+ (dynamic metadata). */
    val hevcHdr10Plus: Boolean = false,
    /** A `video/dolby-vision` decoder. */
    val dolbyVision: Boolean = false,
)

/** The decoders' HDR abilities; read once per resolve. */
fun interface VideoHdrCapabilities {
    fun decoders(): VideoHdrDecoders
}

/** The HDR types the display shows ([Display.HdrCapabilities] `HDR_TYPE_*`); re-read at each resolve. */
fun interface DisplayHdr {
    fun types(): Set<Int>
}

/**
 * The Jellyfin `VideoRangeType`s to declare (#276, ADR 0050): SDR always; HDR10 / HLG with a Main 10 decoder and the
 * display type; HDR10+ with HDR10 (its base layer); `DOVI` only with a Dolby Vision decoder and display. A Dolby Vision file
 * with an HDR10 / HLG / SDR base layer (profile 8) still plays its base layer on the HEVC decoder, so `DOVIWith*` follows
 * that range. Never a DV-only file (profile 5) without DV: the server tone-maps it instead of a purple picture. Pure.
 */
fun videoRangeTypes(
    display: Set<Int>,
    decoders: VideoHdrDecoders,
): List<String> {
    val hdr10 = decoders.hevcMain10 && Display.HdrCapabilities.HDR_TYPE_HDR10 in display
    val hlg = decoders.hevcMain10 && Display.HdrCapabilities.HDR_TYPE_HLG in display
    // An HDR10+ file has an HDR10 base: it plays as HDR10 where the dynamic metadata is not taken (as Jellyfin web).
    val hdr10Plus = hdr10
    val dv = decoders.dolbyVision && Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in display
    return buildList {
        add("SDR")
        if (hdr10) add("HDR10")
        if (hlg) add("HLG")
        if (hdr10Plus) add("HDR10Plus")
        if (dv) add("DOVI")
        if (dv || hdr10) add("DOVIWithHDR10")
        if (dv || hlg) add("DOVIWithHLG")
        add("DOVIWithSDR")
    }
}

/** [VideoHdrCapabilities] from the platform's codec list. */
internal class MediaCodecVideoHdr
    @Inject
    constructor() : VideoHdrCapabilities {
        override fun decoders(): VideoHdrDecoders {
            val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot { it.isEncoder }

            fun profiles(mime: String) =
                infos
                    .filter { mime in it.supportedTypes }
                    .flatMap { info -> info.getCapabilitiesForType(mime).profileLevels.map { it.profile } }
                    .toSet()
            val hevc = profiles("video/hevc")
            return VideoHdrDecoders(
                hevcMain10 =
                    hevc.any {
                        it == CodecProfileLevel.HEVCProfileMain10 ||
                            it == CodecProfileLevel.HEVCProfileMain10HDR10 ||
                            it == CodecProfileLevel.HEVCProfileMain10HDR10Plus
                    },
                hevcHdr10Plus = CodecProfileLevel.HEVCProfileMain10HDR10Plus in hevc,
                dolbyVision = infos.any { "video/dolby-vision" in it.supportedTypes },
            )
        }
    }

/** [DisplayHdr] of the phone's built-in display (the player is full screen there; an external display is not handled). */
internal class DefaultDisplayHdr
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : DisplayHdr {
        override fun types(): Set<Int> {
            val display =
                context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
                    ?: return emptySet()
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                display.mode.supportedHdrTypes.toSet()
            } else {
                @Suppress("DEPRECATION")
                display.hdrCapabilities
                    ?.supportedHdrTypes
                    ?.toSet()
                    .orEmpty()
            }
        }
    }
