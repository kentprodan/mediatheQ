package io.github.kentprodan.mediatheq.core.navigation

import io.github.kentprodan.mediatheq.core.model.OutputId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Saves an [OutputId] in a NavKey as its text form, so back stacks survive process death. */
object OutputIdSerializer : KSerializer<OutputId> {
    override val descriptor = PrimitiveSerialDescriptor("OutputId", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: OutputId,
    ) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): OutputId =
        decoder.decodeString().let {
            OutputId.parseOrNull(
                it,
            ) ?: throw SerializationException("Invalid OutputId '$it'")
        }
}
