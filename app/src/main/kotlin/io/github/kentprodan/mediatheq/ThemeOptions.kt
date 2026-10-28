package io.github.kentprodan.mediatheq

import io.github.kentprodan.mediatheq.core.data.ColorSource
import io.github.kentprodan.mediatheq.core.data.NowPlayingColors
import io.github.kentprodan.mediatheq.core.data.ThemeMode
import io.github.kentprodan.mediatheq.core.data.ThemePreferences
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqColorSource
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqFont
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqPlayerColors
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqThemeMode
import io.github.kentprodan.mediatheq.core.designsystem.theme.MqThemeOptions

/** The stored theme settings as the design system's options (it knows no domain types). */
fun ThemePreferences.toThemeOptions(): MqThemeOptions =
    MqThemeOptions(
        mode =
            when (mode) {
                ThemeMode.System -> MqThemeMode.System
                ThemeMode.Light -> MqThemeMode.Light
                ThemeMode.Dark -> MqThemeMode.Dark
                ThemeMode.Black -> MqThemeMode.Black
                ThemeMode.SystemBlack -> MqThemeMode.SystemBlack
            },
        colorSource =
            when (colorSource) {
                ColorSource.Iris -> MqColorSource.Iris
                ColorSource.MaterialYou -> MqColorSource.MaterialYou
                ColorSource.Custom -> MqColorSource.Custom
            },
        customSeed = customSeed,
        playerColors =
            when (nowPlayingColors) {
                NowPlayingColors.Off -> MqPlayerColors.Off
                NowPlayingColors.Cover -> MqPlayerColors.Cover
                NowPlayingColors.CoverVibrant -> MqPlayerColors.CoverVibrant
            },
        ignoreSurfaces = ignoreSurfaces,
        instantColors = instantColors,
        font = MqFont.valueOf(font.name),
    )
