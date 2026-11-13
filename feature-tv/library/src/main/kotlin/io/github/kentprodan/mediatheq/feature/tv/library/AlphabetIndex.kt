package io.github.kentprodan.mediatheq.feature.tv.library

import java.text.Normalizer

/**
 * The A-Z strip of a list sorted by name (#237): each letter the [names] start with, at the index of its first name.
 * Accents do not count ("Élan" is under E, like the name sort reads it); a name starting with anything but a letter
 * is under '#'. A letter seen again later (the sort ignores case and accents) keeps its first index.
 */
fun alphabetIndex(names: List<String>): List<Letter> {
    val seen = mutableSetOf<Char>()
    return names.mapIndexedNotNull { index, name -> initial(name).takeIf(seen::add)?.let { Letter(it, index) } }
}

private fun initial(name: String): Char {
    val first = name.trimStart().firstOrNull() ?: return '#'
    val upper = first.uppercaseChar()
    val base = UNDECOMPOSED[upper] ?: Normalizer.normalize(upper.toString(), Normalizer.Form.NFD).first()
    return if (base in 'A'..'Z') base else '#'
}

/** Letters NFD leaves whole that the name sort still files under a Latin letter. */
private val UNDECOMPOSED =
    mapOf('Ø' to 'O', 'Œ' to 'O', 'Æ' to 'A', 'Ł' to 'L', 'Đ' to 'D', 'Ð' to 'D', 'Þ' to 'T', 'ß' to 'S')
