package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.common.NetworkMonitor
import io.github.kentprodan.mediatheq.core.data.AudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.PlaybackRepository
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.data.VideoPreferencesRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlayMethod
import io.github.kentprodan.mediatheq.core.model.PlaybackStream
import io.github.kentprodan.mediatheq.core.model.StreamChoice
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

/**
 * Turns a track into a playable stream when the player loads it (media3 skill, section 3), in the quality the user set
 * for the network kind. One stream per track and quality: seeks and retries reopen the same stream (a reopen mid-file
 * must never switch format), and progress reports carry its play session.
 */
@Singleton
class StreamResolver
    @Inject
    constructor(
        private val playbackRepository: PlaybackRepository,
        private val decoders: AudioDecoders,
        private val networkMonitor: NetworkMonitor,
        private val audioPreferences: AudioPreferencesRepository,
        private val videoDecoders: VideoDecoders = VideoDecoders { emptySet() },
        private val trackChooser: VideoTrackChooser = VideoTrackChooser.None,
        private val videoPreferences: VideoPreferencesRepository = VideoPreferencesRepository.Defaults,
        private val videoHdr: VideoHdrCapabilities = VideoHdrCapabilities { VideoHdrDecoders() },
        private val displayHdr: DisplayHdr = DisplayHdr { emptySet() },
    ) {
        private val hdrDecoders by lazy { videoHdr.decoders() }

        private class Resolved(
            val stream: PlaybackStream,
            val quality: StreamingQuality,
            /** The passthrough set the stream was asked with: another route asks again (ADR 0039). */
            val passthrough: Set<String> = emptySet(),
            /** Played from the download, not from the server. */
            val local: Boolean = false,
            /** Played from a download that is the server's conversion at this bitrate, not the original file (#68). */
            val convertedKbps: Int? = null,
            /** A video's stream (ADR 0047): no music quality applies to it. */
            val video: Boolean = false,
        )

        private val decoderMimeTypes by lazy { decoders.mimeTypes() }
        private val streams = ConcurrentHashMap<MediaId, Resolved>()

        /** The Atmos MIME type of each track seen (`MediaItem.atmosMime`), for the data source, which has only ids. */
        private val atmosMimes = ConcurrentHashMap<MediaId, String>()

        /** The passthrough MIME type of each file seen (`MediaItem.passthroughMime`), Atmos or not. */
        private val fileMimes = ConcurrentHashMap<MediaId, String>()

        /**
         * What the route passes through, for stream requests (ADR 0039): set by the service at each output decision,
         * empty off HDMI or with the output's Dolby Atmos passthrough switch off.
         */
        @Volatile var passthrough: Set<String> = emptySet()

        /**
         * The stream for [itemId]. [continuing] always reuses the known stream: the player's file source passes it on
         * every open, so it keeps the stream the item started with (ADR 0019). Otherwise (choosing the item's source) the
         * known stream is reused unless the quality changed since (another network kind, e.g. home Wi-Fi to cellular, or
         * another setting) or the route passes through something else. Reading on from a download that was removed
         * meanwhile asks for the original file: the bytes already played came from it. A Dolby Atmos track ([atmosMime])
         * the route passes through always asks for the original ([atmosRequest]).
         */
        suspend fun resolve(
            itemId: MediaId,
            continuing: Boolean = false,
            atmosMime: String? = null,
        ): MqResult<PlaybackStream> {
            rememberAtmos(itemId, atmosMime)
            // The file source passes only the id: the type the item's source chose with still counts.
            val atmos = atmosMimes[itemId]
            val known = streams[itemId]
            val fromDownload = continuing && known?.local == true
            val passthrough = passthrough
            val quality =
                if (fromDownload) StreamingQuality.Original else atmosRequest(atmos, passthrough, currentQuality())
            known?.takeUnless { it.local }?.let {
                if (continuing || (it.quality == quality && it.passthrough == passthrough)) {
                    return MqResult.Success(it.stream)
                }
            }
            val profile = DeviceProfileBuilder.build(decoderMimeTypes, passthrough, quality)
            val result = playbackRepository.stream(itemId, profile)
            if (result is MqResult.Success) streams[itemId] = Resolved(result.value, quality, passthrough)
            return result
        }

        /**
         * A new stream for the video [itemId] (ADR 0050), starting at [startPosition], in Settings -> Video's quality for the
         * network kind (#276; Original on Wi-Fi, 480p on mobile data by default). Always asked anew: a video is opened once,
         * and a new play session per opening is what the server expects. Kept for progress reports ([current]).
         */
        suspend fun resolveVideo(
            itemId: MediaId,
            startPosition: Duration = Duration.ZERO,
        ): MqResult<PlaybackStream> {
            val settings = videoPreferences.preferences.first()
            val profile =
                DeviceProfileBuilder.video(
                    decoderMimeTypes,
                    videoDecoders.mimeTypes(),
                    settings.quality(networkMonitor.isMetered()),
                    burnInStyled = settings.burnInStyled,
                    videoRangeTypes = videoRangeTypes(displayHdr.types(), hdrDecoders),
                )
            // The chosen tracks (#274): the server's conversion takes them, and the player selects them in a file.
            val tracks = trackChooser.tracks(itemId)
            val choice = StreamChoice(startPosition, tracks?.audioIndex, tracks?.subtitleIndex)
            val result = playbackRepository.stream(itemId, profile, choice)
            if (result is MqResult.Success) {
                streams[itemId] = Resolved(result.value, StreamingQuality.Original, video = true)
            }
            return result
        }

        /** Keeps [itemId]'s Atmos MIME type for [playsCachedOriginal] and [requestQuality]; `null` changes nothing. */
        fun rememberAtmos(
            itemId: MediaId,
            atmosMime: String?,
        ) {
            if (atmosMime != null) atmosMimes[itemId] = atmosMime
        }

        /** Keeps [itemId]'s file passthrough MIME type for [playsCachedOriginal]; `null` changes nothing. */
        fun rememberFile(
            itemId: MediaId,
            fileMime: String?,
        ) {
            if (fileMime != null) fileMimes[itemId] = fileMime
        }

        /**
         * Whether a complete cached original of [itemId] plays on this route: not a bitstream (E-AC-3, TrueHD, Atmos or
         * not) this route neither passes through nor decodes (a TrueHD file cached over HDMI, now on Bluetooth).
         */
        fun playsCachedOriginal(itemId: MediaId): Boolean {
            val mime = fileMimes[itemId] ?: return true
            return mime in passthrough || mime in decoderMimeTypes
        }

        /** The quality [itemId] is asked for now when the setting says [quality] ([atmosRequest]). */
        fun requestQuality(
            itemId: MediaId,
            quality: StreamingQuality,
        ): StreamingQuality = atmosRequest(atmosMimes[itemId], passthrough, quality)

        /**
         * The stream to prefetch [itemId] with: the one the player already knows (never replaced, so a track that is
         * playing never switches format), or a new one the player will then reuse with its play session.
         */
        suspend fun prefetchStream(
            itemId: MediaId,
            atmosMime: String? = null,
        ): MqResult<PlaybackStream> {
            rememberAtmos(itemId, atmosMime)
            streams[itemId]?.takeUnless { it.local }?.let { return MqResult.Success(it.stream) }
            val passthrough = passthrough
            val quality = atmosRequest(atmosMimes[itemId], passthrough, currentQuality())
            val profile = DeviceProfileBuilder.build(decoderMimeTypes, passthrough, quality)
            val result = playbackRepository.stream(itemId, profile)
            if (result is MqResult.Success) {
                val known = streams.putIfAbsent(itemId, Resolved(result.value, quality, passthrough))
                if (known != null && !known.local) return MqResult.Success(known.stream)
            }
            return result
        }

        /**
         * Records that [itemId] plays from its download, so progress reports still reach the server: a direct play with
         * a play session of our own (no server stream was asked for), also when the download is the server's conversion
         * at [convertedKbps].
         */
        fun playLocal(
            itemId: MediaId,
            convertedKbps: Int? = null,
        ) {
            val known = streams[itemId]?.takeIf { it.local }
            if (known != null) {
                // A download that replaced the cached original (or the other way round) keeps the play session.
                streams[itemId] = Resolved(known.stream, known.quality, local = true, convertedKbps = convertedKbps)
                return
            }
            val stream =
                PlaybackStream(
                    itemId = itemId,
                    url = "",
                    playMethod = PlayMethod.DirectPlay,
                    // Jellyfin's media source of a single-file item has the item's own id.
                    mediaSourceId = itemId.itemId,
                    playSessionId = UUID.randomUUID().toString().replace("-", ""),
                    canSeek = true,
                )
            streams[itemId] = Resolved(stream, StreamingQuality.Original, local = true, convertedKbps = convertedKbps)
        }

        /** Whether [itemId] plays from a download that is the server's conversion (still reported as a direct play). */
        fun isConvertedDownload(itemId: MediaId): Boolean = convertedDownloadKbps(itemId) != null

        /** The bitrate of the converted download [itemId] plays from, `null` when it plays anything else. */
        fun convertedDownloadKbps(itemId: MediaId): Int? = streams[itemId]?.takeIf { it.local }?.convertedKbps

        /**
         * The file to download [itemId] from, whatever the streaming quality: per Download quality (#144) the original
         * when the device plays it as it is, else one converted file (Opus in Ogg, #68, ADR 0024). Never HLS: a download
         * is one file. Not remembered:
         * downloads never share the player's streams or play sessions.
         */
        suspend fun downloadStream(itemId: MediaId): MqResult<DownloadStream> {
            // The quality of now (#144): a download that continues later asks again, at the quality of then.
            val quality = audioPreferences.preferences.first().downloadQuality
            val profile = DeviceProfileBuilder.download(decoderMimeTypes, quality)
            return when (val result = playbackRepository.stream(itemId, profile)) {
                is MqResult.Failure -> {
                    result
                }

                is MqResult.Success -> {
                    if (result.value.segmented) {
                        MqResult.Failure(MqError.Client(0, "download: the server gave a playlist, not a file"))
                    } else {
                        MqResult.Success(DownloadStream(result.value, profile.transcoding.bitrate / BITS_PER_KBIT))
                    }
                }
            }
        }

        private suspend fun currentQuality(): StreamingQuality =
            audioPreferences.preferences.first().qualityFor(networkMonitor.isMetered())

        /** The stream the player uses for [itemId], or `null` if it was never resolved. */
        fun current(itemId: MediaId): PlaybackStream? = streams[itemId]?.stream

        /** The quality [itemId]'s known stream was asked for; `null` while none is known. */
        fun quality(itemId: MediaId): StreamingQuality? = streams[itemId]?.takeUnless { it.video }?.quality

        /** Request headers (authentication) for stream requests, from the current token. */
        suspend fun headers(): Map<String, String> = playbackRepository.streamHeaders()
    }

/**
 * The quality to ask for (ADR 0039, rule 4): the original for a Dolby Atmos file ([atmosMime], its passthrough MIME
 * type) the route passes through ([passthrough]), whatever [quality] says; every other track keeps [quality]. Pure.
 */
internal fun atmosRequest(
    atmosMime: String?,
    passthrough: Set<String>,
    quality: StreamingQuality,
): StreamingQuality = if (atmosMime != null && atmosMime in passthrough) StreamingQuality.Original else quality

/** The file a download reads, and the bitrate the server converts it to when it is not the original (#144). */
data class DownloadStream(
    val stream: PlaybackStream,
    val convertedKbps: Int,
)

private const val BITS_PER_KBIT = 1000
