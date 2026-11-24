package io.github.kentprodan.mediatheq.core.designsystem.tv.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardScale
import androidx.tv.material3.ClassicCard
import androidx.tv.material3.CompactCard
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.StandardCardContainer
import androidx.tv.material3.Text
import androidx.tv.material3.WideCardContainer
import androidx.tv.material3.WideClassicCard

/**
 * The guideline's card variants (#518): Standard (text under the image, outside the card), Classic (text inside the
 * card under the image), Compact (text over the image with a scrim), Wide standard and Wide classic (the image beside
 * the text).
 */
enum class MqTvCardStyle {
    Standard,
    Classic,
    Compact,
    WideStandard,
    WideClassic,
    ;

    val wide: Boolean get() = this == WideStandard || this == WideClassic

    /** The guideline width: five cards per screen, or three for the wide variants (20 dp apart). */
    val width: Dp get() = if (wide) WIDE_CARD_WIDTH else CARD_WIDTH
}

/**
 * The guideline's card width when [count] cards fit across the screen, 20 dp apart with the next one peeking (#604):
 * 1 = 844 dp, 2 = 412, 3 = 268, 4 = 196, 5 = 124.
 */
fun cardWidth(count: Int): Dp = CARD_WIDTHS[count.coerceIn(1, CARD_WIDTHS.size) - 1]

/**
 * A card in [style] with [image] at [imageAspectRatio], its [title], an optional [subtitle] and, on the wide variants,
 * a two-line [description]. Focus (#517, the guideline's card focus): a slight scale and a thin white outline, no
 * brand color. The content block follows the guideline's spacing (#603): 8 dp under a Standard image, 12 dp padding
 * inside Classic and Compact, 16 x 12 dp beside a wide image, 8 dp before the description.
 */
@Composable
fun MqTvCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /** Long-press of select: the item's menu. */
    onLongClick: (() -> Unit)? = null,
    imageAspectRatio: Float = 1f,
    style: MqTvCardStyle = MqTvCardStyle.Classic,
    description: String? = null,
    /** The whole card's width; a wide card gives half of it to its image. */
    width: Dp = style.width,
    image: @Composable () -> Unit,
) {
    val scale = CardDefaults.scale(focusedScale = FOCUSED_SCALE)
    val border = focusBorder()
    // Standard and Wide standard keep their text outside the focusable card: the card itself carries it for
    // TalkBack, and the visible text is left out of the semantics.
    val outside = style == MqTvCardStyle.Standard || style == MqTvCardStyle.WideStandard
    val textSemantics = if (outside) Modifier.clearAndSetSemantics {} else Modifier
    val label = listOfNotNull(title, subtitle).joinToString(", ")
    val padding =
        when (style) {
            MqTvCardStyle.Standard -> {
                PaddingValues(top = STANDARD_GAP)
            }

            MqTvCardStyle.Classic, MqTvCardStyle.Compact -> {
                PaddingValues(INNER_PADDING)
            }

            MqTvCardStyle.WideStandard, MqTvCardStyle.WideClassic -> {
                PaddingValues(horizontal = WIDE_PADDING_H, vertical = INNER_PADDING)
            }
        }
    // The whole content block in the title slot: tv-material's slots have no spacing or padding of their own.
    val titleText: @Composable () -> Unit = {
        ContentBlock(
            title,
            subtitle,
            description?.takeIf { style.wide },
            centered = style == MqTvCardStyle.Standard,
            modifier = textSemantics.padding(padding),
        )
    }
    val subtitleText: @Composable () -> Unit = {}
    // A wide card puts its image beside the text, at a fixed width.
    val pictureSize = if (style.wide) Modifier.width(width / 2) else Modifier.fillMaxWidth()
    val picture: @Composable () -> Unit = { Box(pictureSize.aspectRatio(imageAspectRatio)) { image() } }
    val sized = modifier.width(width)
    when (style) {
        MqTvCardStyle.Standard -> {
            StandardCardContainer(
                imageCard = { source -> ImageCard(label, onClick, onLongClick, scale, border, source, picture) },
                title = titleText,
                modifier = sized,
                subtitle = subtitleText,
            )
        }

        MqTvCardStyle.Classic -> {
            ClassicCard(
                onClick = onClick,
                image = { picture() },
                title = titleText,
                modifier = sized,
                onLongClick = onLongClick,
                subtitle = subtitleText,
                scale = scale,
                border = border,
            )
        }

        MqTvCardStyle.Compact -> {
            CompactCard(
                onClick = onClick,
                image = { picture() },
                title = titleText,
                modifier = sized,
                onLongClick = onLongClick,
                subtitle = subtitleText,
                scale = scale,
                border = border,
            )
        }

        MqTvCardStyle.WideStandard -> {
            WideCardContainer(
                imageCard = { source -> ImageCard(label, onClick, onLongClick, scale, border, source, picture) },
                title = titleText,
                modifier = sized,
                subtitle = subtitleText,
            )
        }

        MqTvCardStyle.WideClassic -> {
            WideClassicCard(
                onClick = onClick,
                image = { picture() },
                title = titleText,
                modifier = sized,
                onLongClick = onLongClick,
                subtitle = subtitleText,
                scale = scale,
                border = border,
            )
        }
    }
}

@Composable
private fun ImageCard(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    scale: CardScale,
    border: CardBorder,
    source: MutableInteractionSource,
    picture: @Composable () -> Unit,
) = Card(
    onClick = onClick,
    modifier = Modifier.semantics { contentDescription = label },
    onLongClick = onLongClick,
    scale = scale,
    border = border,
    interactionSource = source,
) { picture() }

/** The guideline's content block: title, subtitle, then the description 8 dp below. */
@Composable
private fun ContentBlock(
    title: String,
    subtitle: String?,
    description: String?,
    centered: Boolean,
    modifier: Modifier,
) {
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            textAlign = align,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = align,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        description?.let {
            Text(
                it,
                Modifier.padding(top = BLOCK_GAP),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun focusBorder(): CardBorder =
    CardDefaults.border(
        focusedBorder = Border(BorderStroke(FOCUS_BORDER, Color.White), shape = MaterialTheme.shapes.medium),
    )

// Guideline widths (#507): 124 dp for five cards per screen, 268 dp for three.
private val CARD_WIDTH = 124.dp
private val WIDE_CARD_WIDTH = 268.dp
private val CARD_WIDTHS = listOf(844.dp, 412.dp, 268.dp, 196.dp, 124.dp)
private const val FOCUSED_SCALE = 1.05f

// The guideline's content block spacing (#603).
private val STANDARD_GAP = 8.dp
private val INNER_PADDING = 12.dp
private val WIDE_PADDING_H = 16.dp
private val BLOCK_GAP = 8.dp
private val FOCUS_BORDER = 2.dp
