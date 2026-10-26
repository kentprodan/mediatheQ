package io.github.kentprodan.mediatheq.core.database

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.database.entity.AlbumEntity
import io.github.kentprodan.mediatheq.core.database.entity.AlbumStaging
import io.github.kentprodan.mediatheq.core.database.entity.ArtistEntity
import io.github.kentprodan.mediatheq.core.database.entity.ArtistStaging
import io.github.kentprodan.mediatheq.core.database.entity.PendingUserDataEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackEntity
import io.github.kentprodan.mediatheq.core.database.entity.TrackStaging
import io.github.kentprodan.mediatheq.core.database.entity.UserDataColumns
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

class UserDataDaoTest {
    private val database = inMemoryDatabase()
    private val userDataDao = database.userDataDao()
    private val libraryDao = database.libraryDao()
    private val syncDao = database.syncDao()

    @After
    fun close() = database.close()

    private suspend fun library() {
        syncDao.stageAlbumArtists(listOf(ArtistStaging(ArtistEntity("s", "ar1", "Subcarpați", "Subcarpati", true))))
        syncDao.stageAlbums(listOf(AlbumStaging(AlbumEntity("s", "al1", "Underground", "Underground"))))
        syncDao.stageTracks(listOf(TrackStaging(TrackEntity("s", "t1", "Pe-o margine", "Pe-o margine"))))
        syncDao.swap(1, "s", "user-1")
    }

    private suspend fun trackUserData(id: String) =
        libraryDao
            .track("s", id)
            .first()
            ?.track
            ?.userData

    private val favorite = UserDataColumns(isFavorite = true, rating = 8.0, playCount = 2, played = true)

    @Test
    fun `user data is read from whichever table holds the item`() =
        runTest {
            library()

            assertThat(userDataDao.userData("s", "t1")).isEqualTo(UserDataColumns())
            assertThat(userDataDao.userData("s", "al1")).isEqualTo(UserDataColumns())
            assertThat(userDataDao.userData("s", "ar1")).isEqualTo(UserDataColumns())
            assertThat(userDataDao.userData("s", "missing")).isNull()
        }

    @Test
    fun `setUserData writes tracks, albums and artists`() =
        runTest {
            library()

            listOf("t1", "al1", "ar1").forEach { userDataDao.setUserData("s", it, favorite) }

            assertThat(trackUserData("t1")).isEqualTo(favorite)
            assertThat(
                libraryDao
                    .album("s", "al1")
                    .first()!!
                    .album.userData,
            ).isEqualTo(favorite)
            assertThat(libraryDao.artist("s", "ar1").first()?.userData).isEqualTo(favorite)
        }

    @Test
    fun `setUserData touches only the given item and server`() =
        runTest {
            syncDao.stageTracks(
                listOf(TrackStaging(TrackEntity("s", "t1", "A", "A")), TrackStaging(TrackEntity("s", "t2", "B", "B"))),
            )
            syncDao.swap(1, "s", "user-1")

            userDataDao.setUserData("s", "t1", favorite)
            userDataDao.setUserData("other", "t2", favorite)

            assertThat(trackUserData("t2")).isEqualTo(UserDataColumns())
        }

    @Test
    fun `a write during a sync survives the swap`() =
        runTest {
            library()
            // The next sync staged the track before the write reached the database.
            syncDao.stageTracks(listOf(TrackStaging(TrackEntity("s", "t1", "Pe-o margine", "Pe-o margine"))))

            userDataDao.setUserData("s", "t1", favorite)
            syncDao.swap(2, "s", "user-1")

            assertThat(trackUserData("t1")).isEqualTo(favorite)
        }

    private fun pending(
        item: String,
        user: String = "user-1",
    ) = PendingUserDataEntity("s", user, item, isFavorite = true)

    @Test
    fun `a queued change is written together with the new user data`() =
        runTest {
            library()

            userDataDao.setUserDataAndQueue(favorite, pending("t1"))

            assertThat(trackUserData("t1")).isEqualTo(favorite)
            assertThat(userDataDao.pendingChange("s", "user-1", "t1")).isEqualTo(pending("t1").copy(seq = 1))
        }

    @Test
    fun `pending changes come oldest first and a requeued item moves last`() =
        runTest {
            userDataDao.queue(pending("t1"))
            userDataDao.queue(pending("t2"))
            userDataDao.queue(pending("t1").copy(isFavorite = false))

            assertThat(userDataDao.pendingChanges("s", "user-1").map { it.itemId to it.seq })
                .containsExactly("t2" to 2L, "t1" to 3L)
                .inOrder()
        }

    @Test
    fun `pending changes belong to one user`() =
        runTest {
            userDataDao.queue(pending("t1", user = "user-2"))

            assertThat(userDataDao.pendingChanges("s", "user-1")).isEmpty()
            assertThat(userDataDao.pendingChange("s", "user-1", "t1")).isNull()
        }

    @Test
    fun `a pending change changed again since it was read is not deleted`() =
        runTest {
            userDataDao.queue(pending("t1"))
            userDataDao.queue(pending("t1").copy(isFavorite = false))

            assertThat(userDataDao.deletePending("s", "user-1", "t1", seq = 1)).isEqualTo(0)
            assertThat(userDataDao.deletePending("s", "user-1", "t1", seq = 2)).isEqualTo(1)
            assertThat(userDataDao.pendingChanges("s", "user-1")).isEmpty()
        }

    @Test
    fun `signing out drops pending changes`() =
        runTest {
            userDataDao.queue(pending("t1"))

            syncDao.clearAll()

            assertThat(userDataDao.pendingChanges("s", "user-1")).isEmpty()
        }

    @Test
    fun `the track flow emits again after a write`() =
        runTest {
            library()

            libraryDao.track("s", "t1").test {
                assertThat(awaitItem()?.track?.userData?.isFavorite).isFalse()
                userDataDao.setUserData("s", "t1", favorite)
                assertThat(awaitItem()?.track?.userData?.isFavorite).isTrue()
            }
        }
}
