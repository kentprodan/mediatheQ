package io.github.kentprodan.mediatheq.tv.navigation

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.playback.NowPlayingItem
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.testing.FakeAppLog
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import org.junit.Test

class TvShellViewModelTest {
    private val playback = FakePlaybackConnection()
    private val log = FakeAppLog()
    private val viewModel = TvShellViewModel(playback, log)

    @Test
    fun `the strip follows the playback state`() {
        val playing = PlayerUiModel(NowPlayingItem(MediaId(ServerId("s"), "t"), "Strobe", "deadmau5"), isPlaying = true)

        playback.state.value = playing

        assertThat(viewModel.player.value).isEqualTo(playing)
    }

    @Test
    fun `a destination without a TV screen is logged as a warning`() {
        viewModel.unknownDestination(AlbumKey(MediaId(ServerId("s"), "a")))

        assertThat(log.lines.single()).startsWith("W TvShell: No TV screen for")
    }
}
