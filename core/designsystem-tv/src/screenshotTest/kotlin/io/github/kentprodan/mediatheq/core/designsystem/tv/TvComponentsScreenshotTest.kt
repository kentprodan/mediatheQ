package io.github.kentprodan.mediatheq.core.designsystem.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvButton
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvCard
import io.github.kentprodan.mediatheq.core.designsystem.tv.component.MqTvListItem

/**
 * Each `MqTv*` component enabled and disabled. Layoutlib renders no focus (tv-material draws it from the real focus
 * state), so the focused look is proven on Robolectric by the app's focus test and on the Streamer.
 */
@Composable
private fun States(content: @Composable (Boolean) -> Unit) =
    MediatheqTvTheme {
        Surface(Modifier.fillMaxSize()) {
            Row(Modifier.padding(48.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                content(true)
                content(false)
            }
        }
    }

@PreviewTest
@MqTvPreviews
@Composable
fun TvButtonScreenshot() = States { enabled -> MqTvButton("Play", {}, enabled = enabled) }

@PreviewTest
@MqTvPreviews
@Composable
fun TvCardScreenshot() =
    States { _ ->
        MqTvCard(PreviewFixtures.ALBUM, {}, subtitle = PreviewFixtures.ARTIST) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer))
        }
    }

@PreviewTest
@MqTvPreviews
@Composable
fun TvListItemScreenshot() =
    MediatheqTvTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(48.dp).width(480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MqTvListItem(PreviewFixtures.TRACK, {}, supporting = PreviewFixtures.ARTIST)
                MqTvListItem(PreviewFixtures.TRACK, {}, supporting = PreviewFixtures.ARTIST, selected = true)
                MqTvListItem(PreviewFixtures.TRACK, {}, supporting = PreviewFixtures.ARTIST, enabled = false)
            }
        }
    }
