package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.datastore.StoredAccount
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.LyricLine
import io.github.kentprodan.mediatheq.core.model.Lyrics
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakeAudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLyricsSource
import io.github.kentprodan.mediatheq.core.testing.FakeOnlineLyricsSource
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.FakeTokenCipher
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DefaultLyricsRepositoryTest {
    private val source = FakeLyricsSource()
    private val accountStore = AccountStore(InMemoryPreferencesDataStore(), FakeTokenCipher())
    private val session = FakeSessionRepository()
    private val online = FakeOnlineLyricsSource()
    private val audio = FakeAudioPreferencesRepository()
    private val library = FakeLibraryRepository()
    private val repository = DefaultLyricsRepository(source, accountStore, session, online, audio, library)
    private val track = MediaId(ServerId("s"), "t1")

    private suspend fun signIn() =
        accountStore.save(
            StoredAccount("http://nas:8096", ServerId("s"), "Home", "user-1", "Ana", accessToken = "token-1"),
        )

    @Test
    fun `lyrics come from the signed-in server`() =
        runTest {
            signIn()
            val lyrics = Lyrics(listOf(LyricLine("hello")))
            source.lyrics[track] = lyrics

            assertThat(repository.lyrics(track)).isEqualTo(MqResult.Success(lyrics))
        }

    @Test
    fun `a track without lyrics is a success with none`() =
        runTest {
            signIn()

            assertThat(repository.lyrics(track)).isEqualTo(MqResult.Success(null))
        }

    @Test
    fun `signed out, nothing is asked and it is not a session expiry`() =
        runTest {
            assertThat(repository.lyrics(track)).isEqualTo(MqResult.Failure(MqError.Unauthorized))
            assertThat(source.calls).isEqualTo(0)
            assertThat(session.session.value).isEqualTo(SessionState.LoggedOut())
        }

    @Test
    fun `a 401 signs out with session expired`() =
        runTest {
            signIn()
            source.failure = MqError.Unauthorized

            repository.lyrics(track)

            assertThat(session.session.value).isEqualTo(SessionState.LoggedOut(LogoutReason.SessionExpired))
        }

    @Test
    fun `other failures pass through without signing out`() =
        runTest {
            signIn()
            source.failure = MqError.Network

            assertThat(repository.lyrics(track)).isEqualTo(MqResult.Failure(MqError.Network))
            assertThat(session.session.value).isEqualTo(SessionState.LoggedOut())
        }

    @Test
    fun `without server lyrics, LRCLIB answers only while the setting is on, once per track`() =
        runTest {
            signIn()
            library.tracks.value =
                listOf(
                    Track(track, "Strobe", listOf(Artist(MediaId(ServerId("s"), "ar1"), "deadmau5")), 10.minutes)
                        .copy(albumTitle = "For Lack of a Better Name"),
                )
            val found = Lyrics(listOf(LyricLine("la", 1.seconds)))
            online.answer = MqResult.Success(found)

            assertThat(repository.lyrics(track)).isEqualTo(MqResult.Success(null))
            assertThat(online.asked).isEmpty()

            audio.setBehavior(BehaviorSwitch.OnlineLyrics, true)
            assertThat(repository.lyrics(track)).isEqualTo(MqResult.Success(found))
            assertThat(repository.lyrics(track)).isEqualTo(MqResult.Success(found))
            assertThat(online.asked).containsExactly("Strobe|deadmau5|For Lack of a Better Name")
        }

    @Test
    fun `server lyrics win, and an online failure keeps the server's none`() =
        runTest {
            signIn()
            audio.setBehavior(BehaviorSwitch.OnlineLyrics, true)
            library.tracks.value =
                listOf(Track(track, "T", listOf(Artist(MediaId(ServerId("s"), "a"), "A")), 3.minutes))
            online.answer = MqResult.Failure(MqError.Network)

            assertThat(repository.lyrics(track)).isEqualTo(MqResult.Success(null))

            val own = Lyrics(listOf(LyricLine("mine")))
            source.lyrics[track] = own
            assertThat(repository.lyrics(track)).isEqualTo(MqResult.Success(own))
        }

    @Test
    fun `an online none is kept too, so the track is not asked again`() =
        runTest {
            signIn()
            audio.setBehavior(BehaviorSwitch.OnlineLyrics, true)
            library.tracks.value =
                listOf(Track(track, "T", listOf(Artist(MediaId(ServerId("s"), "a"), "A")), 3.minutes))

            repository.lyrics(track)
            repository.lyrics(track)

            assertThat(online.asked).hasSize(1)
        }
}
