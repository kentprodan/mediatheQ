package io.github.kentprodan.mediatheq.core.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.TvTitleStyle

/** The title style of the detail pages, from Settings -> Display -> Title (#523); the TV shell provides it. */
val LocalTvTitleStyle = staticCompositionLocalOf { TvTitleStyle.Logo }

/**
 * A detail page's title (#523): the item's [logo] (left aligned, at most [LOGO_HEIGHT] high), the [title] as text, or
 * both, as [style] says; without a logo it is always the text. Read as one heading named [title].
 */
@Composable
fun MqTvTitle(
    title: String,
    logo: Artwork?,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    style: TvTitleStyle = LocalTvTitleStyle.current,
) {
    val showLogo = logo != null && style != TvTitleStyle.Text
    Column(
        modifier.semantics(mergeDescendants = true) {
            heading()
            if (showLogo && style == TvTitleStyle.Logo) contentDescription = title
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showLogo) {
            AsyncImage(
                logo,
                contentDescription = null,
                modifier = Modifier.height(LOGO_HEIGHT).widthIn(max = LOGO_MAX_WIDTH),
                alignment = Alignment.CenterStart,
                contentScale = ContentScale.Fit,
            )
        }
        if (!showLogo || style == TvTitleStyle.LogoAndText) {
            Text(
                title,
                Modifier.widthIn(max = 840.dp),
                // Under a logo the name is a line of its own, smaller than a title.
                style = if (showLogo) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displaySmall,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val LOGO_HEIGHT = 120.dp
private val LOGO_MAX_WIDTH = 480.dp
