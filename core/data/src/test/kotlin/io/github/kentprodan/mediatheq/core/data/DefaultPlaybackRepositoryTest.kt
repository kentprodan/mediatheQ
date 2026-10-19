package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.datastore.StoredAccount
import io.github.kentprodan.mediatheq.core.model.DeviceProfile
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.TranscodingTarget
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackSource
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.FakeTokenCipher
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultPlaybackRepositoryTest {
    private val source = FakePlaybackSource()
    private val accountStore = AccountStore(InMemoryPreferencesDataStore(), FakeTokenCipher())
    private val session = FakeSessionRepository()
    private val repository = DefaultPlaybackRepository(source, accountStore, session)
    private val itemId = MediaId(ServerId("s"), "t1")
    private val profile =
        DeviceProfile(listOf("flac"), listOf("flac"), TranscodingTarget("ogg", "opus", 256_000, 2), null)

    private suspend fun signIn(token: String = "token-1") =
        accountStore.save(StoredAccount("http://nas:8096", ServerId("s"), "Home", "user-1", "Ana", accessToken = token))

    @Test
    fun `a stream is asked of the signed-in server`() =
        runTest {
            signIn()

            assertThat(
                repository.stream(itemId, profile),
            ).isEqualTo(MqResult.Success(FakePlaybackSource.fakeStream(itemId)))
            assertThat(source.profiles).containsExactly(profile)
        }

    @Test
    fun `headers follow the newest token`() =
        runTest {
            signIn("token-1")
            assertThat(repository.streamHeaders()).containsEntry("Authorization", "token=token-1")

            signIn("token-2")
            assertThat(repository.streamHeaders()).containsEntry("Authorization", "token=token-2")
        }

    @Test
    fun `signed out, nothing is asked and it is not a session expiry`() =
        runTest {
            assertThat(repository.stream(itemId, profile)).isEqualTo(MqResult.Failure(MqError.Unauthorized))
            assertThat(repository.streamHeaders()).isEmpty()
            assertThat(source.profiles).isEmpty()
            assertThat(session.session.value).isEqualTo(SessionState.LoggedOut())
        }

    @Test
    fun `a 401 signs out with session expired`() =
        runTest {
            signIn()
            source.failure = MqResult.Failure(MqError.Unauthorized)

            repository.reportProgress(
                io.github.kentprodan.mediatheq.core.model.PlaybackReport(
                    FakePlaybackSource.fakeStream(itemId),
                    kotlin.time.Duration.ZERO,
                    false,
                ),
            )

            assertThat(session.session.value).isEqualTo(SessionState.LoggedOut(LogoutReason.SessionExpired))
        }

    @Test
    fun `right after sign-out the final report still goes to the last server, without a session expiry`() =
        runTest {
            signIn()
            repository.stream(itemId, profile)
            accountStore.clear()
            source.failure = MqResult.Failure(MqError.Unauthorized) // the token was revoked by the logout

            repository.reportStopped(
                io.github.kentprodan.mediatheq.core.model.PlaybackReport(
                    FakePlaybackSource.fakeStream(itemId),
                    kotlin.time.Duration.ZERO,
                    false,
                ),
                failed = false,
            )

            assertThat(source.reports).containsExactly("stopped t1")
            assertThat(session.session.value).isEqualTo(SessionState.LoggedOut())
        }
}
