package io.github.kentprodan.mediatheq.core.data

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class HomeLibraryFlowTest {
    private val library = FakeLibraryRepository()
    private val track = Track(MediaId(ServerId("s"), "t1"), "T", emptyList(), 3.minutes)

    @Test
    fun `online the shown tracks are all the tracks, offline both lists are read`() =
        runTest {
            library.tracks.value = listOf(track)
            library.homeLibrary().test {
                val online = awaitItem()
                assertThat(online.tracks).containsExactly(track)
                assertThat(online.allTracks).isSameInstanceAs(online.tracks)

                library.offline.value = true
                val offline = awaitItem()
                assertThat(offline.tracks).containsExactly(track)
                assertThat(offline.allTracks).containsExactly(track)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
