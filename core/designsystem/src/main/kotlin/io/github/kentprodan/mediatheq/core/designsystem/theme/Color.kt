package io.github.kentprodan.mediatheq.core.designsystem.theme

// Iris (ADR 0012, #530): neutral surfaces with the Iris seed #5B45D6 as the accent. Built once: schemes compare by
// identity, a new instance would restart the theme blend.

internal val IrisLightColorScheme by lazy { neutralColorScheme(MqThemeOptions.IRIS_SEED, isDark = false) }

internal val IrisDarkColorScheme by lazy { neutralColorScheme(MqThemeOptions.IRIS_SEED, isDark = true) }
