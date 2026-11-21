package io.github.kentprodan.mediatheq.tv.navigation

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.TvMenuStyle
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvPreviews
import io.github.kentprodan.mediatheq.core.model.AppMode
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.playback.NowPlayingItem
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel

private val playing =
    PlayerUiModel(item = NowPlayingItem(MediaId(ServerId("s"), "t"), "Strobe", "deadmau5"), isPlaying = true)

@Composable
private fun Shell(
    player: PlayerUiModel,
    style: TvMenuStyle = TvMenuStyle.Modal,
    open: Boolean = false,
    destinations: List<TvDestination> = TvDestination.shownIn(AppMode.Music),
) = MediatheqTvTheme {
    // The frame alone: NavDisplay needs a back dispatcher that previews do not have.
    TvFrame(
        TvDestination.Library,
        player,
        onNavigate = {},
        destinations = destinations,
        menuStyle = style,
        userName = "Ana",
        previewOpen = open,
    ) { TvPlaceholder("Music") }
}

/** Modal, open (#520): the island over the dimmed screen with the labels and the profile. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvShellModalOpenScreenshot() = Shell(playing, open = true)

/** Standard, open (#520): the screen pushed aside, not dimmed. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvShellStandardOpenScreenshot() = Shell(playing, TvMenuStyle.Standard, open = true)

/** The longest menu (#520): music and video while something plays, open. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvShellBothOpenScreenshot() = Shell(playing, open = true, destinations = TvDestination.shownIn(AppMode.Both))

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
