package io.github.kentprodan.mediatheq.core.database

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.kentprodan.mediatheq.core.common.DEFAULT_DATABASE
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Room database files, one per account (#147, ADR 0036). The process opens one, chosen at start (SharedPreferences,
 * read before the database opens, like ADR 0020): switching accounts writes the other name and restarts the app.
 */
interface AccountDatabases {
    /** The database this process opened. */
    val open: String

    /** Opens [name] from the next start on; `false` when it could not be written. */
    fun select(name: String): Boolean

    /** A file name for an account added while another one is active. */
    fun newName(): String

    /** Deletes [name] with its journal files; never the open one. */
    fun delete(name: String)

    /** The account database files on disk. */
    fun names(): List<String>
}

@Singleton
internal class SharedPreferencesAccountDatabases
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : AccountDatabases {
        private val preferences = context.getSharedPreferences("accounts", Context.MODE_PRIVATE)

        override val open: String = preferences.getString(KEY, null) ?: DEFAULT_DATABASE

        override fun select(name: String): Boolean = preferences.edit().putString(KEY, name).commit()

        override fun newName(): String = "mediatheq-${UUID.randomUUID()}.db"

        override fun delete(name: String) {
            if (name != open && isAccountDatabase(name)) context.deleteDatabase(name)
        }

        override fun names(): List<String> = context.databaseList().filter(::isAccountDatabase)

        private fun isAccountDatabase(name: String) = name == DEFAULT_DATABASE || NAME.matches(name)

        private companion object {
            const val KEY = "database"
            val NAME = Regex("mediatheq-[0-9a-f-]{36}\\.db")
        }
    }
