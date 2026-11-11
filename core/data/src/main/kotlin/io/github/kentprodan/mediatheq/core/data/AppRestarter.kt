package io.github.kentprodan.mediatheq.core.data

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Restarts the app, for changes that apply only at start: a storage move (ADR 0035), an account switch (ADR 0036). */
fun interface AppRestarter {
    fun restart()
}

internal class DefaultAppRestarter
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : AppRestarter {
        override fun restart() {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
            context.startActivity(Intent.makeRestartActivityTask(launch.component))
            Runtime.getRuntime().exit(0)
        }
    }
