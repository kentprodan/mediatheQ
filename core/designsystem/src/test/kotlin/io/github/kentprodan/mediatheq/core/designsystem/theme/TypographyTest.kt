package io.github.kentprodan.mediatheq.core.designsystem.theme

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TypographyTest {
    private val system = MediatheqTypography

    @Test
    fun `the system font is the default type scale`() {
        assertThat(mediatheqTypography(MqFont.System)).isSameInstanceAs(system)
    }

    @Test
    fun `a whole font changes headings, body and labels alike`() {
        val inter = mediatheqTypography(MqFont.Inter)

        assertThat(inter.bodyLarge.fontFamily).isNotNull()
        assertThat(inter.bodyLarge.fontFamily).isEqualTo(inter.titleLarge.fontFamily)
        assertThat(inter.labelSmall.fontFamily).isEqualTo(inter.displayLarge.fontFamily)
        assertThat(inter.bodyLarge.fontSize).isEqualTo(system.bodyLarge.fontSize)
    }

    @Test
    fun `display fonts change headings only, body and labels stay the system font`() {
        listOf(MqFont.BebasNeue, MqFont.CastoroTitling).forEach { font ->
            val typography = mediatheqTypography(font)

            assertThat(typography.displayLarge.fontFamily).isNotEqualTo(system.displayLarge.fontFamily)
            assertThat(typography.titleSmallEmphasized.fontFamily).isEqualTo(typography.displayLarge.fontFamily)
            assertThat(typography.bodyLarge).isEqualTo(system.bodyLarge)
            assertThat(typography.labelLarge).isEqualTo(system.labelLarge)
            assertThat(typography.bodyMediumEmphasized).isEqualTo(system.bodyMediumEmphasized)
        }
    }

    @Test
    fun `Bitter and Rubik split headings and body`() {
        val pair = mediatheqTypography(MqFont.BitterRubik)

        assertThat(pair.headlineLarge.fontFamily).isNotEqualTo(pair.bodyLarge.fontFamily)
        assertThat(pair.bodyLarge.fontFamily).isEqualTo(pair.labelMedium.fontFamily)
        assertThat(pair.bodyLarge.fontFamily).isNotEqualTo(system.bodyLarge.fontFamily)
    }
}
