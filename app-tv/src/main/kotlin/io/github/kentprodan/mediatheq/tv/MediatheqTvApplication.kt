package io.github.kentprodan.mediatheq.tv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** The TV app (ADR 0038); the shared core is wired here by the issues that first need it. */
@HiltAndroidApp
class MediatheqTvApplication : Application()
