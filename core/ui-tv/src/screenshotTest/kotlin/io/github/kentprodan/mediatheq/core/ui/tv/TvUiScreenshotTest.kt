package io.github.kentprodan.mediatheq.core.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Surface
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.common.QualityKind
import io.github.kentprodan.mediatheq.core.common.QualityLabel
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvIcons
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvPreviews
import io.github.kentprodan.mediatheq.core.designsystem.tv.PreviewFixtures
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.AudioFormat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.SpatialFormat
import io.github.kentprodan.mediatheq.core.model.Track
import kotlin.time.Duration.Companion.seconds

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

/** Track rows: plain, playing, a Dolby Atmos file; and the quality badges. Focus is not rendered by layoutlib. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvTrackRowScreenshot() =
    MediatheqTvTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(48.dp).width(640.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val artist = listOf(Artist(MediaId(ServerId("s"), "a"), PreviewFixtures.ARTIST))

                fun track(
                    n: Int,
                    format: AudioFormat? = null,
                ) = Track(
                    MediaId(ServerId("s"), "t$n"),
                    PreviewFixtures.TRACK,
                    artist,
                    634.seconds,
                    trackNumber = n,
                    audioFormat = format,
                )
                MqTvTrackRow(track(1), {})
                MqTvTrackRow(track(2), {}, isPlaying = true)
                MqTvTrackRow(
                    track(3, AudioFormat("eac3", 48_000, channels = 8, spatialFormat = SpatialFormat.DolbyAtmos)),
                    {},
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MqTvFormatBadge(QualityLabel(QualityKind.DolbyAtmos, ""))
                    MqTvFormatBadge(QualityLabel(QualityKind.HiResLossless, "FLAC · 24/96"))
                    MqTvFormatBadge(QualityLabel(QualityKind.Lossless, "FLAC · 16/44.1"))
                    MqTvFormatBadge(QualityLabel(QualityKind.HighQuality, "MP3 · 320 kb/s"))
                }
            }
        }
    }
