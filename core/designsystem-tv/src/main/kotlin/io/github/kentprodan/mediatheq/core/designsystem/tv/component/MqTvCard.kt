package io.github.kentprodan.mediatheq.core.designsystem.tv.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * A card in [style] with [image] at [imageAspectRatio], its [title] and an optional [subtitle]. Focus (#517, the
 * guideline's card focus): a slight scale and a thin white outline, no brand color.
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
    image: @Composable () -> Unit,
) {
    val scale = CardDefaults.scale(focusedScale = FOCUSED_SCALE)
    val border = focusBorder()
    // Standard and Wide standard keep their text outside the focusable card: the card itself carries it for
    // TalkBack, and the visible text is left out of the semantics.
    val outside = style == MqTvCardStyle.Standard || style == MqTvCardStyle.WideStandard
    val textSemantics = if (outside) Modifier.clearAndSetSemantics {} else Modifier
    val label = listOfNotNull(title, subtitle).joinToString(", ")
    val titleText: @Composable () -> Unit = { CardTitle(title, textSemantics) }
    val subtitleText: @Composable () -> Unit = { subtitle?.let { CardSubtitle(it, textSemantics) } }
    // A wide card puts its image beside the text, at a fixed width.
    val pictureSize = if (style.wide) Modifier.width(WIDE_IMAGE_WIDTH) else Modifier.fillMaxWidth()
    val picture: @Composable () -> Unit = { Box(pictureSize.aspectRatio(imageAspectRatio)) { image() } }
    val sized = modifier.width(style.width)
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

@Composable
private fun CardTitle(
    text: String,
    modifier: Modifier,
) = Text(text, modifier, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun CardSubtitle(
    text: String,
    modifier: Modifier,
) = Text(
    text,
    modifier,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)

@Composable
private fun focusBorder(): CardBorder =
    CardDefaults.border(
        focusedBorder = Border(BorderStroke(FOCUS_BORDER, Color.White), shape = MaterialTheme.shapes.medium),
    )

// Guideline widths (#507): 124 dp for five cards per screen, 268 dp for three.
private val CARD_WIDTH = 124.dp
private val WIDE_CARD_WIDTH = 268.dp
private val WIDE_IMAGE_WIDTH = 124.dp
private const val FOCUSED_SCALE = 1.05f
private val FOCUS_BORDER = 2.dp
