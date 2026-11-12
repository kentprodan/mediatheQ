package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.HomeDisplay
import io.github.kentprodan.mediatheq.core.model.HomeSectionSettings

/**
 * A Home section's settings as one stored value (#182): `key=value` pairs separated by `;`, only for the fields that
 * differ from [defaults], so a field added later reads as its default and an unknown or broken pair is skipped.
 * `null` ("same as Media lists") is left out like a default. `null` when nothing differs.
 */
internal fun encodeHomeSection(
    settings: HomeSectionSettings,
    defaults: HomeSectionSettings,
): String? {
    val pairs =
        listOfNotNull(
            "display=${settings.display.name}".takeIf { settings.display != defaults.display },
            "rows=${settings.gridRows}".takeIf { settings.gridRows != defaults.gridRows },
            "max=${settings.maxItems}".takeIf { settings.maxItems != defaults.maxItems },
            "header=${settings.showHeader}".takeIf { settings.showHeader != defaults.showHeader },
            "more=${settings.moreIndicator}".takeIf { settings.moreIndicator != defaults.moreIndicator },
            "hideEmpty=${settings.hideIfEmpty}".takeIf { settings.hideIfEmpty != defaults.hideIfEmpty },
            settings.centerText?.let { "center=$it" },
            "noSubtitles=${settings.hideSubtitles}".takeIf { settings.hideSubtitles != defaults.hideSubtitles },
            "play=${settings.playButton}".takeIf { settings.playButton != defaults.playButton },
            settings.favoriteOverlay?.let { "favorite=$it" },
            settings.offlineOverlay?.let { "offline=$it" },
        )
    return pairs.takeIf { it.isNotEmpty() }?.joinToString(";")
}

/** [encodeHomeSection] read back over [defaults], within range. */
internal fun decodeHomeSection(
    value: String,
    defaults: HomeSectionSettings,
): HomeSectionSettings =
    value
        .split(';')
        .mapNotNull { pair -> pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
        .fold(defaults) { settings, (key, raw) ->
            val flag = raw.toBooleanStrictOrNull()
            when (key) {
                "display" -> HomeDisplay.entries.firstOrNull { it.name == raw }?.let { settings.copy(display = it) }
                "rows" -> raw.toIntOrNull()?.let { settings.copy(gridRows = it) }
                "max" -> raw.toIntOrNull()?.let { settings.copy(maxItems = it) }
                "header" -> flag?.let { settings.copy(showHeader = it) }
                "more" -> flag?.let { settings.copy(moreIndicator = it) }
                "hideEmpty" -> flag?.let { settings.copy(hideIfEmpty = it) }
                "center" -> flag?.let { settings.copy(centerText = it) }
                "noSubtitles" -> flag?.let { settings.copy(hideSubtitles = it) }
                "play" -> flag?.let { settings.copy(playButton = it) }
                "favorite" -> flag?.let { settings.copy(favoriteOverlay = it) }
                "offline" -> flag?.let { settings.copy(offlineOverlay = it) }
                else -> null
            } ?: settings
        }.clamped()
