package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.AppLog
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.database.entity.SeasonEntity
import io.github.kentprodan.mediatheq.core.database.entity.SeasonStaging
import io.github.kentprodan.mediatheq.core.database.entity.SeriesStaging
import io.github.kentprodan.mediatheq.core.database.entity.VideoStaging
import io.github.kentprodan.mediatheq.core.database.entity.VideoStreamStaging
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.datastore.StoredAccount
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.MediaStreamInfo
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.StreamType
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoFormat
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.model.VideoRange
import io.github.kentprodan.mediatheq.core.server.ServerCapabilities
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibrarySource
import io.github.kentprodan.mediatheq.core.testing.FakeNetworkMonitor
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.FakeTokenCipher
import io.github.kentprodan.mediatheq.core.testing.FakeUserDataSink
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import io.github.kentprodan.mediatheq.core.testing.inMemoryDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class DefaultVideoRepositoryTest {
    private val database = inMemoryDatabase()
    private val preferences = FakeLibraryPreferencesRepository()
    private val accountStore = AccountStore(InMemoryPreferencesDataStore(), FakeTokenCipher())
    private val source = FakeLibrarySource()
    private val repository =
        DefaultVideoRepository(
            database.videoDao(),
            OfflineFilter(preferences, FakeNetworkMonitor()) { FakeDownloadRepository() },
            ServerCapabilities(video = true),
            source,
            accountStore,
            DefaultUserDataRepository(
                database.userDataDao(),
                FakeUserDataSink(),
                accountStore,
                FakeSessionRepository(),
                CoroutineScope(Dispatchers.Unconfined),
                AppLog.None,
            ),
        )
    private val server = ServerId("s")

    private fun id(value: String) = MediaId(server, value)

    @After
    fun close() = database.close()

    private val movie =
        Video(
            id("m1"),
            VideoKind.Movie,
            "Moșoi",
            year = 2020,
            duration = 90.minutes,
            genres = listOf("Comedy", "Drama"),
            format = VideoFormat("hevc", 3840, 2160, VideoRange.DolbyVision, 20_000),
            userData = UserData(played = false, playbackPosition = 12.minutes, playedPercentage = 13.3),
        )

    private suspend fun library() {
        val dao = database.syncDao()
        dao.stageVideos(
            listOf(
                movie,
                Video(id("m2"), VideoKind.Movie, "Alpha", year = 2024, userData = UserData(played = true)),
                Video(id("e1"), VideoKind.Episode, "Pilot", seriesId = id("sr1"), seasonId = id("se1")),
            ).map { VideoStaging(it.toEntity()) },
        )
        dao.stageVideoStreams(
            movie
                .copy(streams = listOf(MediaStreamInfo(3, StreamType.Subtitle, "subrip", "eng", isExternal = true)))
                .streamRows()
                .map(::VideoStreamStaging),
        )
        dao.stageSeries(
            listOf(SeriesStaging(Series(id("sr1"), "Show", userData = UserData(unplayedCount = 5)).toEntity())),
        )
        dao.stageSeasons(listOf(SeasonStaging(SeasonEntity("s", "se1", "sr1", "Season 1", 1))))
        dao.swap(1, "s", "u")
    }

    @Test
    fun `a movie keeps every field through the database`() =
        runTest {
            library()

            assertThat(repository.video(id("m1")).first()).isEqualTo(movie)
            assertThat(
                repository
                    .streams(id("m1"))
                    .first()
                    .single()
                    .isExternal,
            ).isTrue()
        }

    @Test
    fun `lists sort and filter, counts follow`() =
        runTest {
            library()

            assertThat(repository.movies(VideoSort.Name, unwatched = false).first().map { it.title })
                .containsExactly("Alpha", "Moșoi")
                .inOrder()
            assertThat(
                repository.movies(VideoSort.Year, unwatched = true).first().map { it.title },
            ).containsExactly("Moșoi")
            assertThat(
                repository
                    .series(VideoSort.Name, unwatched = false)
                    .first()
                    .single()
                    .userData.unplayedCount,
            ).isEqualTo(5)
            assertThat(repository.movieCount.first()).isEqualTo(2)
            assertThat(repository.seriesCount.first()).isEqualTo(1)
            assertThat(repository.seasons(id("sr1")).first().map { it.id }).containsExactly(id("se1"))
            assertThat(repository.episodes(id("se1")).first().map { it.id }).containsExactly(id("e1"))
            assertThat(repository.series(id("sr1")).first()?.title).isEqualTo("Show")
        }

    @Test
    fun `offline mode lists no videos, since none is downloaded yet`() =
        runTest {
            library()
            preferences.setOfflineMode(true)

            assertThat(repository.movies(VideoSort.Name, unwatched = false).first()).isEmpty()
            assertThat(repository.movieCount.first()).isEqualTo(0)
            assertThat(repository.seriesCount.first()).isEqualTo(0)
        }

    @Test
    fun `refreshing Continue watching writes the server's resume points into the rows`() =
        runTest {
            database.syncDao().stageVideos(listOf(VideoStaging(movie.toEntity())))
            database.syncDao().swap(1, "s", "user-1")
            accountStore.save(StoredAccount("http://nas:8096", server, "Home", "user-1", "Ana", accessToken = "token"))
            source.resumeAnswer =
                MqResult.Success(
                    mapOf(
                        movie.id to UserData(playbackPosition = 20.minutes, playedPercentage = 20.0),
                        id("unknown") to UserData(playbackPosition = 5.minutes),
                    ),
                )

            repository.refreshResume()

            val watching = repository.continueWatching(20).first()
            assertThat(watching.map { it.id }).containsExactly(movie.id)
            assertThat(watching.single().userData.playbackPosition).isEqualTo(20.minutes)
        }
}
