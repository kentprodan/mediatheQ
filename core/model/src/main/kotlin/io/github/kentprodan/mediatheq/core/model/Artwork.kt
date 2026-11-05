package io.github.kentprodan.mediatheq.core.model

/**
 * An image of an item: [itemId] owns the image (a track often uses its album's), [tag] versions it. Only [ImageType.Primary]
 * images reach media items and the session; a backdrop is for the artist page's header (#133).
 */
data class Artwork(
    val itemId: String,
    val tag: String,
    val blurHash: String? = null,
    val type: ImageType = ImageType.Primary,
)

enum class ImageType {
    /** The cover or the artist's photo. */
    Primary,

    /** A wide artist image (fanart) behind the artist page's header. */
    Backdrop,
}
