package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.LyricLine
import io.github.kentprodan.mediatheq.core.model.Lyrics
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BluetoothLyricsTest {
    private val player: ExoPlayer =
        TestExoPlayerBuilder(ApplicationProvider.getApplicationContext<Context>())
            .setMediaSourceFactory(FakeMediaSourceFactory())
            .build()
    private val synced =
        Lyrics(
            listOf(
                LyricLine("First", 1.seconds),
                LyricLine("", 2.seconds),
                LyricLine("Third", 3.seconds),
                LyricLine("Fourth", 3.2.seconds),
            ),
        )
    private val plain = Lyrics(listOf(LyricLine("No times"), LyricLine("here")))
    private var enabled = true

    // What the server answers per track id; a missing id has no lyrics. t3 answers when the test says so.
    private val answers = mutableMapOf<String, Lyrics?>("t1" to synced, "t2" to plain)
    private val slow = CompletableDeferred<Lyrics?>()
    private val loads = mutableListOf<String>()
    private val titles = mutableListOf<String?>()

    // The clock and timers, moved by hand.
    private var time = 0L
    private val timers = mutableListOf<Pair<Long, () -> Unit>>()
    private val lyrics =
        BluetoothLyrics(
            player,
            { enabled },
            load = { id ->
                loads += id.itemId
                if (id.itemId == "t3") slow.await() else answers[id.itemId]
            },
            scope = CoroutineScope(Dispatchers.Unconfined),
            schedule = { delay, block ->
                val timer = (time + delay) to block
                timers += timer
                ({ timers.remove(timer) })
            },
            now = { time },
        ) { titles += it }

    @After
    fun release() {
        lyrics.release()
        player.release()
    }

    private fun item(id: String) =
        Track(MediaId(ServerId("s"), id), id, emptyList(), 3.minutes).toMediaItem(null).withTrackUri()!!

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

    /** A paused queue of t1 (synced), t2 (plain), t3 (slow to load), t4 (no lyrics) with the listener on. */
    private fun start() {
        player.addListener(lyrics)
        player.setMediaItems(listOf(item("t1"), item("t2"), item("t3"), item("t4")))
        player.prepare()
        idle()
    }

    private fun idle() = advance(player).untilPendingCommandsAreFullyHandled()

    private fun seek(ms: Long) {
        player.seekTo(ms)
        idle()
    }

    @Test
    fun `the line at a position, or none before the first line, on an empty line and for plain lyrics`() {
        assertThat(lyricsTitle(synced, 500.milliseconds)).isNull()
        assertThat(lyricsTitle(synced, 1.seconds)).isEqualTo("First")
        assertThat(lyricsTitle(synced, 1.9.seconds)).isEqualTo("First")
        assertThat(lyricsTitle(synced, 2.5.seconds)).isNull()
        assertThat(lyricsTitle(synced, 2.minutes)).isEqualTo("Fourth")
        assertThat(lyricsTitle(plain, 1.minutes)).isNull()
    }

    @Test
    fun `the next look is at the next line, never sooner than half a second after the last change`() {
        assertThat(nextLyricsUpdateDelay(synced, 400.milliseconds, 10.seconds)).isEqualTo(600.milliseconds)
        assertThat(nextLyricsUpdateDelay(synced, 3.seconds, 0.milliseconds)).isEqualTo(500.milliseconds)
        assertThat(nextLyricsUpdateDelay(synced, 3.seconds, 400.milliseconds)).isEqualTo(200.milliseconds)
        assertThat(nextLyricsUpdateDelay(synced, 4.seconds, 10.seconds)).isNull()
        assertThat(nextLyricsUpdateDelay(plain, 1.seconds, 10.seconds)).isNull()
    }

    @Test
    fun `the title follows the line at the position, also backwards, and an empty line gives the real title`() {
        start()
        assertThat(titles).isEmpty()

        seek(1_200)
        pass(1_000)
        seek(3_500)
        pass(1_000)
        seek(2_200)
        pass(1_000)
        seek(1_100)

        assertThat(titles).containsExactly("First", "Fourth", null, "First").inOrder()
    }

    @Test
    fun `a seek inside the same line changes nothing`() {
        start()
        seek(1_200)
        pass(1_000)
        seek(1_500)

        assertThat(titles).containsExactly("First")
    }

    @Test
    fun `two changes never come within half a second`() {
        start()
        seek(1_200)
        pass(100)
        seek(3_500)
        assertThat(titles).containsExactly("First")

        pass(400)
        assertThat(titles).containsExactly("First", "Fourth").inOrder()
    }

    @Test
    fun `while playing the next look is scheduled, while paused it is not`() {
        start()
        seek(1_200)
        assertThat(timers).isEmpty()

        player.play()
        advance(player).untilPendingCommandsAreFullyHandled()
        assertThat(timers).hasSize(1)
    }

    @Test
    fun `a track change restores the real title, and plain or missing lyrics never set one`() {
        start()
        seek(1_200)
        pass(1_000)

        player.seekToNextMediaItem()
        idle()
        seek(4_000)
        player.seekTo(2, 4_000)
        idle()
        player.seekTo(3, 4_000)
        idle()

        assertThat(titles).containsExactly("First", null).inOrder()
        assertThat(loads).containsExactly("t1", "t2", "t3", "t4").inOrder()
    }

    @Test
    fun `lyrics that arrive after a fast skip are dropped`() {
        start()
        player.seekTo(2, 1_200)
        idle()
        player.seekTo(3, 1_200)
        idle()

        slow.complete(synced)

        assertThat(titles).isEmpty()
    }

    @Test
    fun `turning the setting off restores the real title and on loads again, once per track`() {
        start()
        seek(1_200)
        pass(1_000)

        enabled = false
        lyrics.onSettingChanged()
        seek(3_500)
        pass(1_000)
        assertThat(titles).containsExactly("First", null).inOrder()

        enabled = true
        lyrics.onSettingChanged()
        assertThat(titles).containsExactly("First", null, "Fourth").inOrder()
        assertThat(loads).containsExactly("t1", "t1").inOrder()
    }

    @Test
    fun `with the setting off nothing is loaded`() {
        enabled = false
        start()
        seek(1_200)

        assertThat(loads).isEmpty()
        assertThat(titles).isEmpty()
    }
}
