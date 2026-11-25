package io.github.kentprodan.mediatheq.core.playback

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The screens' "the chosen tracks changed" for the playing video (#678), which the service applies. One per process. */
@Singleton
class VideoTrackRequests
    @Inject
    constructor() {
        private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        internal val changes: SharedFlow<Unit> = requests.asSharedFlow()

        fun request() {
            requests.tryEmit(Unit)
        }
    }
