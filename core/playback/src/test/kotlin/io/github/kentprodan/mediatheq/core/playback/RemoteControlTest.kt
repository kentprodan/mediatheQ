package io.github.kentprodan.mediatheq.core.playback

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.RemoteCommand
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.testing.FakeLibraryRepository
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakeVideoRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class RemoteControlTest {
    private val playback = FakePlaybackConnection()
    private val videos = FakeVideoRepository()
    private val library = FakeLibraryRepository()
    private val movie = Video(MediaId(ServerId("s"), "m1"), VideoKind.Movie, "Alien")

    @Test
    fun `a movie sent from another client plays from its position and is announced, tracks play as music`() =
        runTest {
            val remote = RemoteControl(liveSync = null, playback, videos, library, backgroundScope)
            val started = mutableListOf<MediaId>()
            backgroundScope.launch { remote.videoStarted.collect { started += it } }
            videos.movies.value = listOf(movie)
            val track = Track(MediaId(ServerId("s"), "t1"), "Song", emptyList(), 3.minutes)
            library.tracks.value = listOf(track)
            runCurrent()

            remote.apply(RemoteCommand.Play(listOf(movie.id), startPosition = 30.minutes))
            remote.apply(RemoteCommand.Play(listOf(track.id)))
            runCurrent()

            assertThat(playback.videoCalls.single()).isEqualTo(movie to 30.minutes)
            assertThat(started).containsExactly(movie.id)
            assertThat(playback.plays.single().queue).containsExactly(track)
        }

    @Test
    fun `transport commands go to the session`() =
        runTest {
            val remote = RemoteControl(liveSync = null, playback, videos, library, backgroundScope)

            remote.apply(RemoteCommand.Seek(2.minutes))
            remote.apply(RemoteCommand.Next)
            remote.apply(RemoteCommand.PlayPause)

            assertThat(playback.commands).containsAtLeast("seekTo ${2.minutes}", "next", "togglePlayPause").inOrder()
        }
}
