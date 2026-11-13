package io.github.kentprodan.mediatheq.core.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Surface
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvPreviews
import io.github.kentprodan.mediatheq.core.designsystem.tv.PreviewFixtures

/**
 * The album card's states: a title that ellipsizes, no subtitle, the artist placeholder. No artwork: layoutlib loads no
 * image, so the placeholder is what renders; focus is not rendered either (proven by the Home D-pad test).
 */
@PreviewTest
@MqTvPreviews
@Composable
fun TvAlbumCardScreenshot() =
    MediatheqTvTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(48.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                MqTvRowHeader("Recently added albums")
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    MqTvAlbumCard(PreviewFixtures.ALBUM, null, {}, subtitle = PreviewFixtures.ARTIST)
                    MqTvAlbumCard(PreviewFixtures.TRACK, null, {})
                    MqTvAlbumCard(PreviewFixtures.ARTIST, null, {}, placeholder = MqTvIcons.Person)
                }
            }
        }
    }
