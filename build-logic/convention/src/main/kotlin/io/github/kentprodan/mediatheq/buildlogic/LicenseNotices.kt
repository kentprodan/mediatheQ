package io.github.kentprodan.mediatheq.buildlogic

import com.android.build.api.variant.Variant
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.kotlin.dsl.register

/**
 * Puts the NOTICE files of [variant]'s shipped libraries into `R.raw.license_notices` (#87, ADR 0022): AboutLibraries
 * does not carry them. Used by both apps (#243).
 */
fun Project.collectLicenseNotices(variant: Variant) {
    val libraryJars =
        variant.runtimeConfiguration.incoming.artifactView {
            attributes { attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "android-classes-jar") }
            // External libraries only: the project's own modules have no NOTICE, and need not be built first.
            componentFilter { it is ModuleComponentIdentifier }
        }
    val notices =
        tasks.register<CollectLicenseNoticesTask>(
            "collect${variant.name.replaceFirstChar(Char::uppercase)}LicenseNotices",
        ) {
            jars.from(libraryJars.files)
            libraries.set(
                libraryJars.artifacts.resolvedArtifacts.map { artifacts ->
                    artifacts.associate { artifact ->
                        val library = artifact.id.componentIdentifier as ModuleComponentIdentifier
                        artifact.file.absolutePath to "${library.group}:${library.module}"
                    }
                },
            )
        }
    variant.sources.res?.addGeneratedSourceDirectory(notices, CollectLicenseNoticesTask::outputDirectory)
}
