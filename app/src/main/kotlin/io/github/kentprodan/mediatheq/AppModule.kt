package io.github.kentprodan.mediatheq

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.kentprodan.mediatheq.core.common.AppLog
import io.github.kentprodan.mediatheq.core.common.ApplicationScope
import io.github.kentprodan.mediatheq.core.common.DefaultDispatcher
import io.github.kentprodan.mediatheq.core.common.FileAppLog
import io.github.kentprodan.mediatheq.core.common.IoDispatcher
import io.github.kentprodan.mediatheq.core.server.ClientInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Singleton
import kotlin.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    /** Work that outlives screens (library sync). SupervisorJob: one failure does not stop the others. */
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(log: AppLog): CoroutineScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default +
                // A bug in background work is logged (on device only), not a crash; callers handle expected failures.
                CoroutineExceptionHandler { _, error -> log.warn("mediatheQ", "Background work failed", error) },
        )

    /**
     * The app's log file (#169) in `files/logs`, written on its own scope (the application scope logs into it). Debug
     * builds also print each line to logcat.
     */
    @Provides
    @Singleton
    fun fileAppLog(
        @ApplicationContext context: Context,
        clock: Clock,
        @IoDispatcher io: CoroutineDispatcher,
    ): FileAppLog {
        val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return FileAppLog(
            File(context.filesDir, "logs"),
            clock,
            CoroutineScope(SupervisorJob()),
            io,
            sink = { level, tag, line -> if (debug) Log.println(if (level == 'I') Log.INFO else Log.WARN, tag, line) },
        )
    }

    @Provides
    fun appLog(log: FileAppLog): AppLog = log

    @Provides
    fun clock(): Clock = Clock.System

    /** How the app introduces itself to servers: name, phone model, version. */
    @Provides
    fun clientInfo(
        @ApplicationContext context: Context,
    ): ClientInfo =
        ClientInfo(
            client = "mediatheQ",
            device = Build.MODEL,
            version =
                context.packageManager
                    .getPackageInfo(context.packageName, 0)
                    .versionName
                    .orEmpty(),
        )
}
