package io.github.kentprodan.mediatheq.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** The `libs` version catalog. */
val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** An integer version from the catalog, e.g. `compileSdk`. */
fun VersionCatalog.intVersion(name: String): Int = findVersion(name).get().requiredVersion.toInt()
