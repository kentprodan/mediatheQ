// The zlib PGS hook, the HDMV TS hook and the trick-play filter are adapted from Moonfin
// (github.com/Moonfin-Client/Moonfin-Core, packages/moonfin_native_video: MoonfinMatroskaExtractor,
// withHdmvTsSupport, TrickPlayFreeHlsPlaylistParserFactory), GPL-3.0. See THIRD_PARTY.md.
package io.github.kentprodan.mediatheq.core.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistParserFactory
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParserFactory
import androidx.media3.exoplayer.upstream.ParsingLoadable
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor

/**
 * The extractors of a video file (#634): Media3's, with constant-bitrate seeking for files without a seek table, MKV files with zlib-compressed PGS subtitles let through
 * ([PgsTolerantMatroskaExtractor]) and Blu-ray DTS read from a TS that carries the HDMV registration
 * ([HdmvTsExtractor]). The replacements are built with the settings [DefaultExtractorsFactory] gives its own.
 */
@UnstableApi
internal class VideoContainerExtractorsFactory : ExtractorsFactory {
    private val base = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)

    override fun createExtractors(): Array<Extractor> = base.createExtractors().map(::replace).toTypedArray()

    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>,
    ): Array<Extractor> = base.createExtractors(uri, responseHeaders).map(::replace).toTypedArray()

    private fun replace(extractor: Extractor): Extractor =
        when (extractor) {
            is MatroskaExtractor -> {
                PgsTolerantMatroskaExtractor()
            }

            is TsExtractor -> {
                val readers = HdmvTsPayloadReaderFactory(DefaultTsPayloadReaderFactory())
                HdmvTsExtractor(
                    TsExtractor(
                        TsExtractor.MODE_SINGLE_PMT,
                        0,
                        DefaultSubtitleParserFactory(),
                        TimestampAdjuster(0),
                        readers,
                        TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES,
                    ),
                    readers,
                )
            }

            else -> {
                extractor
            }
        }
}

/**
 * Media3's MKV extractor refuses a whole file over any track compression but header stripping, although its PGS parser
 * inflates zlib samples itself (#634). A PGS track's zlib compression is let through; every other stays refused.
 */
@UnstableApi
internal class PgsTolerantMatroskaExtractor : MatroskaExtractor(DefaultSubtitleParserFactory()) {
    override fun integerElement(
        id: Int,
        value: Long,
    ) {
        if (id == ID_CONTENT_COMPRESSION_ALGORITHM &&
            value == CONTENT_COMP_ALGO_ZLIB &&
            getCurrentTrack(id).codecId == CODEC_ID_PGS
        ) {
            return
        }
        super.integerElement(id, value)
    }

    private companion object {
        const val ID_CONTENT_COMPRESSION_ALGORITHM = 0x4254
        const val CONTENT_COMP_ALGO_ZLIB = 0L
        const val CODEC_ID_PGS = "S_HDMV/PGS"
    }
}

/**
 * Leaves I-frame-only (trick-play) variants out of a multivariant playlist (#634). Media3 decides whether the variants
 * carry muxed audio from the first one listed, so a playlist naming its I-frame stream first plays with no audio. Nothing
 * here plays trick-play variants. A playlist of nothing but trick play is left alone.
 */
@UnstableApi
internal class TrickPlayFreeHlsPlaylistParserFactory(
    private val delegate: HlsPlaylistParserFactory = DefaultHlsPlaylistParserFactory(),
) : HlsPlaylistParserFactory {
    override fun createPlaylistParser(): ParsingLoadable.Parser<HlsPlaylist> =
        withoutTrickPlay(delegate.createPlaylistParser())

    override fun createPlaylistParser(
        multivariantPlaylist: HlsMultivariantPlaylist,
        previousMediaPlaylist: HlsMediaPlaylist?,
    ): ParsingLoadable.Parser<HlsPlaylist> =
        withoutTrickPlay(delegate.createPlaylistParser(multivariantPlaylist, previousMediaPlaylist))

    private fun withoutTrickPlay(parser: ParsingLoadable.Parser<HlsPlaylist>) =
        ParsingLoadable.Parser { uri, input ->
            when (val playlist = parser.parse(uri, input)) {
                is HlsMultivariantPlaylist -> withoutTrickPlay(playlist)
                else -> playlist
            }
        }

    companion object {
        fun withoutTrickPlay(playlist: HlsMultivariantPlaylist): HlsMultivariantPlaylist {
            val variants = playlist.variants.filter { it.format.roleFlags and C.ROLE_FLAG_TRICK_PLAY == 0 }
            if (variants.size == playlist.variants.size || variants.isEmpty()) return playlist
            return HlsMultivariantPlaylist(
                playlist.baseUri,
                playlist.tags,
                variants,
                playlist.videos,
                playlist.audios,
                playlist.subtitles,
                playlist.closedCaptions,
                playlist.muxedAudioFormat,
                playlist.muxedCaptionFormats,
                playlist.hasIndependentSegments,
                playlist.variableDefinitions,
                playlist.sessionKeyDrmInitData,
                playlist.contentSteeringInfo,
            )
        }
    }
}
