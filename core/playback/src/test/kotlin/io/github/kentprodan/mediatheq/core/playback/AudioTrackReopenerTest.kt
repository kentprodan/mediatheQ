package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioTrackReopenerTest {
    private val player: ExoPlayer =
        TestExoPlayerBuilder(ApplicationProvider.getApplicationContext<Context>())
            .setMediaSourceFactory(FakeMediaSourceFactory())
            .build()

    // The timer, run by hand.
    private var timer: (() -> Unit)? = null
    private val reopener =
        AudioTrackReopener(player, { _, block ->
            timer = block
            ({ timer = null })
        })

    private val audioOff get() = C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes

    @After
    fun release() = player.release()

    @Test
    fun `audio goes off until the old track is released, without a pause, and the offload preference stays`() {
        player.playWhenReady = true
        player.applyOffload(false)

        reopener.reopen()

        assertThat(audioOff).isTrue()
        assertThat(player.playWhenReady).isTrue()
        reopener.onTracksReleased()
        assertThat(audioOff).isFalse()
        assertThat(timer).isNull()
        assertThat(player.trackSelectionParameters.audioOffloadPreferences.audioOffloadMode)
            .isEqualTo(
                androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED,
            )
    }

    @Test
    fun `a track that is never released turns audio on again after the timeout`() {
        reopener.reopen()

        timer!!.invoke()

        assertThat(audioOff).isFalse()
        assertThat(reopener.reopening).isFalse()
    }

    @Test
    fun `a second request while reopening changes nothing, and a release outside a reopen does nothing`() {
        reopener.onTracksReleased()
        assertThat(audioOff).isFalse()

        reopener.reopen()
        reopener.reopen()
        reopener.onTracksReleased()

        assertThat(audioOff).isFalse()
        assertThat(reopener.reopening).isFalse()
    }
}
