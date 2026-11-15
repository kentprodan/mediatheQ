package io.github.kentprodan.mediatheq.core.model

/**
 * What a download's key names (#279): a track or a video by its [MediaId] (`server:item`, the music scheme, unchanged),
 * or one external subtitle file of a video (`server:item:sub:<index>`), kept beside it.
 */
sealed interface DownloadKey {
    val id: MediaId

    data class Item(
        override val id: MediaId,
    ) : DownloadKey {
        override fun toString() = id.toString()
    }

    data class Subtitle(
        override val id: MediaId,
        val index: Int,
    ) : DownloadKey {
        override fun toString() = "$id$SUBTITLE_MARK$index"
    }

    companion object {
        /** The request URI of a video download (#279); a track's stays `mediatheq://download`. */
        const val VIDEO_URI = "mediatheq://download/video"

        /** The request URI of a downloaded video's external subtitle file (#279). */
        const val SUBTITLE_URI = "mediatheq://download/sub"

        /** Content metadata of a video key (#279): 1 when the phone cannot play its original (no converted videos). */
        const val CANNOT_DOWNLOAD_METADATA = "mediatheq.cannot_download"

        private const val SUBTITLE_MARK = ":sub:"

        /** `null` for a key that is neither (never thrown: old or foreign keys are skipped). */
        fun parse(key: String): DownloadKey? {
            val mark = key.lastIndexOf(SUBTITLE_MARK)
            if (mark > 0) {
                val index = key.substring(mark + SUBTITLE_MARK.length).toIntOrNull() ?: return null
                val id = runCatching { MediaId.parse(key.substring(0, mark)) }.getOrNull() ?: return null
                return Subtitle(id, index)
            }
            return runCatching { MediaId.parse(key) }.getOrNull()?.let(::Item)
        }
    }
}
