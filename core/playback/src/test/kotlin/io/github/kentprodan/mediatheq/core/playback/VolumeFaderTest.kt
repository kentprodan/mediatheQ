package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.TransitionsPreferences
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VolumeFaderTest {
    private val player: ExoPlayer =
        TestExoPlayerBuilder(ApplicationProvider.getApplicationContext<Context>())
            .setMediaSourceFactory(FakeMediaSourceFactory())
            .build()
    private var settings = TransitionsPreferences(fadeInMs = 400, fadeOutMs = 200)
    private var fixedVolume = false

    // The fader's clock and timer, moved by hand.
    private var time = 0L
    private val timers = mutableListOf<Pair<Long, () -> Unit>>()
    private val changes = mutableListOf<Float>()
    private lateinit var fader: VolumeFader
    private var actions = 0

    @After
    fun release() = player.release()

    private fun pass(ms: Long) {
        val end = time + ms
        while (true) {
            val next = timers.filter { it.first <= end }.minByOrNull { it.first } ?: break
            timers.remove(next)
            time = next.first
            next.second()
        }
        time = end
    }

    /** A playing (or paused) track with the fader listening. */
    private fun start(playing: Boolean = true) {
        fader =
            VolumeFader(
                player,
                { settings },
                { fixedVolume },
                schedule = { delay, block ->
                    val timer = (time + delay) to block
                    timers += timer
                    ({ timers.remove(timer) })
                },
                now = { time },
            ) { changes += fader.factor }
        player.addListener(fader)
        player.setMediaItem(
            Track(MediaId(ServerId("s"), "t1"), "T1", emptyList(), 3.minutes).toMediaItem(null).withTrackUri()!!,
        )
        player.prepare()
        if (playing) play()
    }

    private fun play() {
        player.play()
        advance(player).untilState(Player.STATE_READY)
        advance(player).untilPendingCommandsAreFullyHandled()
    }

    @Test
    fun `fade progress is linear and a fade of no length is done at once`() {
        assertThat(fadeProgress(0, 400)).isEqualTo(0f)
        assertThat(fadeProgress(100, 400)).isEqualTo(0.25f)
        assertThat(fadeProgress(400, 400)).isEqualTo(1f)
        assertThat(fadeProgress(900, 400)).isEqualTo(1f)
        assertThat(fadeProgress(0, 0)).isEqualTo(1f)
    }

    @Test
    fun `play starts silent and rises to full volume over the fade in`() {
        start()
        assertThat(fader.factor).isEqualTo(0f)

        pass(200)
        assertThat(fader.factor).isWithin(0.05f).of(0.5f)
        pass(200)
        assertThat(fader.factor).isEqualTo(1f)
        assertThat(changes).isInOrder()
    }

    @Test
    fun `a seek while playing fades in again, a seek while paused does not`() {
        start()
        pass(400)

        player.seekTo(60_000)
        advance(player).untilPendingCommandsAreFullyHandled()
        assertThat(fader.factor).isLessThan(1f)
        pass(400)
        assertThat(fader.factor).isEqualTo(1f)

        player.pause()
        player.seekTo(30_000)
        advance(player).untilPendingCommandsAreFullyHandled()
        assertThat(fader.factor).isEqualTo(1f)
    }

    @Test
    fun `with fade in off play keeps full volume`() {
        settings = settings.copy(fadeInMs = 0)
        start()
        assertThat(fader.factor).isEqualTo(1f)
        assertThat(changes).isEmpty()
    }

    @Test
    fun `fade out lowers the volume, then runs the pause and restores the factor`() {
        start()
        pass(400)

        fader.fadeOutThen { actions++ }
        pass(100)
        assertThat(fader.factor).isWithin(0.05f).of(0.5f)
        assertThat(actions).isEqualTo(0)
        // The last volume step lands on the first tick after the fade's length.
        pass(120)
        assertThat(actions).isEqualTo(1)
        assertThat(fader.factor).isEqualTo(1f)
    }

    @Test
    fun `a second pause or a stop during the fade out cuts at once and both run`() {
        start()
        pass(400)
        fader.fadeOutThen { actions++ }
        pass(50)

        fader.fadeOutThen { actions += 10 }
        assertThat(actions).isEqualTo(11)
        assertThat(fader.factor).isEqualTo(1f)
        pass(500)
        assertThat(actions).isEqualTo(11)
    }

    @Test
    fun `a volume that becomes fixed while fading in is full at once`() {
        start()
        assertThat(fader.factor).isEqualTo(0f)

        fixedVolume = true
        assertThat(fader.factor).isEqualTo(1f)
    }

    @Test
    fun `play during the fade out cancels the pause and brings the volume back`() {
        start()
        pass(400)
        fader.fadeOutThen { actions++ }
        pass(100)

        fader.cancelFadeOut()
        pass(500)
        assertThat(actions).isEqualTo(0)
        assertThat(fader.factor).isEqualTo(1f)
    }

    @Test
    fun `the action runs at once when fade out is off, nothing plays or the volume is fixed`() {
        start(playing = false)
        fader.fadeOutThen { actions++ }
        assertThat(actions).isEqualTo(1)

        play()
        pass(400)
        settings = settings.copy(fadeOutMs = 0)
        fader.fadeOutThen { actions++ }
        assertThat(actions).isEqualTo(2)

        settings = settings.copy(fadeOutMs = 200)
        fixedVolume = true
        fader.fadeOutThen { actions++ }
        assertThat(actions).isEqualTo(3)
    }

    @Test
    fun `a fixed volume (bit-perfect) never fades in`() {
        fixedVolume = true
        start()
        assertThat(fader.factor).isEqualTo(1f)
    }

    @Test
    fun `finish ends a fade out now and release drops it`() {
        start()
        pass(400)
        fader.fadeOutThen { actions++ }
        fader.finish()
        assertThat(actions).isEqualTo(1)

        fader.fadeOutThen { actions++ }
        fader.release()
        pass(500)
        assertThat(actions).isEqualTo(1)
    }
}
