package io.github.kentprodan.mediatheq.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.component.MqArtistAvatar
import io.github.kentprodan.mediatheq.core.designsystem.component.MqCover
import io.github.kentprodan.mediatheq.core.designsystem.component.MqDialogContent
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.component.MqFavoriteToggle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqFilterBar
import io.github.kentprodan.mediatheq.core.designsystem.component.MqFilterBarStyle
import io.github.kentprodan.mediatheq.core.designsystem.component.MqLoadingIndicator
import io.github.kentprodan.mediatheq.core.designsystem.component.MqStarRating
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.preview.PREVIEW_BLUR_HASH
import io.github.kentprodan.mediatheq.core.designsystem.preview.rememberPreviewCover
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme

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

/** Filter bar in both styles (ADR 0014), second option selected. */
@PreviewTest
@MqPreviews
@Composable
fun MqFilterBarScreenshot() {
    val labels = listOf("Albums", "Artists", "Playlists", "Songs")
    MediatheqTheme {
        Surface {
            Column(
                Modifier.padding(vertical = MediatheqSpacing.l),
                verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.l),
            ) {
                MqFilterBar(labels, selectedIndex = 1, onSelect = {})
                MqFilterBar(labels, selectedIndex = 1, onSelect = {}, style = MqFilterBarStyle.ConnectedButtons)
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

/** The heart off and on, and stars unrated, 1, 3 and 5. */
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
                listOf(null, 1, 3, 5).forEach { MqStarRating(stars = it, onRate = {}) }
            }
        }
    }
}
