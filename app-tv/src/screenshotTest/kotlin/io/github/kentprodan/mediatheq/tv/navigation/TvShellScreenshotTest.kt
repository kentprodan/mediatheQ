package io.github.kentprodan.mediatheq.tv.navigation

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvPreviews
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.playback.NowPlayingItem
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel

private val playing =
    PlayerUiModel(item = NowPlayingItem(MediaId(ServerId("s"), "t"), "Strobe", "deadmau5"), isPlaying = true)

@Composable
private fun Shell(player: PlayerUiModel) =
    MediatheqTvTheme {
        // The frame alone: NavDisplay needs a back dispatcher that previews do not have.
        TvFrame(TvDestination.Library, player, onNavigate = {}) { TvPlaceholder("Library") }
    }

/** The tab bar (#508), Library selected. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvShellTabsScreenshot() = Shell(PlayerUiModel())

/** With something queued: what plays at the end of the bar. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvShellTabsPlayingScreenshot() = Shell(playing)
