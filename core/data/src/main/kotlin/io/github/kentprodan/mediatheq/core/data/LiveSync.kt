package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.common.NetworkMonitor
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.server.LiveEvent
import io.github.kentprodan.mediatheq.core.server.LiveEvents
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps Room in step with the server's live events (jellyfin skill, section 9): user data changes are written at once,
 * library changes start a full sync, and each connection sends the favorite and rating changes waiting for the server. Listens while signed in, online, and the app is in the foreground or playing; a dropped
 * connection comes back after 1, 2, 4 ... 60 s (with jitter), and a 401 signs out instead.
 * shortcut: events missed while not listening wait for the next full sync; sync on reconnect if that shows up.
 */
@Singleton
class LiveSync
    @Inject
    constructor(
        private val liveEvents: LiveEvents,
        private val accountStore: AccountStore,
        private val sessionRepository: SessionRepository,
        private val userData: DefaultUserDataRepository,
        private val librarySync: DefaultLibrarySyncRepository,
        private val networkMonitor: NetworkMonitor,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) {
        private val foreground = MutableStateFlow(false)
        private val playing = MutableStateFlow(false)
        private val libraryChanges = Channel<Unit>(Channel.CONFLATED)

        /** Jitter source; tests fix it. */
        internal var random: Random = Random.Default

        /** Whether an activity of the app is visible. */
        fun setForeground(value: Boolean) {
            foreground.value = value
        }

        /** Whether the player is playing. */
        fun setPlaying(value: Boolean) {
            playing.value = value
        }

        /** Follows the account and app state for the app's lifetime; call once from the Application. */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun start() {
            scope.launch {
                // Offline there is nothing to listen to; coming back online connects at once, without a backoff wait.
                combine(accountStore.account, foreground, playing, networkMonitor.online) {
                    account,
                    isForeground,
                    isPlaying,
                    isOnline,
                    ->
                    (account as? MqResult.Success)?.value?.takeIf { isOnline && (isForeground || isPlaying) }
                }.mapLatest { account -> account?.let { accountStore.signedInServer(it) } }
                    .distinctUntilChanged()
                    .collectLatest { server -> if (server != null) listen(server) }
            }
            scope.launch { syncOnLibraryChanges() }
        }

        private suspend fun listen(server: SignedInServer) {
            var failures = 0
            while (true) {
                var closedWith: MqError = MqError.Network
                coroutineScope {
                    var stable: Job? = null
                    liveEvents.events(server).collect { event ->
                        when (event) {
                            // Only a connection that lasts resets the wait: a server that accepts and drops at once
                            // must not be called every second.
                            LiveEvent.Connected -> {
                                // The server is reachable: send what waited for it (#56). In the app scope, so slow
                                // writes neither hold up events nor stop when the socket closes.
                                scope.launch { userData.flushPending() }
                                stable =
                                    launch {
                                        delay(STABLE_AFTER)
                                        failures = 0
                                    }
                            }

                            is LiveEvent.UserDataChanged -> {
                                userData.applyFromServer(event.changes)
                            }

                            LiveEvent.LibraryChanged -> {
                                libraryChanges.trySend(Unit)
                            }

                            is LiveEvent.Closed -> {
                                closedWith = event.error
                            }
                        }
                    }
                    stable?.cancel()
                }
                if (closedWith == MqError.Unauthorized) {
                    withContext(NonCancellable) { sessionRepository.onUnauthorized() }
                    return
                }
                delay(retryDelay(failures++))
            }
        }

        /** 1 s doubling up to 60 s, each +/- 20 %, so many phones do not come back at the same moment. */
        internal fun retryDelay(failures: Int): Duration {
            val base = MIN_RETRY * (1 shl min(failures, MAX_DOUBLINGS)).toDouble()
            return minOf(base, MAX_RETRY) * (1 + (random.nextDouble() * 2 - 1) * JITTER)
        }

        /**
         * One sync per burst of library changes: it starts once the server has been quiet for [QUIET], or [MAX_WAIT]
         * after the first change, so a long scan still shows up while it runs.
         */
        private suspend fun syncOnLibraryChanges() {
            while (true) {
                libraryChanges.receive()
                withTimeoutOrNull(MAX_WAIT) {
                    while (withTimeoutOrNull(QUIET) { libraryChanges.receive() } != null) Unit
                }
                librarySync.syncAfterChange()
            }
        }

        internal companion object {
            val MIN_RETRY = 1.seconds
            val MAX_RETRY = 60.seconds
            const val MAX_DOUBLINGS = 6
            const val JITTER = 0.2
            val STABLE_AFTER = 30.seconds
            val QUIET = 5.seconds
            val MAX_WAIT = 30.seconds
        }
    }
