package io.github.kentprodan.mediatheq.core.data

/** The enum value stored as [name]; `null` for a missing, old or damaged value (the default applies). */
internal inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
    enumValues<E>().firstOrNull { it.name == name }

/** Stored custom band gains; anything but one valid gain per band is dropped (an old or damaged value: the defaults). */
internal fun String.toGains(): List<Float>? =
    split(",")
        .map { it.toFloatOrNull()?.takeIf { gain -> gain in AudioPreferences.EQ_GAIN_DB } ?: return null }
        .takeIf { it.size == AudioPreferences.EQ_BANDS_HZ.size }
