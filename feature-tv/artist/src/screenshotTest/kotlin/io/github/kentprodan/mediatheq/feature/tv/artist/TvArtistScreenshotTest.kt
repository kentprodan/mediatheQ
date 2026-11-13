package io.github.kentprodan.mediatheq.feature.tv.artist

import androidx.compose.runtime.Composable
import androidx.tv.material3.Surface
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvPreviews
import io.github.kentprodan.mediatheq.core.designsystem.tv.PreviewFixtures
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.ImageType
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import kotlin.time.Duration.Companion.seconds

private fun id(value: String) = MediaId(ServerId("s"), value)

private const val BIO =
    "Joel Zimmerman, known as deadmau5, is a Canadian electronic music producer from Niagara Falls, Ontario. He " +
        "produces mainly progressive house music, as well as electro house and techno, and is known for the mouse " +
        "helmet he wears on stage."

private val albums =
    (1..6).map {
        Album(
            id("al$it"),
            if (it ==
                1
            ) {
                PreviewFixtures.ALBUM
            } else {
                "Album $it"
            },
            emptyList(),
            year = 2004 + it,
        )
    }
private val tracks =
    (1..3).map {
        Track(
            id("t$it"),
            if (it ==
                1
            ) {
                PreviewFixtures.TRACK
            } else {
                "Track $it"
            },
            emptyList(),
            (500 + it).seconds,
        )
    }

private fun page(artist: Artist) =
    TvArtistUiState.Content(artist, albums, albums.take(2), tracks, tracks, currentTrackId = id("t2"))

@Composable
private fun Page(state: TvArtistUiState) = MediatheqTvTheme { Surface { TvArtistScreen(state, {}, {}) } }

/** With a backdrop and a biography; layoutlib loads no image, so the backdrop's place is empty. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvArtistScreenshot() =
    Page(
        page(
            Artist(
                id("a"),
                PreviewFixtures.ARTIST,
                backdrop = Artwork("a", "t", type = ImageType.Backdrop),
                overview = BIO,
            ),
        ),
    )

@PreviewTest
@MqTvPreviews
@Composable
fun TvArtistPlainScreenshot() = Page(page(Artist(id("a"), PreviewFixtures.ARTIST)))

@PreviewTest
@MqTvPreviews
@Composable
fun TvArtistEmptyScreenshot() =
    Page(
        TvArtistUiState.Content(
            Artist(id("a"), PreviewFixtures.ARTIST),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
        ),
    )

@PreviewTest
@MqTvPreviews
@Composable
fun TvArtistNotFoundScreenshot() = Page(TvArtistUiState.NotFound)
