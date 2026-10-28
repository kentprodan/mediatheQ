package io.github.kentprodan.mediatheq.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import com.materialkolor.hct.Hct
import org.junit.Test

class AppColorSchemeTest {
    private val teal = 0xFF00897B.toInt()
    private val dynamicLight = lightColorScheme(primary = Color.Green)
    private val dynamicDark = darkColorScheme(primary = Color.Green)
    private val cover = coverColorsOf(IntArray(400) { teal })!!

    private fun scheme(
        options: MqThemeOptions,
        systemDark: Boolean = false,
    ) = appColorScheme(options, options.isDark(systemDark), options.isBlack(systemDark)) { dark ->
        if (dark) dynamicDark else dynamicLight
    }

    private fun ColorScheme.isBlack() = background == Color.Black && surface == Color.Black

    @Test
    fun `each mode is light or dark, following the system where it says so`() {
        val modes = MqThemeMode.entries.associateWith { MqThemeOptions(mode = it) }

        assertThat(modes.mapValues { (_, it) -> it.isDark(systemDark = false) })
            .containsExactly(
                MqThemeMode.System,
                false,
                MqThemeMode.Light,
                false,
                MqThemeMode.Dark,
                true,
                MqThemeMode.Black,
                true,
                MqThemeMode.SystemBlack,
                false,
            )
        assertThat(modes.mapValues { (_, it) -> it.isDark(systemDark = true) })
            .containsExactly(
                MqThemeMode.System,
                true,
                MqThemeMode.Light,
                false,
                MqThemeMode.Dark,
                true,
                MqThemeMode.Black,
                true,
                MqThemeMode.SystemBlack,
                true,
            )
    }

    @Test
    fun `black is the Black mode, and System light-black only while the system is dark`() {
        assertThat(MqThemeOptions(mode = MqThemeMode.Black).isBlack(systemDark = false)).isTrue()
        assertThat(MqThemeOptions(mode = MqThemeMode.SystemBlack).isBlack(systemDark = false)).isFalse()
        assertThat(MqThemeOptions(mode = MqThemeMode.SystemBlack).isBlack(systemDark = true)).isTrue()
        assertThat(MqThemeOptions(mode = MqThemeMode.Dark).isBlack(systemDark = true)).isFalse()
    }

    @Test
    fun `Iris and Material You give their own schemes for each mode`() {
        assertThat(scheme(MqThemeOptions())).isSameInstanceAs(IrisLightColorScheme)
        assertThat(scheme(MqThemeOptions(), systemDark = true)).isSameInstanceAs(IrisDarkColorScheme)
        assertThat(scheme(MqThemeOptions(colorSource = MqColorSource.MaterialYou))).isSameInstanceAs(dynamicLight)
        assertThat(scheme(MqThemeOptions(MqThemeMode.Dark, MqColorSource.MaterialYou))).isSameInstanceAs(dynamicDark)
    }

    @Test
    fun `black mode blackens background and base surfaces of any source, keeping the containers`() {
        MqColorSource.entries.forEach { source ->
            val black = scheme(MqThemeOptions(MqThemeMode.Black, source, customSeed = teal))
            val dark = scheme(MqThemeOptions(MqThemeMode.Dark, source, customSeed = teal))

            assertThat(black.isBlack()).isTrue()
            assertThat(black.surfaceDim).isEqualTo(Color.Black)
            assertThat(black.surfaceContainerLowest).isEqualTo(Color.Black)
            assertThat(black.surfaceContainerHigh).isEqualTo(dark.surfaceContainerHigh)
            assertThat(black.primary).isEqualTo(dark.primary)
        }
    }

    @Test
    fun `a custom seed gives a full light and dark scheme in its hue`() {
        val light = scheme(MqThemeOptions(colorSource = MqColorSource.Custom, customSeed = teal))
        val dark = scheme(MqThemeOptions(MqThemeMode.Dark, MqColorSource.Custom, customSeed = teal))
        val hue = { color: Color -> Hct.fromInt(color.value.shr(32).toInt()).hue }

        assertThat(kotlin.math.abs(hue(light.primary) - Hct.fromInt(teal).hue)).isLessThan(30.0)
        assertThat(light.surface.luminance()).isGreaterThan(0.5f)
        assertThat(dark.surface.luminance()).isLessThan(0.1f)
        assertThat(light.primary).isNotEqualTo(IrisLightColorScheme.primary)
    }

    @Test
    fun `the player follows the cover, vibrant when asked, and the app theme when off`() {
        val dark = MqThemeState(MqThemeOptions(), isDark = true)

        assertThat(playerColorScheme(cover, dark)).isSameInstanceAs(cover.dark)
        assertThat(playerColorScheme(cover, MqThemeState())).isSameInstanceAs(cover.light)
        assertThat(
            playerColorScheme(cover, dark.copy(options = MqThemeOptions(playerColors = MqPlayerColors.CoverVibrant))),
        ).isSameInstanceAs(cover.vibrantDark)
        assertThat(playerColorScheme(cover, dark.copy(options = MqThemeOptions(playerColors = MqPlayerColors.Off))))
            .isNull()
        assertThat(playerColorScheme(null, dark)).isNull()
    }

    @Test
    fun `cover vibrant is more saturated than cover`() {
        val chroma = { color: Color -> Hct.fromInt(color.value.shr(32).toInt()).chroma }

        assertThat(chroma(cover.vibrantLight.primaryContainer)).isGreaterThan(chroma(cover.light.primaryContainer))
    }

    @Test
    fun `in black mode the player stays black, unless its surfaces come from the app anyway`() {
        val black = MqThemeState(MqThemeOptions(mode = MqThemeMode.Black), isDark = true, isBlack = true)

        assertThat(playerColorScheme(cover, black)!!.isBlack()).isTrue()
        assertThat(playerColorScheme(cover, black)!!.primary).isEqualTo(cover.dark.primary)
        assertThat(playerColorScheme(cover, black.copy(options = black.options.copy(ignoreSurfaces = true))))
            .isSameInstanceAs(cover.dark)
    }

    @Test
    fun `ignoring surfaces keeps the cover's accents and the app's background and surfaces`() {
        val app = IrisDarkColorScheme

        val merged = cover.dark.withSurfacesOf(app)

        assertThat(merged.primary).isEqualTo(cover.dark.primary)
        assertThat(merged.secondaryContainer).isEqualTo(cover.dark.secondaryContainer)
        assertThat(merged.background).isEqualTo(app.background)
        assertThat(merged.surface).isEqualTo(app.surface)
        assertThat(merged.onSurface).isEqualTo(app.onSurface)
        assertThat(merged.surfaceContainerHigh).isEqualTo(app.surfaceContainerHigh)
    }
}
