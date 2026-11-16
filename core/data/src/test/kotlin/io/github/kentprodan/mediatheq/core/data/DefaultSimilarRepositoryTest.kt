package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.datastore.StoredAccount
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibrarySource
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.FakeTokenCipher
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class DefaultSimilarRepositoryTest {
    private val source = FakeLibrarySource()
    private val accountStore = AccountStore(InMemoryPreferencesDataStore(), FakeTokenCipher())
    private val session = FakeSessionRepository()
    private val library = FakeLibraryRepository()
    private val repository = DefaultSimilarRepository(source, accountStore, session, library)

    private fun id(item: String) = MediaId(ServerId("s"), item)

    private fun track(item: String) = Track(id(item), item.uppercase(), emptyList(), 3.minutes)

    private suspend fun signIn() =
        accountStore.save(
            StoredAccount("http://nas:8096", ServerId("s"), "Home", "user-1", "Ana", accessToken = "token-1"),
        )

    @Test
    fun `the mix keeps the server's order and only the library's tracks, a track seed first`() =
        runTest {
            signIn()
            library.tracks.value = listOf(track("a"), track("b"), track("seed"))
            source.mixAnswer = MqResult.Success(listOf(id("b"), id("gone"), id("seed"), id("a")))

            val mix = (repository.instantMix(id("seed")) as MqResult.Success).value

            assertThat(mix.map { it.id.itemId }).containsExactly("seed", "b", "a").inOrder()
            assertThat(source.calls).contains("mix:seed@100")
        }

    @Test
    fun `an album seed is not a track, so the mix is only what the server sent`() =
        runTest {
            signIn()
            library.tracks.value = listOf(track("a"))
            source.mixAnswer = MqResult.Success(listOf(id("a")))

            val mix = (repository.instantMix(id("album")) as MqResult.Success).value

            assertThat(mix.map { it.id.itemId }).containsExactly("a")
        }

    @Test
    fun `a failure passes through and a 401 signs out`() =
        runTest {
            signIn()
            source.mixAnswer = MqResult.Failure(MqError.Network)
            assertThat(repository.instantMix(id("a"))).isEqualTo(MqResult.Failure(MqError.Network))

            source.mixAnswer = MqResult.Failure(MqError.Unauthorized)
            repository.instantMix(id("a"))

            assertThat(session.session.value)
                .isEqualTo(SessionState.LoggedOut(LogoutReason.SessionExpired))
        }

    @Test
    fun `signed out, nothing is asked`() =
        runTest {
            assertThat(repository.instantMix(id("a"))).isEqualTo(MqResult.Failure(MqError.Unauthorized))
            assertThat(source.calls).isEmpty()
        }
}
