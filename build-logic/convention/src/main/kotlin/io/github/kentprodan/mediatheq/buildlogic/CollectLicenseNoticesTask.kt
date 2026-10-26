package io.github.kentprodan.mediatheq.buildlogic

import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

/**
 * Writes `raw/license_notices.json`: the NOTICE files the shipped libraries carry (Apache-2.0 section 4(d)), as
 * `{"group:name": text}`, for the open-source licenses screen (#87, ADR 0022).
 */
@CacheableTask
abstract class CollectLicenseNoticesTask : DefaultTask() {
    /** The class jars of the shipped external libraries. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val jars: ConfigurableFileCollection

    /** Each jar's library, by the jar's absolute path (not an input: paths differ per machine; [libraryIds] is). */
    @get:Internal
    abstract val libraries: MapProperty<String, String>

    /** The libraries, for the cache key. */
    @get:Input
    val libraryIds: Provider<List<String>> = libraries.map { it.values.sorted() }

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun collect() {
        val notices = sortedMapOf<String, String>()
        libraries.get().forEach { (path, library) ->
            val texts =
                ZipFile(path).use { zip ->
                    zip
                        .entries()
                        .asSequence()
                        .filter { NOTICE.matches(it.name) }
                        .map { zip.getInputStream(it).bufferedReader().use { reader -> reader.readText().trim() } }
                        // The same text twice (NOTICE and NOTICE.txt) shows once.
                        .distinct()
                        .toList()
                }
            if (texts.isNotEmpty()) notices[library] = texts.joinToString("\n\n")
        }
        val raw = outputDirectory.dir("raw").get().asFile
        raw.mkdirs()
        raw.resolve("license_notices.json").writeText(JsonOutput.toJson(notices))
    }

    private companion object {
        // NOTICE, NOTICE.txt, NOTICE.md, at the root or in META-INF, any case.
        val NOTICE = Regex("(?i)(META-INF/)?NOTICE(\\.[a-z]+)?")
    }
}
