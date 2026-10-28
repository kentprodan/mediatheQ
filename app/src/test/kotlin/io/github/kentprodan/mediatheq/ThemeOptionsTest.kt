package io.github.kentprodan.mediatheq

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.AppFont
import io.github.kentprodan.mediatheq.core.data.ColorSource
import io.github.kentprodan.mediatheq.core.data.NowPlayingColors
import io.github.kentprodan.mediatheq.core.data.ThemeMode
import io.github.kentprodan.mediatheq.core.data.ThemePreferences
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqColorSource
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqFont
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqPlayerColors
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqThemeMode
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqThemeOptions
import org.junit.Test

class ThemeOptionsTest {
    @Test
    fun `every stored choice maps to the design system option of the same name`() {
        ThemeMode.entries.forEach {
            assertThat(
                ThemePreferences(mode = it).toThemeOptions().mode.name,
            ).isEqualTo(it.name)
        }
        ColorSource.entries.forEach {
            assertThat(ThemePreferences(colorSource = it).toThemeOptions().colorSource.name).isEqualTo(it.name)
        }
        NowPlayingColors.entries.forEach {
            assertThat(ThemePreferences(nowPlayingColors = it).toThemeOptions().playerColors.name).isEqualTo(it.name)
        }
        AppFont.entries.forEach {
            assertThat(
                ThemePreferences(font = it).toThemeOptions().font.name,
            ).isEqualTo(it.name)
        }
    }

    @Test
    fun `the defaults map to the design system defaults`() {
        assertThat(ThemePreferences().toThemeOptions()).isEqualTo(MqThemeOptions())
        assertThat(
            ThemePreferences(
                ThemeMode.SystemBlack,
                ColorSource.Custom,
                0xFF336699.toInt(),
                NowPlayingColors.CoverVibrant,
                ignoreSurfaces = true,
                instantColors = true,
                font = AppFont.Lato,
            ).toThemeOptions(),
        ).isEqualTo(
            MqThemeOptions(
                MqThemeMode.SystemBlack,
                MqColorSource.Custom,
                0xFF336699.toInt(),
                MqPlayerColors.CoverVibrant,
                ignoreSurfaces = true,
                instantColors = true,
                font = MqFont.Lato,
            ),
        )
    }
}
