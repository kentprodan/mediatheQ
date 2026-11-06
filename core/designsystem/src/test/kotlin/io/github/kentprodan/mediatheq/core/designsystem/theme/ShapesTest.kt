package io.github.kentprodan.mediatheq.core.designsystem.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShapesTest {
    private fun CornerBasedShape.dp() = topStart.toPx(Size(1000f, 1000f), Density(1f))

    private fun MqCornerScale.roles() =
        mediatheqShapes(this).run {
            listOf(extraSmall, small, medium, large, largeIncreased, extraLarge, extraLargeIncreased, extraExtraLarge)
                .map { it.dp() }
        }

    @Test
    fun `Normal is the M3 shape scale`() {
        assertThat(MqCornerScale.Normal.roles()).containsExactly(4f, 8f, 12f, 16f, 20f, 28f, 32f, 48f).inOrder()
    }

    @Test
    fun `Small halves every corner and Large makes it one and a half times`() {
        assertThat(MqCornerScale.Small.roles()).containsExactly(2f, 4f, 6f, 8f, 10f, 14f, 16f, 24f).inOrder()
        assertThat(MqCornerScale.Large.roles()).containsExactly(6f, 12f, 18f, 24f, 30f, 42f, 48f, 72f).inOrder()
    }
}
