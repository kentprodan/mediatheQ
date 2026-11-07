package io.github.kentprodan.mediatheq.core.model

/** The kind of an audio output (#140). Remote playback outputs come later. */
enum class OutputKind { Phone, Bluetooth, Usb }

/**
 * A remembered audio output (#140). [key] stays the same across reconnects and is never a raw Bluetooth address (a
 * hash). [toString] gives `phone` for the phone and `kind:key` otherwise, which [parseOrNull] reads back.
 */
data class OutputId(
    val kind: OutputKind,
    val key: String,
) {
    init {
        require(key.isNotEmpty() && ':' !in key) { "Invalid output key: '$key'" }
        require((kind == OutputKind.Phone) == (key == PHONE)) { "The phone's key is '$PHONE'" }
    }

    override fun toString(): String = if (kind == OutputKind.Phone) PHONE else "${kind.name.lowercase()}:$key"

    companion object {
        private const val PHONE = "phone"

        /** The phone's own speaker: always there, never forgotten. */
        val Phone = OutputId(OutputKind.Phone, PHONE)

        /** Reads the form [toString] produces; `null` for anything else (an old or damaged stored value). */
        fun parseOrNull(value: String): OutputId? {
            if (value == PHONE) return Phone
            val separator = value.indexOf(':')
            if (separator <= 0) return null
            val kind = OutputKind.entries.firstOrNull { it.name.lowercase() == value.substring(0, separator) }
            val key = value.substring(separator + 1)
            return if (kind == null || kind == OutputKind.Phone || key.isEmpty() || ':' in key) {
                null
            } else {
                OutputId(kind, key)
            }
        }
    }
}
