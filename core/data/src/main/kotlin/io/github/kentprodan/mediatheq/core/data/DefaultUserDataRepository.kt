package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.AppLog
import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.database.dao.UserDataDao
import io.github.kentprodan.mediatheq.core.database.entity.PendingUserDataEntity
import io.github.kentprodan.mediatheq.core.database.entity.UserDataColumns
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import io.github.kentprodan.mediatheq.core.server.UserDataSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optimistic favorite and rating writes (architecture skill, section 4), one at a time. A change is stored in Room as
 * waiting (pending_user_data) until the server answers it, and is kept as an override meanwhile, so neither a socket
 * echo nor a sync swap can put the old value back. Without network it simply waits, also across restarts, and is sent
 * oldest first with the next change or when [LiveSync] connects again (#56); only a server answer undoes it. A change
 * made while its track plays stays an override until that play ends and is sent again after each progress and the stop
 * report, which may carry the server's older value over it (R2).
 * shortcut: the R2 override lives in memory; if the app dies before the stop report the last re-send may be missing.
 * shortcut: while an R2 override is held, a change made to the same item in another client is hidden until stop.
 * shortcut: back online in the background with nothing playing, waiting changes go only with the next connection.
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
        private val log: AppLog,
    ) : UserDataRepository {
        // writeLock: one server write at a time, so answers cannot arrive out of order. stateLock: overrides and Room.
        private val writeLock = Mutex()
        private val stateLock = Mutex()
        private val whilePlaying = mutableMapOf<MediaId, Change>()

        // Changes the server confirmed while a sync was fetching: its pages may hold the older value.
        private val sinceSyncStart = mutableMapOf<MediaId, Change>()
        private var playing: MediaId? = null
        private val mutableError = MutableStateFlow<MqError?>(null)
        override val error: StateFlow<MqError?> = mutableError.asStateFlow()

        override fun dismissError() {
            mutableError.value = null
        }

        override suspend fun setFavorite(
            id: MediaId,
            isFavorite: Boolean,
        ) = write(id, Change(isFavorite = isFavorite))

        override suspend fun setPlayed(
            id: MediaId,
            played: Boolean,
        ) = write(id, Change(played = played))

        override suspend fun setRating(
            id: MediaId,
            halfStars: Int?,
        ): MqResult<Unit> {
            require(halfStars == null || halfStars in 1..10) { "halfStars must be 1-10" }
            val rating = halfStars?.toDouble()
            return write(id, Change(setsRating = true, rating = rating))
        }

        /** The server says [changes] are its user data now; pending overrides stay on top. */
        internal suspend fun applyFromServer(changes: Map<MediaId, UserData>) =
            stateLock.withLock {
                storageSafe {
                    val userId = accountStore.signedInServer()?.userId
                    changes.forEach { (id, data) ->
                        val override = overrideOf(id, userId)
                        store(id, override?.applyTo(data.toColumns()) ?: data.toColumns())
                        // A video's resume point (#273); a watched change still waiting keeps it cleared.
                        if (override?.played != true) {
                            userDataDao.setVideoProgress(
                                id.serverId.value,
                                id.itemId,
                                data.playbackPosition.inWholeMilliseconds,
                                data.playedPercentage,
                            )
                        }
                    }
                    MqResult.Success(Unit)
                }
            }

        /** A video is no longer halfway on the server (#275): its resume point goes here too. */
        internal suspend fun clearVideoProgress(id: MediaId) =
            stateLock.withLock {
                storageSafe { MqResult.Success(userDataDao.setVideoProgress(id.serverId.value, id.itemId, 0, null)) }
            }

        /** A sync starts fetching: what it reads from now on already has the changes confirmed before. */
        internal suspend fun onSyncStarted() = stateLock.withLock { sinceSyncStart.clear() }

        /** Writes the overrides again after a sync swapped in rows fetched before them. */
        internal suspend fun reapplyOverrides() =
            stateLock.withLock {
                storageSafe {
                    val server = accountStore.signedInServer()
                    val waiting =
                        server
                            ?.let { userDataDao.pendingChanges(it.serverId.value, it.userId) }
                            .orEmpty()
                            .map { MediaId(ServerId(it.serverId), it.itemId) }
                    (sinceSyncStart.keys + waiting + whilePlaying.keys).forEach { id ->
                        val stored = userDataDao.userData(id.serverId.value, id.itemId) ?: return@forEach
                        val confirmed = sinceSyncStart[id]
                        val pending = overrideOf(id, server?.userId)
                        val change =
                            if (confirmed != null &&
                                pending != null
                            ) {
                                confirmed.then(pending)
                            } else {
                                pending ?: confirmed!!
                            }
                        store(id, change.applyTo(stored))
                    }
                    sinceSyncStart.clear()
                    MqResult.Success(Unit)
                }
            }

        /** Signed out or another account: nothing pending belongs to the next user (the wipe drops waiting rows). */
        internal suspend fun forgetPending() =
            stateLock.withLock {
                whilePlaying.clear()
                sinceSyncStart.clear()
                playing = null
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
            // Best effort: the change already reached the server once; a failure here is not the user's to see. Not
            // awaited: playback reports must not wait behind a slow write.
            scope.launch {
                val server = accountStore.signedInServer() ?: return@launch
                writeLock.withLock {
                    // Not the watched flag: sent again it would clear the resume point of what plays (#273).
                    change.isFavorite?.let { sink.setFavorite(server, itemId, it) }
                    if (change.setsRating) sink.setRating(server, itemId, change.rating)
                }
            }
        }

        /** Sends the changes still waiting for the server, e.g. once it can be reached again (#56). */
        internal suspend fun flushPending() {
            accountStore.signedInServer()?.let { flush(it, target = null) }
        }

        private suspend fun write(
            id: MediaId,
            change: Change,
        ): MqResult<Unit> =
            // In the app scope: leaving the screen must not stop a change between Room and the server.
            scope
                .async {
                    // Signed out meanwhile: nothing to do, and not a session expiry.
                    val server =
                        accountStore.signedInServer() ?: return@async MqResult.Failure(MqError.Unauthorized)
                    when (val queued = storageSafe { stateLock.withLock { queue(server, id, change) } }) {
                        is MqResult.Failure -> queued.also { surface(it.error) }
                        is MqResult.Success -> flush(server, target = id)
                    }
                }.await()

        /** Shows [change] in Room and stores it, merged with what [id] still has waiting, until the server has it. */
        private suspend fun queue(
            server: SignedInServer,
            id: MediaId,
            change: Change,
        ): MqResult<Unit> {
            val stored =
                userDataDao.userData(id.serverId.value, id.itemId) ?: return MqResult.Failure(MqError.NotFound)
            val queued = userDataDao.pendingChange(id.serverId.value, server.userId, id.itemId)
            val merged = queued?.toChange()?.then(change) ?: change
            val pending =
                PendingUserDataEntity(
                    serverId = id.serverId.value,
                    userId = server.userId,
                    itemId = id.itemId,
                    isFavorite = merged.isFavorite,
                    setsRating = merged.setsRating,
                    rating = merged.rating,
                    played = merged.played,
                    // Each field keeps what it had before its first unsent change.
                    beforeFavorite = if (queued?.isFavorite != null) queued.beforeFavorite else stored.isFavorite,
                    beforeRating = if (queued?.setsRating == true) queued.beforeRating else stored.rating,
                    beforePlayed = if (queued?.played != null) queued.beforePlayed else stored.played,
                )
            userDataDao.setUserDataAndQueue(change.applyTo(stored), pending)
            // shortcut: an undone watched change leaves the resume point cleared until the next sync or socket event.
            if (change.played == true) userDataDao.setVideoProgress(id.serverId.value, id.itemId, 0, null)
            return MqResult.Success(Unit)
        }

        /**
         * Sends the waiting changes of [server]'s user, oldest first, and returns how the change of [target] went. No
         * network: the changes stay for later, and that is no failure. Any answer from the server settles a change.
         */
        private suspend fun flush(
            server: SignedInServer,
            target: MediaId?,
        ): MqResult<Unit> =
            writeLock.withLock {
                storageSafe {
                    var outcome: MqResult<Unit> = MqResult.Success(Unit)
                    for (queued in userDataDao.pendingChanges(server.serverId.value, server.userId)) {
                        // Signed out or another account meanwhile: the rest is not ours to send.
                        if (accountStore.signedInServer() != server) break
                        // Read again: the change may have been made again or settled since the list was read.
                        val row = userDataDao.pendingChange(queued.serverId, queued.userId, queued.itemId) ?: continue
                        val id = MediaId(server.serverId, row.itemId)
                        val change = row.toChange()
                        val favorite = change.isFavorite?.let { sink.setFavorite(server, id, it) }
                        if (favorite.isNetworkFailure()) {
                            log.info(TAG, "Queued offline: ${change.describe()} [${id.itemId}]")
                            break
                        }
                        val rating =
                            if (change.setsRating && (favorite as? MqResult.Failure)?.error != MqError.Unauthorized) {
                                sink.setRating(server, id, change.rating)
                            } else {
                                null
                            }
                        if (rating.isNetworkFailure()) {
                            log.info(TAG, "Queued offline: ${change.describe()} [${id.itemId}]")
                            break
                        }
                        val unauthorized =
                            listOf(favorite, rating).any { (it as? MqResult.Failure)?.error == MqError.Unauthorized }
                        val played = change.played?.takeIf { !unauthorized }?.let { sink.setPlayed(server, id, it) }
                        if (played.isNetworkFailure()) {
                            log.info(TAG, "Queued offline: ${change.describe()} [${id.itemId}]")
                            break
                        }
                        val sent =
                            change.only(
                                favorite is MqResult.Success,
                                rating is MqResult.Success,
                                played is MqResult.Success,
                            )
                        val refused =
                            change.only(
                                favorite is MqResult.Failure,
                                rating !is MqResult.Success,
                                played !is MqResult.Success,
                            )
                        val error =
                            listOf(favorite, rating, played).firstNotNullOfOrNull { (it as? MqResult.Failure)?.error }
                        if (sent != Change()) log.info(TAG, "Sent: ${sent.describe()} [${id.itemId}]")
                        if (refused != Change()) log.warn(TAG, "Refused: ${refused.describe()} [${id.itemId}]: $error")
                        val stillSignedIn =
                            withContext(NonCancellable) {
                                // Checked under stateLock: signing out clears the account before the wipe takes it.
                                stateLock
                                    .withLock {
                                        (accountStore.signedInServer() == server).also {
                                            if (it) settle(id, row, sent, refused)
                                        }
                                    }.also {
                                        if (it &&
                                            error == MqError.Unauthorized
                                        ) {
                                            sessionRepository.onUnauthorized()
                                        }
                                    }
                            }
                        if (!stillSignedIn) break
                        if (error != null) {
                            surface(error)
                            if (id == target) outcome = MqResult.Failure(error)
                            // Signing out drops the rest.
                            if (error == MqError.Unauthorized) break
                        }
                    }
                    outcome
                }.also { if ((it as? MqResult.Failure)?.error == MqError.Storage) surface(MqError.Storage) }
            }

        /** The server answered [row]: it took [sent] and refused [refused], which is undone. */
        private suspend fun settle(
            id: MediaId,
            row: PendingUserDataEntity,
            sent: Change,
            refused: Change,
        ) {
            // Gone: signed out meanwhile, and nothing of it belongs to the next user.
            val now = userDataDao.pendingChange(row.serverId, row.userId, row.itemId) ?: return
            // Changed again meanwhile: the newer change goes next and decides, so nothing is undone.
            // shortcut: if that newer change is refused, the item goes back to its values before the first change, not
            // to what the server took from this row; fine while a refusal means the item cannot be changed at all.
            if (now.seq == row.seq) {
                userDataDao.deletePending(row.serverId, row.userId, row.itemId, row.seq)
                if (refused != Change()) {
                    val before =
                        UserDataColumns(
                            isFavorite = row.beforeFavorite,
                            rating = row.beforeRating,
                            played = row.beforePlayed,
                        )
                    userDataDao.userData(id.serverId.value, id.itemId)?.let { current ->
                        store(id, refused.undo(before).applyTo(current))
                    }
                }
            }
            if (sent != Change()) {
                sinceSyncStart[id] = sinceSyncStart[id]?.then(sent) ?: sent
                if (id == playing) whilePlaying[id] = whilePlaying[id]?.then(sent) ?: sent
            }
        }

        /** Offers [error] as the app-wide message; a 401 is not one, the login screen says it. */
        private fun surface(error: MqError) {
            if (error != MqError.Unauthorized) mutableError.value = error
        }

        /** The waiting change of [id] for [userId], on top of its R2 override. */
        private suspend fun overrideOf(
            id: MediaId,
            userId: String?,
        ): Change? {
            val playingChange = whilePlaying[id]
            val pending = userId?.let { userDataDao.pendingChange(id.serverId.value, it, id.itemId) }?.toChange()
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

private fun MqResult<Unit>?.isNetworkFailure() = (this as? MqResult.Failure)?.error == MqError.Network

private fun PendingUserDataEntity.toChange() = Change(isFavorite, setsRating, rating, played)

/**
 * What the user changed on an item: the favorite flag when [isFavorite] is set, the rating when [setsRating], the
 * watched flag when [played] is set (#273).
 */
private data class Change(
    val isFavorite: Boolean? = null,
    val setsRating: Boolean = false,
    val rating: Double? = null,
    val played: Boolean? = null,
) {
    fun applyTo(data: UserDataColumns) =
        data.copy(
            isFavorite = isFavorite ?: data.isFavorite,
            rating = if (setsRating) rating else data.rating,
            played = played ?: data.played,
        )

    /** This change followed by [next]. */
    fun then(next: Change) =
        Change(
            next.isFavorite ?: isFavorite,
            setsRating || next.setsRating,
            if (next.setsRating) next.rating else rating,
            next.played ?: played,
        )

    /** The part of this change in the favorite, the rating and the watched flag, each when its flag says so. */
    fun only(
        withFavorite: Boolean,
        withRating: Boolean,
        withPlayed: Boolean = false,
    ) = Change(
        isFavorite.takeIf { withFavorite },
        setsRating && withRating,
        rating.takeIf { withRating },
        played.takeIf { withPlayed },
    )

    /** The change that puts back what [before] had. */
    fun undo(before: UserDataColumns) =
        Change(isFavorite?.let { before.isFavorite }, setsRating, before.rating, played?.let { before.played })
}

/** "favorite on, rating 7.0, watched" for the log. */
private fun Change.describe(): String =
    listOfNotNull(
        isFavorite?.let { if (it) "favorite on" else "favorite off" },
        if (setsRating) "rating ${rating ?: "cleared"}" else null,
        played?.let { if (it) "watched" else "not watched" },
    ).joinToString(", ")

private const val TAG = "UserData"
