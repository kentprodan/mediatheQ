package io.github.kentprodan.mediatheq.core.designsystem.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqSpacing

/** Groups of color roles, small enough that each group fits one Pixel 9 screenshot at fontScale 2.0. */
enum class ColorRoleGroup { Accent, Surface, Fixed }

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
            ColorRoleGroup.entries.forEach { ColorRolesShowcase(it) }
            TypeScaleShowcase(emphasized = false)
            TypeScaleShowcase(emphasized = true)
            ShapesShowcase()
        }
    }
}

/** Every color role of [group] in the current scheme as a labeled swatch. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorRolesShowcase(
    group: ColorRoleGroup,
    modifier: Modifier = Modifier,
) {
    val roles = colorRoles(MaterialTheme.colorScheme, group)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.s)) {
        Text("Color roles: $group", style = MaterialTheme.typography.titleLarge)
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
                    Text(name, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** Every text style of the type scale, either the regular or the emphasized set. */
@Composable
fun TypeScaleShowcase(
    emphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    val styles = typeStyles(MaterialTheme.typography, emphasized)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MediatheqSpacing.xs)) {
        Text(if (emphasized) "Type scale: emphasized" else "Type scale", style = MaterialTheme.typography.titleLarge)
        styles.forEach { (name, style) ->
            // A short sample keeps every row on one line, even at fontScale 2.0.
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(MediatheqSpacing.s),
            ) {
                Text("Aa", style = style)
                Text(name, style = MaterialTheme.typography.labelSmall)
            }
        }
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

private fun colorRoles(
    c: ColorScheme,
    group: ColorRoleGroup,
): List<Pair<String, Color>> =
    when (group) {
        ColorRoleGroup.Accent -> {
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
            )
        }

        ColorRoleGroup.Surface -> {
            listOf(
                "background" to c.background,
                "onBackground" to c.onBackground,
                "surfaceDim" to c.surfaceDim,
                "surface" to c.surface,
                "surfaceBright" to c.surfaceBright,
                "surfaceContainerLowest" to c.surfaceContainerLowest,
                "surfaceContainerLow" to c.surfaceContainerLow,
                "surfaceContainer" to c.surfaceContainer,
                "surfaceContainerHigh" to c.surfaceContainerHigh,
                "surfaceContainerHighest" to c.surfaceContainerHighest,
                "onSurface" to c.onSurface,
                "surfaceVariant" to c.surfaceVariant,
                "onSurfaceVariant" to c.onSurfaceVariant,
                "surfaceTint" to c.surfaceTint,
                "outline" to c.outline,
                "outlineVariant" to c.outlineVariant,
                "inverseSurface" to c.inverseSurface,
                "inverseOnSurface" to c.inverseOnSurface,
                "inversePrimary" to c.inversePrimary,
                "scrim" to c.scrim,
            )
        }

        ColorRoleGroup.Fixed -> {
            listOf(
                "primaryFixed" to c.primaryFixed,
                "primaryFixedDim" to c.primaryFixedDim,
                "onPrimaryFixed" to c.onPrimaryFixed,
                "onPrimaryFixedVariant" to c.onPrimaryFixedVariant,
                "secondaryFixed" to c.secondaryFixed,
                "secondaryFixedDim" to c.secondaryFixedDim,
                "onSecondaryFixed" to c.onSecondaryFixed,
                "onSecondaryFixedVariant" to c.onSecondaryFixedVariant,
                "tertiaryFixed" to c.tertiaryFixed,
                "tertiaryFixedDim" to c.tertiaryFixedDim,
                "onTertiaryFixed" to c.onTertiaryFixed,
                "onTertiaryFixedVariant" to c.onTertiaryFixedVariant,
            )
        }
    }

private fun typeStyles(
    t: Typography,
    emphasized: Boolean,
): List<Pair<String, TextStyle>> =
    if (emphasized) {
        listOf(
            "displayLargeEmphasized" to t.displayLargeEmphasized,
            "displayMediumEmphasized" to t.displayMediumEmphasized,
            "displaySmallEmphasized" to t.displaySmallEmphasized,
            "headlineLargeEmphasized" to t.headlineLargeEmphasized,
            "headlineMediumEmphasized" to t.headlineMediumEmphasized,
            "headlineSmallEmphasized" to t.headlineSmallEmphasized,
            "titleLargeEmphasized" to t.titleLargeEmphasized,
            "titleMediumEmphasized" to t.titleMediumEmphasized,
            "titleSmallEmphasized" to t.titleSmallEmphasized,
            "bodyLargeEmphasized" to t.bodyLargeEmphasized,
            "bodyMediumEmphasized" to t.bodyMediumEmphasized,
            "bodySmallEmphasized" to t.bodySmallEmphasized,
            "labelLargeEmphasized" to t.labelLargeEmphasized,
            "labelMediumEmphasized" to t.labelMediumEmphasized,
            "labelSmallEmphasized" to t.labelSmallEmphasized,
        )
    } else {
        listOf(
            "displayLarge" to t.displayLarge,
            "displayMedium" to t.displayMedium,
            "displaySmall" to t.displaySmall,
            "headlineLarge" to t.headlineLarge,
            "headlineMedium" to t.headlineMedium,
            "headlineSmall" to t.headlineSmall,
            "titleLarge" to t.titleLarge,
            "titleMedium" to t.titleMedium,
            "titleSmall" to t.titleSmall,
            "bodyLarge" to t.bodyLarge,
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
