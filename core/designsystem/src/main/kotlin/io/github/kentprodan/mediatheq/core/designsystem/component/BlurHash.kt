package io.github.kentprodan.mediatheq.core.designsystem.component

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.withSign

/**
 * Decodes a BlurHash (https://blurha.sh) into [width] x [height] opaque ARGB pixels, row by row.
 * Returns `null` for an invalid hash.
 */
internal fun decodeBlurHash(
    hash: String,
    width: Int,
    height: Int,
): IntArray? {
    if (hash.length < 6) return null
    val sizeFlag = decode83(hash, 0, 1) ?: return null
    val numX = sizeFlag % 9 + 1
    val numY = sizeFlag / 9 + 1
    if (hash.length != 4 + 2 * numX * numY) return null
    val quantisedMax = decode83(hash, 1, 2) ?: return null
    val maxValue = (quantisedMax + 1) / 166f

    val colors =
        Array(numX * numY) { i ->
            if (i == 0) {
                val value = decode83(hash, 2, 6) ?: return null
                floatArrayOf(
                    srgbToLinear(value shr 16),
                    srgbToLinear((value shr 8) and 255),
                    srgbToLinear(value and 255),
                )
            } else {
                val value = decode83(hash, 4 + i * 2, 6 + i * 2) ?: return null
                floatArrayOf(
                    signPow(((value / (19 * 19)) - 9) / 9f) * maxValue,
                    signPow(((value / 19) % 19 - 9) / 9f) * maxValue,
                    signPow((value % 19 - 9) / 9f) * maxValue,
                )
            }
        }

    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            var r = 0f
            var g = 0f
            var b = 0f
            for (j in 0 until numY) {
                for (i in 0 until numX) {
                    val basis = (cos(PI * x * i / width) * cos(PI * y * j / height)).toFloat()
                    val color = colors[i + j * numX]
                    r += color[0] * basis
                    g += color[1] * basis
                    b += color[2] * basis
                }
            }
            pixels[x + y * width] =
                (0xFF shl 24) or (linearToSrgb(r) shl 16) or (linearToSrgb(g) shl 8) or linearToSrgb(b)
        }
    }
    return pixels
}

private const val BASE83 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"

private fun decode83(
    text: String,
    from: Int,
    to: Int,
): Int? {
    var value = 0
    for (index in from until to) {
        val digit = BASE83.indexOf(text[index])
        if (digit < 0) return null
        value = value * 83 + digit
    }
    return value
}

private fun srgbToLinear(value: Int): Float {
    val v = value / 255f
    return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
}

private fun linearToSrgb(value: Float): Int {
    val v = value.coerceIn(0f, 1f)
    val srgb = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1 / 2.4f) - 0.055f
    return (srgb * 255 + 0.5f).toInt().coerceIn(0, 255)
}

private fun signPow(value: Float): Float = (value * value).withSign(value)
