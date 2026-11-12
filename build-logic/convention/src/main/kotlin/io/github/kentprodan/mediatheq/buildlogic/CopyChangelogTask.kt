package io.github.kentprodan.mediatheq.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Writes `raw/changelog.md`: the repository's CHANGELOG.md, for Settings -> About -> Changelog, read offline (#151). */
@CacheableTask
abstract class CopyChangelogTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val changelog: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun copy() {
        val raw = outputDirectory.get().dir("raw").asFile
        raw.deleteRecursively()
        raw.mkdirs()
        changelog.get().asFile.copyTo(raw.resolve("changelog.md"))
    }
}
