package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.common.DEFAULT_DATABASE
import io.github.kentprodan.mediatheq.core.database.AccountDatabases

/** [AccountDatabases] in memory: [files] are the database files "on disk", [selected] what the next start opens. */
class FakeAccountDatabases(
    override val open: String = DEFAULT_DATABASE,
    vararg files: String = arrayOf(open),
) : AccountDatabases {
    val files = files.toMutableSet()
    var selected: String? = null
    var selectWorks = true
    private var next = 0

    override fun select(name: String): Boolean {
        if (selectWorks) selected = name
        return selectWorks
    }

    override fun newName(): String = "mediatheq-${++next}.db"

    override fun delete(name: String) {
        if (name != open) files -= name
    }

    override fun names(): List<String> = files.toList()
}
