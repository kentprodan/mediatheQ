package io.github.kentprodan.mediatheq.core.common

import java.text.Collator
import java.util.Locale

/** Compares names like a person reads them: case and diacritics are ignored ("Moșoi" next to "Mosoi"). */
fun nameComparator(): Comparator<String> {
    val collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }
    return Comparator { a, b -> collator.compare(a, b) }
}
