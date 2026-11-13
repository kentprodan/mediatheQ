package io.github.kentprodan.mediatheq.tv.navigation

import androidx.compose.runtime.Composable
import androidx.tv.material3.DrawerValue
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
private fun Shell(
    player: PlayerUiModel,
    drawer: DrawerValue,
) = MediatheqTvTheme {
    // The frame alone: NavDisplay needs a back dispatcher that previews do not have.
    TvFrame(TvDestination.Library, player, onNavigate = {}, initialDrawer = drawer) { TvPlaceholder("Library") }
}

@PreviewTest
@MqTvPreviews
@Composable
fun TvShellCollapsedScreenshot() = Shell(PlayerUiModel(), DrawerValue.Closed)

@PreviewTest
@MqTvPreviews
@Composable
fun TvShellExpandedScreenshot() = Shell(PlayerUiModel(), DrawerValue.Open)

/** With something queued: the strip in the drawer's footer. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvShellStripCollapsedScreenshot() = Shell(playing, DrawerValue.Closed)

@PreviewTest
@MqTvPreviews
@Composable
fun TvShellStripExpandedScreenshot() = Shell(playing, DrawerValue.Open)
