package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.database.dao.UserDataDao
import io.github.kentprodan.mediatheq.core.database.entity.UserDataColumns
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import io.github.kentprodan.mediatheq.core.server.UserDataSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optimistic favorite and rating writes (architecture skill, section 4), one at a time. Until the server has a change
 * it is kept as an override, so neither a socket echo nor a sync swap can put the old value back. A change made while
 * its track plays stays an override until that play ends and is sent again after each progress and the stop report,
 * which may carry the server's older value over it (R2).
 * shortcut: the R2 override lives in memory; if the app dies before the stop report the last re-send may be missing.
 * shortcut: while an R2 override is held, a change made to the same item in another client is hidden until stop.
 */
@Singleton
class DefaultUserDataRepository
    @Inject
    constructor(
        private val userDataDao: UserDataDao,
        private val sink: UserDataSink,
        private val accountStore: AccountStore,
        private val sessionRepository: SessionRepository,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) : UserDataRepository {
        // writeLock: one server write at a time, so answers cannot arrive out of order. stateLock: overrides and Room.
        private val writeLock = Mutex()
        private val stateLock = Mutex()
        private val inFlight = mutableMapOf<MediaId, Change>()
        private val whilePlaying = mutableMapOf<MediaId, Change>()
        private var playing: MediaId? = null
        private val mutableError = MutableStateFlow<MqError?>(null)
        override val error: StateFlow<MqError?> = mutableError.asStateFlow()

        override fun dismissError() {
            mutableError.value = null
        }

        override suspend fun setFavorite(
            id: MediaId,
            isFavorite: Boolean,
        ) = write(id, Change(isFavorite = isFavorite)) { sink.setFavorite(it, id, isFavorite) }

        override suspend fun setRating(
            id: MediaId,
            stars: Int?,
        ): MqResult<Unit> {
            val rating = stars?.let(UserData::ratingOf)
            return write(id, Change(setsRating = true, rating = rating)) { sink.setRating(it, id, rating) }
        }

        /** The server says [changes] are its user data now; pending overrides stay on top. */
        internal suspend fun applyFromServer(changes: Map<MediaId, UserData>) =
            stateLock.withLock {
                storageSafe {
                    changes.forEach { (id, data) ->
                        store(
                            id,
                            overrideOf(id)?.applyTo(data.toColumns()) ?: data.toColumns(),
                        )
                    }
                    MqResult.Success(Unit)
                }
            }

        /** Writes the overrides again, e.g. after a sync swapped in rows fetched before them. */
        internal suspend fun reapplyOverrides() =
            stateLock.withLock {
                storageSafe {
                    (inFlight.keys + whilePlaying.keys).forEach { id ->
                        val stored = userDataDao.userData(id.serverId.value, id.itemId) ?: return@forEach
                        store(id, overrideOf(id)!!.applyTo(stored))
                    }
                    MqResult.Success(Unit)
                }
            }

        /** [itemId] started playing: changes to it from now on are R2 overrides. */
        internal suspend fun onPlaybackStarted(itemId: MediaId) = stateLock.withLock { playing = itemId }

        /** A progress ([stopped] = false) or stop report of [itemId] went out: send its R2 override again. */
        internal suspend fun onPlaybackReported(
            itemId: MediaId,
            stopped: Boolean,
        ) {
            val change =
                stateLock.withLock {
                    val change = whilePlaying[itemId]
                    if (stopped) {
                        whilePlaying.remove(itemId)
                        if (playing == itemId) playing = null
                    }
                    change
                } ?: return
            val server = accountStore.signedInServer() ?: return
            // Best effort: the change already reached the server once; a failure here is not the user's to see.
            writeLock.withLock {
                change.isFavorite?.let { sink.setFavorite(server, itemId, it) }
                if (change.setsRating) sink.setRating(server, itemId, change.rating)
            }
        }

        private suspend fun write(
            id: MediaId,
            change: Change,
            call: suspend (SignedInServer) -> MqResult<Unit>,
        ): MqResult<Unit> =
            // In the app scope: leaving the screen must not stop a change between Room and the server.
            scope
                .async {
                    writeLock.withLock {
                        storageSafe {
                            // Signed out meanwhile: nothing to do, and not a session expiry.
                            val server =
                                accountStore.signedInServer()
                                    ?: return@storageSafe MqResult.Failure(MqError.Unauthorized)
                            val before =
                                stateLock.withLock {
                                    val stored =
                                        userDataDao.userData(id.serverId.value, id.itemId)
                                            ?: return@storageSafe MqResult.Failure(MqError.NotFound)
                                    inFlight[id] = change
                                    store(id, change.applyTo(stored))
                                    stored
                                }
                            val result = call(server)
                            withContext(NonCancellable) {
                                stateLock.withLock {
                                    inFlight.remove(id)
                                    if (result is MqResult.Failure) {
                                        userDataDao.userData(id.serverId.value, id.itemId)?.let { now ->
                                            store(id, change.undo(before).applyTo(now))
                                        }
                                    } else if (id == playing) {
                                        whilePlaying[id] = whilePlaying[id]?.then(change) ?: change
                                    }
                                }
                                if ((result as? MqResult.Failure)?.error == MqError.Unauthorized) {
                                    sessionRepository.onUnauthorized()
                                }
                            }
                            result
                        }
                    }
                }.await()
                .also { result ->
                    // Unauthorized means signed out (or about to be): the login screen says it, not a message.
                    val error = (result as? MqResult.Failure)?.error
                    if (error != null && error != MqError.Unauthorized) mutableError.value = error
                }

        private fun overrideOf(id: MediaId): Change? {
            val playingChange = whilePlaying[id]
            val pending = inFlight[id]
            return if (playingChange != null &&
                pending != null
            ) {
                playingChange.then(pending)
            } else {
                pending ?: playingChange
            }
        }

        private suspend fun store(
            id: MediaId,
            data: UserDataColumns,
        ) = userDataDao.setUserData(id.serverId.value, id.itemId, data)
    }

/** What the user changed on an item: the favorite flag when [isFavorite] is set, the rating when [setsRating]. */
private data class Change(
    val isFavorite: Boolean? = null,
    val setsRating: Boolean = false,
    val rating: Double? = null,
) {
    fun applyTo(data: UserDataColumns) =
        data.copy(isFavorite = isFavorite ?: data.isFavorite, rating = if (setsRating) rating else data.rating)

    /** This change followed by [next]. */
    fun then(next: Change) =
        Change(
            next.isFavorite ?: isFavorite,
            setsRating || next.setsRating,
            if (next.setsRating) next.rating else rating,
        )

    /** The change that puts back what [before] had. */
    fun undo(before: UserDataColumns) = Change(isFavorite?.let { before.isFavorite }, setsRating, before.rating)
}
