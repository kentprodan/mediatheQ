package io.github.kentprodan.mediatheq.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.kentprodan.mediatheq.core.data.GenreItem
import io.github.kentprodan.mediatheq.core.data.GenreRow
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSizes
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing
import io.github.kentprodan.mediatheq.core.ui.MqPosterCard

/** Home's genre rows (#441): the genre's name, then its movies and series as posters; a tap opens the page. */
internal fun LazyListScope.genreRows(
    rows: List<GenreRow>,
    navigation: HomeNavigation,
) {
    rows.forEach { row ->
        item(key = "genre-${row.genre}-title", contentType = "title") {
            Text(
                row.genre,
                Modifier
                    .padding(
                        start = MediatheqSpacing.l,
                        end = MediatheqSpacing.l,
                        top = MediatheqSpacing.l,
                        bottom = MediatheqSpacing.s,
                    ).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
        }
        item(key = "genre-${row.genre}-items") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = MediatheqSpacing.l),
                horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
            ) {
                items(row.items, key = { it.id.toString() }) { item ->
                    when (item) {
                        is GenreItem.Movie -> {
                            MqPosterCard(
                                item.video.title,
                                item.video.year?.toString(),
                                item.video.artwork,
                                onClick = { navigation.onOpenMovie(item.id) },
                                modifier = Modifier.width(MediatheqSizes.albumCardInRow),
                                watched = item.video.userData.played,
                            )
                        }

                        is GenreItem.Show -> {
                            MqPosterCard(
                                item.series.title,
                                item.series.year?.toString(),
                                item.series.artwork,
                                onClick = { navigation.onOpenSeries(item.id) },
                                modifier = Modifier.width(MediatheqSizes.albumCardInRow),
                                watched = item.series.userData.played,
                            )
                        }
                    }
                }
            }
        }
    }
}
