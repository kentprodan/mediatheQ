package io.github.kentprodan.mediatheq.feature.album

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.data.AlbumButton
import io.github.kentprodan.mediatheq.core.data.AlbumHeaderBackground
import io.github.kentprodan.mediatheq.core.data.AlbumPagePreferences
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.AudioFormat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.RecordLabel
import io.github.kentprodan.mediatheq.core.model.ResumePoint
import io.github.kentprodan.mediatheq.core.model.SpatialFormat
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.ui.preview.PreviewData
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val album = PreviewData.album.copy(artwork = PreviewData.artwork, year = 2024)
private val tracks =
    List(5) { i ->
        val base = if (i == 2) PreviewData.longTrack else PreviewData.track
        base.copy(id = MediaId(base.id.serverId, "t$i"), trackNumber = i + 1, discNumber = 1)
    }
private val hiRes = AudioFormat("flac", sampleRateHz = 96_000, bitDepth = 24, bitrateKbps = 2_400)

@Composable
private fun Album(state: AlbumUiState) = MediatheqTheme { AlbumScreen(album.id, state, onAction = {}, onBack = {}) }

private fun content(
    page: AlbumPagePreferences = AlbumPagePreferences(),
    discs: List<Disc> = listOf(Disc(1, tracks)),
) = AlbumUiState.Content(album, discs, page = page)

/** The default header (#132): the cover edge to edge fading into the page; at fontScale 2.0 the buttons wrap. */
@PreviewTest
@MqPreviews
@Composable
fun AlbumCoverImageScreenshot() =
    Album(
        content(
            discs =
                listOf(
                    Disc(
                        1,
                        tracks.map {
                            it.copy(audioFormat = hiRes)
                        },
                    ),
                ),
        ).copy(resume = ResumePoint(tracks[1].id, 30.seconds)),
    )

/** Header background "Cover color": a gradient from the cover's color (the fixture has none: the tonal fallback). */
@PreviewTest
@MqPreviews
@Composable
fun AlbumCoverColorScreenshot() =
    Album(content(AlbumPagePreferences(headerBackground = AlbumHeaderBackground.CoverColor)))

/** Header background "None", stars on, every hideable button hidden (they wait in More). */
@PreviewTest
@MqPreviews
@Composable
fun AlbumPlainHeaderScreenshot() =
    Album(
        content(
            AlbumPagePreferences(
                headerBackground = AlbumHeaderBackground.None,
                showRatingBar = true,
                hiddenButtons = AlbumButton.entries.toSet(),
            ),
        ).copy(album = album.copy(userData = UserData(isFavorite = true, rating = 6.0))),
    )

/** A title with its edition as the version line, and a Dolby Atmos album. */
@PreviewTest
@MqPreviews
@Composable
fun AlbumVersionAtmosScreenshot() =
    Album(
        content(
            AlbumPagePreferences(headerBackground = AlbumHeaderBackground.None),
            listOf(
                Disc(
                    1,
                    tracks.map {
                        it.copy(
                            audioFormat =
                                AudioFormat(
                                    "eac3",
                                    bitrateKbps = 768,
                                    spatialFormat = SpatialFormat.DolbyAtmos,
                                ),
                        )
                    },
                ),
            ),
        ).copy(album = album.copy(title = "Northern Lights (25th Anniversary Super Deluxe Edition)")),
    )

/** A lossy album shows its codec and bitrate; long titles wrap. */
@PreviewTest
@MqPreviews
@Composable
fun AlbumLongTitlesScreenshot() =
    Album(
        AlbumUiState.Content(
            PreviewData.longAlbum.copy(year = 1999),
            listOf(Disc(1, listOf(PreviewData.longTrack.copy(audioFormat = AudioFormat("mp3", bitrateKbps = 320))))),
            page = AlbumPagePreferences(headerBackground = AlbumHeaderBackground.None, multilineTrackTitles = true),
        ),
    )

/** The tracks under a plain header: the second one playing, thumbnails on, numbers off, artists always shown. */
@PreviewTest
@MqPreviews
@Composable
fun AlbumTrackOptionsScreenshot() =
    Album(
        content(
            AlbumPagePreferences(
                headerBackground = AlbumHeaderBackground.None,
                showTrackThumbnails = true,
                alwaysShowTrackArtists = true,
            ),
        ).copy(currentTrackId = tracks[1].id),
    )

@PreviewTest
@MqPreviews
@Composable
fun AlbumMultiDiscScreenshot() =
    Album(
        content(
            AlbumPagePreferences(headerBackground = AlbumHeaderBackground.None),
            listOf(
                Disc(1, tracks.take(2)),
                Disc(
                    2,
                    tracks.drop(2).map { it.copy(id = MediaId(it.id.serverId, "d2-${it.id.itemId}"), discNumber = 2) },
                ),
            ),
        ),
    )

/** More from the artist and About, drawn in place (they sit below the fold of the page). */
@PreviewTest
@MqPreviews
@Composable
fun AlbumSectionsScreenshot() =
    MediatheqTheme {
        val more = List(3) { i -> album.copy(id = MediaId(album.id.serverId, "more-$i"), title = "Album ${i + 1}") }
        val state =
            content(
                discs =
                    listOf(
                        Disc(
                            1,
                            tracks.map {
                                it.copy(
                                    audioFormat = hiRes,
                                    sizeBytes = 120_000_000,
                                    userData = UserData(playCount = 3),
                                )
                            },
                        ),
                    ),
            ).copy(
                album =
                    album.copy(
                        overview =
                            "Northern Lights is the second album of the band, recorded over one winter in a cabin " +
                                "by the sea. It mixes folk songs with long electronic pieces and field recordings.",
                        releaseDate = Instant.parse("2024-03-15T00:00:00Z"),
                        dateAdded = Instant.parse("2026-07-11T13:55:28Z"),
                    ),
                genres = listOf("Electronic", "Folk"),
                labels = listOf(RecordLabel(MediaId(album.id.serverId, "l1"), "Electrecord")),
                moreAlbums = more,
            )
        Surface {
            Column {
                MoreFromArtist(PreviewData.artist.name, more) {}
                AboutSection(state)
            }
        }
    }

@PreviewTest
@MqPreviews
@Composable
fun AlbumLoadingScreenshot() = Album(AlbumUiState.Loading)

@PreviewTest
@MqPreviews
@Composable
fun AlbumNotFoundScreenshot() = Album(AlbumUiState.NotFound)
