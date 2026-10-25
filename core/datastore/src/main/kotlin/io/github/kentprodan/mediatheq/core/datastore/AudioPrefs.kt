package io.github.kentprodan.mediatheq.core.datastore

import javax.inject.Qualifier

/** The DataStore file holding audio preferences (bit-perfect USB, equalizer). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AudioPrefs
