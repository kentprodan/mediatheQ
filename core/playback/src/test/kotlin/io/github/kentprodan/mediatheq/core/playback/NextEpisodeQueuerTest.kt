package io.github.kentprodan.mediatheq.core.playback

import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Season
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.testing.FakeVideoRepository
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@UnstableApi
@RunWith(RobolectricTestRunner::class)
class NextEpisodeQueuerTest {
    private val player: ExoPlayer =
        TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
            .setMediaSourceFactory(FakeMediaSourceFactory())
            .build()
    private val season = Season(MediaId(ServerId("s"), "se1"), MediaId(ServerId("s"), "sr1"), "Season 1", 1)
    private val episodes =
        (1..2).map {
            Video(
                MediaId(ServerId("s"), "e$it"),
                VideoKind.Episode,
                "Episode $it",
                seriesId = season.seriesId,
                seasonId = season.id,
                seasonNumber = 1,
                episodeNumber = it,
            )
        }
    private val videos = FakeVideoRepository().apply { seasonEpisodes.value = mapOf(season.id to episodes) }

    @After
    fun release() = player.release()

    private fun TestScope.queuer(enabled: Boolean = true) =
        NextEpisodeQueuer(player, backgroundScope, videos, { it.toMediaItem(null).withTrackUri()!! }) { enabled }
            .also(player::addListener)

    private fun TestScope.settle() {
        advance(player).untilPendingCommandsAreFullyHandled()
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
    }

    @Test
    fun `an episode becoming the playing item gets its next episode queued behind it`() =
        runTest {
            queuer()

            player.setMediaItem(episodes[0].toMediaItem(null).withTrackUri()!!)
            settle()

            assertThat(player.mediaItemCount).isEqualTo(2)
            assertThat(player.getMediaItemAt(1).mediaId).isEqualTo(episodes[1].id.toString())
        }

    @Test
    fun `a cancelled next episode is not queued again, and nothing is queued with auto-play off`() =
        runTest {
            queuer()
            player.setMediaItem(episodes[0].toMediaItem(null).withTrackUri()!!)
            settle()

            player.removeMediaItem(1)
            settle()
            assertThat(player.mediaItemCount).isEqualTo(1)
        }

    @Test
    fun `with auto-play off nothing is queued`() =
        runTest {
            queuer(enabled = false)

            player.setMediaItem(episodes[0].toMediaItem(null).withTrackUri()!!)
            settle()

            assertThat(player.mediaItemCount).isEqualTo(1)
        }
}
