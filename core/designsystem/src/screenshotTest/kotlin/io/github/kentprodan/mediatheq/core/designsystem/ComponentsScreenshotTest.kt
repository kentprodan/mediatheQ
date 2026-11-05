package io.github.kentprodan.mediatheq.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.component.MqArtistAvatar
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCarouselItem
import io.github.kentprodan.mediatheq.core.designsystem.component.MqColorSwatch
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCover
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCoverCarousel
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialogContent
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.component.MqFavoriteToggle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqIconToggle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqLoadingIndicator
import io.github.kentprodan.mediatheq.core.designsystem.component.MqRatingDialogContent
import io.github.kentprodan.mediatheq.core.designsystem.component.MqStarRating
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.preview.PREVIEW_BLUR_HASH
import io.github.kentprodan.mediatheq.core.designsystem.preview.rememberPreviewCover
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqSeedPresets

@PreviewTest
@MqPreviews
@Composable
fun EmptyStateWithActionScreenshot() {
    MediatheqTheme {
        Surface {
            MqEmptyState(
                icon = MqIcons.MusicNote,
                title = "Your library is empty",
                body = "Add a server to see your music here.",
                actionLabel = "Add server",
            )
        }
    }
}

@PreviewTest
@MqPreviews
@Composable
fun EmptyStateTitleOnlyScreenshot() {
    MediatheqTheme { Surface { MqEmptyState(icon = MqIcons.MusicNote, title = "No results") } }
}

@PreviewTest
@MqPreviews
@Composable
fun LoadingIndicatorScreenshot() {
    MediatheqTheme { Surface { MqLoadingIndicator(delayMillis = 0) } }
}

/** All three cover states side by side: no image, BlurHash placeholder, loaded image. */
@PreviewTest
@MqPreviews
@Composable
fun CoverStatesScreenshot() {
    MediatheqTheme {
        Surface {
            Row(
                Modifier.padding(MediatheqSpacing.l),
                horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
            ) {
                MqCover(model = null, contentDescription = null, Modifier.width(CoverWidth))
                MqCover(
                    model = null,
                    contentDescription = null,
                    Modifier.width(CoverWidth),
                    blurHash = PREVIEW_BLUR_HASH,
                )
                MqCover(model = rememberPreviewCover(), contentDescription = null, Modifier.width(CoverWidth))
            }
        }
    }
}

/** Artist avatar without a picture and with one. */
@PreviewTest
@MqPreviews
@Composable
fun ArtistAvatarScreenshot() {
    MediatheqTheme {
        Surface {
            Row(
                Modifier.padding(MediatheqSpacing.l),
                horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
            ) {
                MqArtistAvatar(model = null, Modifier.width(CoverWidth))
                MqArtistAvatar(model = rememberPreviewCover(), Modifier.width(CoverWidth))
            }
        }
    }
}

private val CoverWidth = 112.dp

/** Dialog content in place (dialog windows render blank): with a text field, and with a message. */
@PreviewTest
@MqPreviews
@Composable
fun MqDialogScreenshot() {
    MediatheqTheme {
        Column(Modifier.padding(MediatheqSpacing.l), verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.l)) {
            MqDialogContent("Rename", "Save", {}, "Cancel", {}) {
                OutlinedTextField("Road Trip", {}, label = { Text("Name") }, singleLine = true)
            }
            MqDialogContent("Delete \"Road Trip\"?", "Delete", {}, "Cancel", {}) {
                Text("The playlist is deleted from the server. Its tracks stay in your library.")
            }
            MqDialogContent("New playlist", "Create", {}, "Cancel", {}, confirmEnabled = false)
        }
    }
}

/** Icon toggles off and on (shuffle, repeat one). */
@PreviewTest
@MqPreviews
@Composable
fun IconToggleScreenshot() {
    MediatheqTheme {
        Surface {
            Row(Modifier.padding(MediatheqSpacing.l)) {
                MqIconToggle(checked = false, onClick = {}, icon = MqIcons.Shuffle, contentDescription = "Shuffle")
                MqIconToggle(checked = true, onClick = {}, icon = MqIcons.Shuffle, contentDescription = "Shuffle")
                MqIconToggle(checked = true, onClick = {}, icon = MqIcons.RepeatOne, contentDescription = "Repeat")
            }
        }
    }
}

/** The heart off and on, and stars unrated, 1, 3 and 5, then 0.5 and 3.5 with half stars. */
@PreviewTest
@MqPreviews
@Composable
fun FavoriteAndRatingScreenshot() {
    MediatheqTheme {
        Surface {
            Column(Modifier.padding(MediatheqSpacing.l)) {
                Row {
                    MqFavoriteToggle(isFavorite = false, onToggle = {})
                    MqFavoriteToggle(isFavorite = true, onToggle = {})
                }
                listOf(null, 2, 6, 10).forEach { MqStarRating(halfStars = it, onRate = {}) }
                listOf(1, 7).forEach { MqStarRating(halfStars = it, onRate = {}, allowHalf = true) }
            }
        }
    }
}

/** Color swatches: read-only samples, then choices with the selected one checked (light and dark colors). */
@PreviewTest
@MqPreviews
@Composable
fun ColorSwatchesScreenshot() {
    MediatheqTheme {
        Surface {
            FlowRow(
                Modifier.padding(MediatheqSpacing.l),
                horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
            ) {
                MqColorSwatch(MaterialTheme.colorScheme.primary, "Primary")
                MqColorSwatch(MaterialTheme.colorScheme.surface, "Surface")
                MqSeedPresets.forEachIndexed { index, seed ->
                    MqColorSwatch(Color(seed), "Preset $index", selected = index == 0, onClick = {})
                }
                MqColorSwatch(Color.White, "White", selected = true, onClick = {})
            }
        }
    }
}

/** A dialog without a title (a plain question, e.g. Restore defaults). */
@PreviewTest
@MqPreviews
@Composable
fun DialogWithoutTitleScreenshot() {
    MediatheqTheme {
        MqDialogContent(null, "Restore", {}, "Cancel", {}) {
            Text("Restore all options on this page to their default value?")
        }
    }
}

/** The rating dialog after a Rate swipe (#123): whole stars at 3, half stars at 3.5. */
@PreviewTest
@MqPreviews
@Composable
fun MqRatingDialogScreenshot() {
    MediatheqTheme {
        Column {
            MqRatingDialogContent("Morning Light", halfStars = 6, allowHalf = false)
            MqRatingDialogContent("Morning Light", halfStars = 7, allowHalf = true)
        }
    }
}

/** Home's carousel display (#127): large covers, title and subtitle over a scrim. */
@PreviewTest
@MqPreviews
@Composable
fun MqCoverCarouselScreenshot() {
    MediatheqTheme {
        Surface {
            MqCoverCarousel(
                items =
                    (1..5).map {
                        MqCarouselItem(it, null, "Album $it with a long title", "Artist $it", PREVIEW_BLUR_HASH)
                    },
                onClick = {},
            )
        }
    }
}
