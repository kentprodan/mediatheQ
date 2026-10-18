package io.github.kentprodan.mediatheq.core.designsystem.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/** All theme tokens on one scrollable page: color roles, type scale, shapes. Shown by the app until real screens exist. */
@Composable
fun ThemeShowcase(modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(MediatheqSpacing.l),
            verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.xl),
        ) {
            ColorRolesShowcase()
            TypeScaleShowcase()
            ShapesShowcase()
        }
    }
}

/** Every color role of the current scheme as a labeled swatch. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorRolesShowcase(modifier: Modifier = Modifier) {
    val roles = colorRoles(MaterialTheme.colorScheme)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s)) {
        Text("Color roles", style = MaterialTheme.typography.titleLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
            verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
        ) {
            roles.forEach { (name, color) ->
                Column(Modifier.width(SwatchWidth)) {
                    Box(
                        Modifier
                            .size(SwatchWidth, SwatchHeight)
                            .background(color, MaterialTheme.shapes.small)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
                    )
                    Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                }
            }
        }
    }
}

/** Every text style of the type scale, including the emphasized variants. */
@Composable
fun TypeScaleShowcase(modifier: Modifier = Modifier) {
    val styles = typeStyles()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.xs)) {
        Text("Type scale", style = MaterialTheme.typography.titleLarge)
        styles.forEach { (name, style) -> Text(name, style = style, maxLines = 1) }
    }
}

/** Every shape of the shape scale. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShapesShowcase(modifier: Modifier = Modifier) {
    val shapes = shapeRoles()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s)) {
        Text("Shapes", style = MaterialTheme.typography.titleLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
            verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.m),
        ) {
            shapes.forEach { (name, shape) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(ShapeSize).background(MaterialTheme.colorScheme.primaryContainer, shape))
                    Text(name, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private val SwatchWidth = 88.dp
private val SwatchHeight = 40.dp
private val ShapeSize = 72.dp

private fun colorRoles(c: ColorScheme): List<Pair<String, Color>> =
    listOf(
        "primary" to c.primary,
        "onPrimary" to c.onPrimary,
        "primaryContainer" to c.primaryContainer,
        "onPrimaryContainer" to c.onPrimaryContainer,
        "secondary" to c.secondary,
        "onSecondary" to c.onSecondary,
        "secondaryContainer" to c.secondaryContainer,
        "onSecondaryContainer" to c.onSecondaryContainer,
        "tertiary" to c.tertiary,
        "onTertiary" to c.onTertiary,
        "tertiaryContainer" to c.tertiaryContainer,
        "onTertiaryContainer" to c.onTertiaryContainer,
        "error" to c.error,
        "onError" to c.onError,
        "errorContainer" to c.errorContainer,
        "onErrorContainer" to c.onErrorContainer,
        "surfaceDim" to c.surfaceDim,
        "surface" to c.surface,
        "surfaceBright" to c.surfaceBright,
        "surfaceContainerLowest" to c.surfaceContainerLowest,
        "surfaceContainerLow" to c.surfaceContainerLow,
        "surfaceContainer" to c.surfaceContainer,
        "surfaceContainerHigh" to c.surfaceContainerHigh,
        "surfaceContainerHighest" to c.surfaceContainerHighest,
        "onSurface" to c.onSurface,
        "onSurfaceVariant" to c.onSurfaceVariant,
        "outline" to c.outline,
        "outlineVariant" to c.outlineVariant,
        "inverseSurface" to c.inverseSurface,
        "inverseOnSurface" to c.inverseOnSurface,
        "inversePrimary" to c.inversePrimary,
        "primaryFixed" to c.primaryFixed,
        "primaryFixedDim" to c.primaryFixedDim,
        "secondaryFixed" to c.secondaryFixed,
        "tertiaryFixed" to c.tertiaryFixed,
    )

@Composable
private fun typeStyles(): List<Pair<String, TextStyle>> {
    val t = MaterialTheme.typography
    return listOf(
        "displayLarge" to t.displayLarge,
        "displayMedium" to t.displayMedium,
        "displaySmall" to t.displaySmall,
        "headlineLarge" to t.headlineLarge,
        "headlineMedium" to t.headlineMedium,
        "headlineSmall" to t.headlineSmall,
        "titleLarge" to t.titleLarge,
        "titleLargeEmphasized" to t.titleLargeEmphasized,
        "titleMedium" to t.titleMedium,
        "titleSmall" to t.titleSmall,
        "bodyLarge" to t.bodyLarge,
        "bodyLargeEmphasized" to t.bodyLargeEmphasized,
        "bodyMedium" to t.bodyMedium,
        "bodySmall" to t.bodySmall,
        "labelLarge" to t.labelLarge,
        "labelMedium" to t.labelMedium,
        "labelSmall" to t.labelSmall,
    )
}

@Composable
private fun shapeRoles(): List<Pair<String, Shape>> {
    val s = MaterialTheme.shapes
    return listOf(
        "extraSmall" to s.extraSmall,
        "small" to s.small,
        "medium" to s.medium,
        "large" to s.large,
        "largeIncreased" to s.largeIncreased,
        "extraLarge" to s.extraLarge,
        "extraLargeIncreased" to s.extraLargeIncreased,
        "extraExtraLarge" to s.extraExtraLarge,
    )
}
