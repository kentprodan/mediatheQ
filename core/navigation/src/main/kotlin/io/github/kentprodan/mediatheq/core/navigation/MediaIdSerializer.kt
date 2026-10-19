package io.github.kentprodan.mediatheq.core.navigation

import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Saves a [MediaId] in a NavKey as its `serverId:itemId` text, so back stacks survive process death. */
object MediaIdSerializer : KSerializer<MediaId> {
    override val descriptor = PrimitiveSerialDescriptor("MediaId", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: MediaId,
    ) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): MediaId = MediaId.parse(decoder.decodeString())
}
