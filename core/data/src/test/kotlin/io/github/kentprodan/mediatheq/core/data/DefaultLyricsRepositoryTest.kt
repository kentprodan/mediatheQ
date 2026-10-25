package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.datastore.StoredAccount
import io.github.kentprodan.mediatheq.core.model.LyricLine
import io.github.kentprodan.mediatheq.core.model.Lyrics
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.testing.FakeLyricsSource
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.FakeTokenCipher
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultLyricsRepositoryTest {
    private val source = FakeLyricsSource()
    private val accountStore = AccountStore(InMemoryPreferencesDataStore(), FakeTokenCipher())
    private val session = FakeSessionRepository()
    private val repository = DefaultLyricsRepository(source, accountStore, session)
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
}
