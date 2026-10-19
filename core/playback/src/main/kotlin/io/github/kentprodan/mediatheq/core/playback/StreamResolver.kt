package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.common.NetworkMonitor
import io.github.kentprodan.mediatheq.core.data.PlaybackRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlaybackStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a track into a playable stream when the player loads it (media3 skill, section 3). One stream per track and
 * network kind: seeks and retries reopen the same stream (a reopen mid-file must never switch format), and progress
 * reports carry its play session.
 */
@Singleton
class StreamResolver
    @Inject
    constructor(
        private val playbackRepository: PlaybackRepository,
        private val decoders: AudioDecoders,
        private val networkMonitor: NetworkMonitor,
    ) {
        private class Resolved(
            val stream: PlaybackStream,
            val metered: Boolean,
        )

        private val decoderMimeTypes by lazy { decoders.mimeTypes() }
        private val streams = ConcurrentHashMap<MediaId, Resolved>()

        /**
         * The stream for [itemId]. [continuing] (reading on from a byte offset) always reuses the known stream; a fresh
         * load reuses it unless the network kind changed since (e.g. home Wi-Fi to cellular).
         */
        suspend fun resolve(
            itemId: MediaId,
            continuing: Boolean = false,
        ): MqResult<PlaybackStream> {
            val metered = networkMonitor.isMetered()
            streams[itemId]?.let { if (continuing || it.metered == metered) return MqResult.Success(it.stream) }
            val result = playbackRepository.stream(itemId, DeviceProfileBuilder.build(decoderMimeTypes, metered))
            if (result is MqResult.Success) streams[itemId] = Resolved(result.value, metered)
            return result
        }

        /** The stream the player uses for [itemId], or `null` if it was never resolved. */
        fun current(itemId: MediaId): PlaybackStream? = streams[itemId]?.stream

        /** Request headers (authentication) for stream requests, from the current token. */
        suspend fun headers(): Map<String, String> = playbackRepository.streamHeaders()
    }
