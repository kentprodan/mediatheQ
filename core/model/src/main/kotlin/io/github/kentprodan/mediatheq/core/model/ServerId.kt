package io.github.kentprodan.mediatheq.core.model

/** Identifies one configured media server account. Never contains ':' (see [MediaId]). */
@JvmInline
value class ServerId(
    val value: String,
) {
    init {
        require(value.isNotEmpty() && ':' !in value) { "Invalid ServerId: '$value'" }
    }

    override fun toString(): String = value
}
