package io.github.kentprodan.mediatheq.core.model

/** A pending Quick Connect request: the user approves [code] in another signed-in client. */
data class QuickConnectCode(
    val code: String,
    val secret: String,
) {
    override fun toString(): String = "QuickConnectCode(code=$code)"
}
