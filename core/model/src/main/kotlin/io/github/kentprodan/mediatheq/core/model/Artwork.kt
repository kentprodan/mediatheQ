package io.github.kentprodan.mediatheq.core.model

/** The primary image of an item: [itemId] owns the image (a track often uses its album's), [tag] versions it. */
data class Artwork(
    val itemId: String,
    val tag: String,
    val blurHash: String? = null,
)
