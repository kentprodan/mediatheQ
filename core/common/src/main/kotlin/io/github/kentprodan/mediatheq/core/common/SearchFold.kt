package io.github.kentprodan.mediatheq.core.common

import java.text.Normalizer

private val MARKS = Regex("\\p{Mn}+")

/** [text] lowercased without diacritics, for in-list search: "Moșoi" and "mosoi" match. */
fun foldForSearch(text: String): String = MARKS.replace(Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD), "")
