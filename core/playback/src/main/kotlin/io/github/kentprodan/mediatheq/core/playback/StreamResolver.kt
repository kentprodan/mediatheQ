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
 * Turns a track into a playable stream when the player is about to load it (media3 skill, section 3). Remembers the
 * stream it handed out for each track, so progress reports carry the same play session.
 */
@Singleton
class StreamResolver
    @Inject
    constructor(
        private val playbackRepository: PlaybackRepository,
        private val decoders: AudioDecoders,
        private val networkMonitor: NetworkMonitor,
    ) {
        private val decoderMimeTypes by lazy { decoders.mimeTypes() }
        private val streams = ConcurrentHashMap<MediaId, PlaybackStream>()

        /** A fresh stream for [itemId] for the current network; each call is a new play session. */
        suspend fun resolve(itemId: MediaId): MqResult<PlaybackStream> {
            val profile = DeviceProfileBuilder.build(decoderMimeTypes, networkMonitor.isMetered())
            val result = playbackRepository.stream(itemId, profile)
            if (result is MqResult.Success) streams[itemId] = result.value
            return result
        }

        /** The stream last handed out for [itemId], or `null` if it was never resolved (or already finished). */
        fun current(itemId: MediaId): PlaybackStream? = streams[itemId]

        /** Forgets [itemId]'s stream once its session ended, so a replay asks for a new one. */
        fun finished(itemId: MediaId) {
            streams.remove(itemId)
        }

        /** Request headers (authentication) for stream requests, from the current token. */
        suspend fun headers(): Map<String, String> = playbackRepository.streamHeaders()
    }
