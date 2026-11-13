package io.github.kentprodan.mediatheq.core.designsystem.tv

import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediatheqTvThemeTest {
    @Test
    fun `the scheme from a seed is always the same, and dark`() {
        val scheme = tvColorScheme(IRIS_SEED)

        val again = tvColorScheme(IRIS_SEED)
        assertThat(listOf(again.primary, again.background, again.surface, again.border))
            .containsExactly(scheme.primary, scheme.background, scheme.surface, scheme.border)
            .inOrder()
        assertThat(scheme.background.luminance()).isLessThan(0.1f)
        assertThat(scheme.onBackground.luminance()).isGreaterThan(0.5f)
        assertThat(tvColorScheme(0xFF00897B.toInt()).primary).isNotEqualTo(scheme.primary)
    }
}
