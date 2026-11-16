package io.github.kentprodan.mediatheq.core.playback

import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.SimilarRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

/** Why Start radio did not start (#379), for one message on screen. */
enum class RadioFailure {
    /** The server's mix had nothing of the library besides the seed (a track without genres, say). */
    NothingSimilar,

    /** No connection to the server. */
    NoServer,

    /** Any other server answer. */
    Failed,
}

/**
 * Start radio (#379): asks the server for a mix from a track, album, artist, genre or playlist and plays it in place
 * of the queue. Fire-and-forget like the other playback commands, so it outlives the menu that asked; a failure
 * waits in [error] for a message on screen. A newer start cancels an older one, and a mix that arrives after the
 * playing track changed is dropped: the user picked something else meanwhile.
 */
@Singleton
class RadioStarter
    @Inject
    constructor(
        private val similar: SimilarRepository,
        private val playback: PlaybackConnection,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val failed = MutableStateFlow<RadioFailure?>(null)

        /** Why the last radio did not start, until [dismissError]. */
        val error: StateFlow<RadioFailure?> = failed.asStateFlow()

        private var job: Job? = null

        /**
         * Plays a mix like [seed]; when the seed is the playing track, [startPosition] keeps it where it is
         * instead of starting it again.
         */
        fun start(
            seed: MediaId,
            startPosition: Duration = Duration.ZERO,
        ) {
            job?.cancel()
            // Main: the connection's commands are made there.
            job =
                scope.launch(Dispatchers.Main.immediate) {
                    val playing =
                        playback.state.value.item
                            ?.id
                    val mix = similar.instantMix(seed)
                    if (playback.state.value.item
                            ?.id != playing
                    ) {
                        return@launch
                    }
                    when (mix) {
                        is MqResult.Success -> {
                            val tracks = mix.value
                            if (tracks.none { it.id != seed }) {
                                failed.value = RadioFailure.NothingSimilar
                            } else {
                                val position = if (tracks.first().id == seed) startPosition else Duration.ZERO
                                playback.play(tracks, startPosition = position)
                            }
                        }

                        is MqResult.Failure -> {
                            // Unauthorized signs out already: no second message.
                            failed.value =
                                when (mix.error) {
                                    MqError.Unauthorized -> null
                                    MqError.Network -> RadioFailure.NoServer
                                    else -> RadioFailure.Failed
                                }
                        }
                    }
                }
        }

        fun dismissError() {
            failed.value = null
        }
    }
