package io.github.kentprodan.mediatheq.core.data

/**
 * The age an official rating is for (#434), `null` when unknown (unrated items are not gated). Covers the US film and
 * TV ratings, plain ages ("12", "16+", "FSK-12", "DE-16", "UK-15") and the common European letters.
 */
fun ratingAge(rating: String?): Int? {
    val r = rating?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
    US[r]?.let { return it }
    // Country prefixes ("DE-16", "FSK 12", "UK:15") and suffixes ("16+") around a plain age.
    return AGE
        .find(r)
        ?.value
        ?.toIntOrNull()
        ?.takeIf { it in 0..21 }
}

private val AGE = Regex("""\d{1,2}""")

/** Whether [rating] is above [limit] (an age; `null` = no limit). */
fun aboveLimit(
    rating: String?,
    limit: Int?,
): Boolean {
    if (limit == null) return false
    val age = ratingAge(rating) ?: return false
    return age > limit
}

private val US =
    mapOf(
        "G" to 0,
        "TV-Y" to 0,
        "TV-G" to 0,
        "U" to 0,
        "APPROVED" to 0,
        "TV-Y7" to 7,
        "TV-Y7-FV" to 7,
        "PG" to 10,
        "TV-PG" to 10,
        "PG-13" to 13,
        "TV-14" to 14,
        "R" to 17,
        "NC-17" to 18,
        "TV-MA" to 17,
        "X" to 18,
        "XXX" to 18,
        "NR" to null,
        "UNRATED" to null,
        "NOT RATED" to null,
    ).filterValues { it != null }.mapValues { it.value!! }
