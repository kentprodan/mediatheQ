package io.github.kentprodan.mediatheq.core.model

/**
 * Identifies an item on a specific server. [toString] gives `serverId:itemId`, which [parse] reads back
 * (used e.g. as the Media3 `mediaId`).
 */
data class MediaId(
    val serverId: ServerId,
    val itemId: String,
) {
    init {
        require(itemId.isNotEmpty()) { "Empty itemId" }
    }

    override fun toString(): String = "$serverId:$itemId"

    companion object {
        /** Reads the `serverId:itemId` form produced by [toString]. */
        fun parse(value: String): MediaId {
            val separator = value.indexOf(':')
            require(separator > 0) { "Invalid MediaId: '$value'" }
            return MediaId(ServerId(value.substring(0, separator)), value.substring(separator + 1))
        }
    }
}
