package io.github.kentprodan.mediatheq.core.playback

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.TrackAction
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class TrackTapsTest {
    private val playback = FakePlaybackConnection()
    private val preferences = FakeLibraryPreferencesRepository()
    private val taps = TrackTaps(playback, preferences)
    private val tracks = (1..3).map { Track(MediaId(ServerId("s"), "t$it"), "Track $it", emptyList(), 3.minutes) }

    private suspend fun tapSecond(action: TrackAction) {
        preferences.setNavigation(NavigationPreferences(trackAction = action))
        taps.tap(tracks, 1)
    }

    @Test
    fun `Play makes the list the queue from the tapped track`() =
        runTest {
            tapSecond(TrackAction.Play)

            assertThat(playback.plays).containsExactly(FakePlaybackConnection.PlayCall(tracks, 1, false))
        }

    @Test
    fun `Play only this track queues just that track`() =
        runTest {
            tapSecond(TrackAction.PlayOnlyThis)

            assertThat(playback.plays).containsExactly(FakePlaybackConnection.PlayCall(listOf(tracks[1]), 0, false))
        }

    @Test
    fun `Play next queues the track after the current one`() =
        runTest {
            tapSecond(TrackAction.PlayNext)

            assertThat(playback.playedNext).containsExactly(listOf(tracks[1]))
            assertThat(playback.plays).isEmpty()
        }

    @Test
    fun `Add to queue queues the track at the end`() =
        runTest {
            tapSecond(TrackAction.AddToQueue)

            assertThat(playback.addedToQueue).containsExactly(listOf(tracks[1]))
            assertThat(playback.plays).isEmpty()
        }

    @Test
    fun `an index outside the list does nothing`() =
        runTest {
            taps.tap(tracks, 5)

            assertThat(playback.plays).isEmpty()
        }

    @Test
    fun `a tap in a playlist plays from that playlist`() =
        runTest {
            val playlist = MediaId(ServerId("s"), "p1")
            preferences.setNavigation(NavigationPreferences(trackAction = TrackAction.Play))

            taps.tap(tracks, 1, playlist)

            assertThat(playback.plays.single().playlistId).isEqualTo(playlist)
        }

    @Test
    fun `a page's own action wins over the default one`() =
        runTest {
            preferences.setNavigation(NavigationPreferences(trackAction = TrackAction.Play))

            taps.tap(tracks, 1, action = TrackAction.AddToQueue)

            assertThat(playback.addedToQueue).containsExactly(listOf(tracks[1]))
            assertThat(playback.plays).isEmpty()
        }
}
