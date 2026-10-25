package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.common.NetworkMonitor
import io.github.kentprodan.mediatheq.core.data.AudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.PlaybackRepository
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlayMethod
import io.github.kentprodan.mediatheq.core.model.PlaybackStream
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

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
    ) {
        private class Resolved(
            val stream: PlaybackStream,
            val quality: StreamingQuality,
            /** Played from the download, not from the server. */
            val local: Boolean = false,
        )

        private val decoderMimeTypes by lazy { decoders.mimeTypes() }
        private val streams = ConcurrentHashMap<MediaId, Resolved>()

        /**
         * The stream for [itemId]. [continuing] always reuses the known stream: the player's file source passes it on
         * every open, so it keeps the stream the item started with (ADR 0019). Otherwise (choosing the item's source) the
         * known stream is reused unless the quality changed since (another network kind, e.g. home Wi-Fi to cellular, or
         * another setting). Reading on from a download that was removed meanwhile asks for the original file: the bytes
         * already played came from it.
         */
        suspend fun resolve(
            itemId: MediaId,
            continuing: Boolean = false,
        ): MqResult<PlaybackStream> {
            val known = streams[itemId]
            val fromDownload = continuing && known?.local == true
            val quality = if (fromDownload) StreamingQuality.Original else currentQuality()
            known?.takeUnless { it.local }?.let {
                if (continuing || it.quality == quality) return MqResult.Success(it.stream)
            }
            val result = playbackRepository.stream(itemId, DeviceProfileBuilder.build(decoderMimeTypes, quality))
            if (result is MqResult.Success) streams[itemId] = Resolved(result.value, quality)
            return result
        }

        /**
         * The stream to prefetch [itemId] with: the one the player already knows (never replaced, so a track that is
         * playing never switches format), or a new one the player will then reuse with its play session.
         */
        suspend fun prefetchStream(itemId: MediaId): MqResult<PlaybackStream> {
            streams[itemId]?.takeUnless { it.local }?.let { return MqResult.Success(it.stream) }
            val quality = currentQuality()
            val result = playbackRepository.stream(itemId, DeviceProfileBuilder.build(decoderMimeTypes, quality))
            if (result is MqResult.Success) {
                val known = streams.putIfAbsent(itemId, Resolved(result.value, quality))
                if (known != null && !known.local) return MqResult.Success(known.stream)
            }
            return result
        }

        /**
         * Records that [itemId] plays from its download, so progress reports still reach the server: a direct play with
         * a play session of our own (no server stream was asked for).
         */
        fun playLocal(itemId: MediaId) {
            if (streams[itemId]?.local == true) return
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
            streams[itemId] = Resolved(stream, StreamingQuality.Original, local = true)
        }

        /**
         * The original file of [itemId] for a download, whatever the streaming quality, and only when the device plays it as it is
         * (a converted stream cannot resume at a byte offset). Not remembered: downloads never share the player's
         * streams or play sessions.
         */
        suspend fun downloadStream(itemId: MediaId): MqResult<PlaybackStream> =
            when (
                val result =
                    playbackRepository.stream(
                        itemId,
                        DeviceProfileBuilder.build(decoderMimeTypes, StreamingQuality.Original),
                    )
            ) {
                is MqResult.Failure -> {
                    result
                }

                is MqResult.Success -> {
                    if (result.value.playMethod == PlayMethod.DirectPlay) {
                        result
                    } else {
                        // shortcut: no converted downloads (e.g. Atmos E-AC-3 JOC on a Pixel); #68.
                        MqResult.Failure(MqError.Client(0, "download: the device cannot play the original"))
                    }
                }
            }

        private suspend fun currentQuality(): StreamingQuality =
            audioPreferences.preferences.first().qualityFor(networkMonitor.isMetered())

        /** The stream the player uses for [itemId], or `null` if it was never resolved. */
        fun current(itemId: MediaId): PlaybackStream? = streams[itemId]?.stream

        /** Request headers (authentication) for stream requests, from the current token. */
        suspend fun headers(): Map<String, String> = playbackRepository.streamHeaders()
    }
