package io.github.kentprodan.mediatheq.core.model

/** How the server delivers a subtitle (ADR 0050), as its `DeliveryMethod`. */
enum class SubtitleMethod {
    /** Inside the stream: the player selects the track. */
    Embed,

    /** A file of its own at [SubtitleDelivery.url], side-loaded by the player. */
    External,

    /** Inside the HLS playlist of a conversion. */
    Hls,

    /** Burned into the picture by the conversion: nothing for the player to show. */
    Encode,
}

/** A subtitle format and how the device takes it, for the stream request (ADR 0050). */
data class SubtitleProfile(
    /** Jellyfin's codec name: `subrip`, `webvtt`, `ass`, `pgssub`, ... */
    val format: String,
    val method: SubtitleMethod,
)

/** One subtitle stream of a playable video and how the server delivers it (#274). */
data class SubtitleDelivery(
    val index: Int,
    val method: SubtitleMethod,
    /** The file to side-load for [SubtitleMethod.External]; never carries the token (R1). */
    val url: String? = null,
    val codec: String? = null,
    val language: String? = null,
    val title: String? = null,
    val isForced: Boolean = false,
    val isDefault: Boolean = false,
)
