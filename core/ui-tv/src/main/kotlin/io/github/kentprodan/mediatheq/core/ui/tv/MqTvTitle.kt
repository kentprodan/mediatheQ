package io.github.kentprodan.mediatheq.core.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.kentprodan.mediatheq.core.model.TvLogoSize
import io.github.kentprodan.mediatheq.core.model.TvTitlePage
import io.github.kentprodan.mediatheq.core.model.TvTitleStyle

/** Each detail page's title style, from Settings -> Screens (#523, #619); the TV shell provides it. */
val LocalTvTitleStyle = staticCompositionLocalOf { emptyMap<TvTitlePage, TvTitleStyle>() }

/** Each detail page's logo size, from Settings -> Screens (#578, #619); the TV shell provides it. */
val LocalTvLogoSize = staticCompositionLocalOf { emptyMap<TvTitlePage, TvLogoSize>() }

/**
 * A detail [page]'s title (#523): the item's [logo] (left aligned, [size] high), the [title] as text, or
 * both, as [style] says; without a logo it is always the text. Read as one heading named [title].
 */
@Composable
fun MqTvTitle(
    title: String,
    logo: Artwork?,
    page: TvTitlePage,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    style: TvTitleStyle = LocalTvTitleStyle.current[page] ?: TvTitleStyle.Logo,
    size: TvLogoSize = LocalTvLogoSize.current[page] ?: TvLogoSize.Medium,
) {
    // A logo that does not load (offline, gone from the server) gives way to the name.
    var failed by remember(logo) { mutableStateOf(false) }
    val showLogo = logo != null && style != TvTitleStyle.Text && !failed
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
                // A wide logo is held by the width, a tall one by the height: both stay inside their area.
                modifier = Modifier.height(size.height).widthIn(max = size.height * LOGO_ASPECT),
                alignment = Alignment.CenterStart,
                contentScale = ContentScale.Fit,
                onError = { failed = true },
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

/** The logo's area (#578, one step smaller in #602): Large is #523's 120 x 480 dp. */
private val TvLogoSize.height
    get() =
        when (this) {
            TvLogoSize.Small -> 56.dp
            TvLogoSize.Medium -> 80.dp
            TvLogoSize.Large -> 120.dp
        }

private const val LOGO_ASPECT = 4f
