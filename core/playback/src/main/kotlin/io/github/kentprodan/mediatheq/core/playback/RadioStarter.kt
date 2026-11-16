package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.SimilarRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Start radio (#379): asks the server for a mix from a track, album, artist, genre or playlist and plays it in place
 * of the queue. Fire-and-forget like the other playback commands, so it outlives the menu that asked; a failure (no
 * server, or nothing of the mix in the library) waits in [error] for a message on screen.
 */
@Singleton
class RadioStarter
    @Inject
    constructor(
        private val similar: SimilarRepository,
        private val playback: PlaybackConnection,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val failed = MutableStateFlow<MqError?>(null)

        /** Why the last radio did not start, until [dismissError]; [MqError.NotFound] when the mix had nothing. */
        val error: StateFlow<MqError?> = failed.asStateFlow()

        fun start(seed: MediaId) {
            scope.launch {
                when (val mix = similar.instantMix(seed)) {
                    is MqResult.Success -> {
                        if (mix.value.isEmpty()) failed.value = MqError.NotFound else playback.play(mix.value)
                    }

                    is MqResult.Failure -> {
                        failed.value = mix.error
                    }
                }
            }
        }

        fun dismissError() {
            failed.value = null
        }
    }
