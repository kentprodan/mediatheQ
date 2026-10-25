package io.github.kentprodan.mediatheq.core.ui

/** A frequency or sample rate: "60 Hz", "3.6 kHz", "44.1 kHz", "96 kHz". Locale-independent digits. */
fun formatFrequency(hz: Int): String {
    if (hz < 1_000) return "$hz Hz"
    val tenths = (hz + 50) / 100
    return if (tenths % 10 == 0) "${tenths / 10} kHz" else "${tenths / 10}.${tenths % 10} kHz"
}
