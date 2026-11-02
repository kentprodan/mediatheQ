package io.github.kentprodan.mediatheq.core.playback

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.MediaListPreferences
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.data.SwipeAction
import io.github.kentprodan.mediatheq.core.data.TopButton
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakeUserDataRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class TrackCommandsTest {
    private val playback = FakePlaybackConnection()
    private val userData = FakeUserDataRepository()
    private val downloads = FakeDownloadRepository()
    private val preferences = FakeLibraryPreferencesRepository()
    private val commands = TrackCommands(playback, userData, downloads, preferences)

    private fun track(
        value: String,
        favorite: Boolean = false,
    ) = Track(MediaId(ServerId("s"), value), value, emptyList(), 3.minutes, userData = UserData(isFavorite = favorite))

    private val tracks = listOf(track("t1"), track("t2"))

    @Test
    fun `top buttons play, shuffle, play next and add the whole list`() {
        commands.run(TopButton.Play, tracks)
        commands.run(TopButton.Shuffle, tracks)
        commands.run(TopButton.PlayNext, tracks)
        commands.run(TopButton.AddToQueue, tracks)

        assertThat(playback.plays)
            .containsExactly(
                FakePlaybackConnection.PlayCall(tracks, 0, false),
                FakePlaybackConnection.PlayCall(tracks, 0, true),
            ).inOrder()
        assertThat(playback.playedNext).containsExactly(tracks)
        assertThat(playback.addedToQueue).containsExactly(tracks)
    }

    @Test
    fun `an empty list does nothing`() {
        TopButton.entries.forEach { commands.run(it, emptyList()) }

        assertThat(playback.plays).isEmpty()
        assertThat(playback.playedNext).isEmpty()
    }

    @Test
    fun `favorite makes all favorites, and removes them when all already are`() =
        runTest {
            commands.toggleFavorite(listOf(track("t1", favorite = true), track("t2")))
            commands.toggleFavorite(listOf(track("t1", favorite = true), track("t2", favorite = true)))

            assertThat(userData.calls)
                .containsExactly("favorite:t2:true", "favorite:t1:false", "favorite:t2:false")
                .inOrder()
        }

    @Test
    fun `each swipe action runs its command or asks the screen`() =
        runTest {
            val track = track("t1")

            suspend fun swipe(action: SwipeAction): SwipeFollowUp? {
                preferences.setMediaLists(MediaListPreferences(swipeRight = action))
                return commands.swipe(track, right = true)
            }

            assertThat(swipe(SwipeAction.Disabled)).isNull()
            assertThat(swipe(SwipeAction.AddToQueue)).isNull()
            assertThat(swipe(SwipeAction.PlayNext)).isNull()
            assertThat(swipe(SwipeAction.Download)).isNull()
            assertThat(swipe(SwipeAction.ToggleFavorite)).isNull()
            assertThat(swipe(SwipeAction.AddToPlaylist)).isEqualTo(SwipeFollowUp.AddToPlaylist)
            assertThat(swipe(SwipeAction.Rate)).isEqualTo(SwipeFollowUp.Rate)

            assertThat(playback.addedToQueue).containsExactly(listOf(track))
            assertThat(playback.playedNext).containsExactly(listOf(track))
            assertThat(downloads.downloads.first().map { it.track.id }).containsExactly(track.id)
            assertThat(userData.calls).containsExactly("favorite:t1:true")
        }

    @Test
    fun `the left side has its own action`() =
        runTest {
            preferences.setMediaLists(MediaListPreferences(swipeRight = SwipeAction.Disabled))

            commands.swipe(track("t1"), right = false)

            assertThat(playback.playedNext).hasSize(1)
            assertThat(playback.addedToQueue).isEmpty()
        }

    @Test
    fun `rate does nothing while ratings are off`() =
        runTest {
            preferences.setMediaLists(MediaListPreferences(swipeRight = SwipeAction.Rate))
            preferences.setRatingMode(RatingMode.Off)

            assertThat(commands.swipe(track("t1"), right = true)).isNull()
        }
}
