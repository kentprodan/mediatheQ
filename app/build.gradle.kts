import io.github.kentprodan.mediatheq.buildlogic.CollectLicenseNoticesTask
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.type.ArtifactTypeDefinition

plugins {
    alias(libs.plugins.mediatheq.android.application)
    alias(libs.plugins.mediatheq.android.hilt)
    alias(libs.plugins.mediatheq.android.screenshot)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries.android)
}

// The open-source licenses screen (#70, ADR 0022): built without network, from what the app ships.
aboutLibraries {
    offlineMode = true
    // The standard texts of the licenses in use: offline the plugin has only their names.
    collect.configPath = file("aboutlibraries")
    // A new kind of license stops the build: add its text above, after checking it is compatible with GPL-3.0.
    license {
        strictMode = com.mikepenz.aboutlibraries.plugin.StrictMode.FAIL
        allowedLicenses.addAll("Apache-2.0", "BSD-3-Clause", "MIT", "GPL-3.0-or-later")
        // The app's own license ships with it too (GPL-3.0 section 4), from LICENSE (a test keeps them equal).
        additionalLicenses.add("GPL-3.0-or-later")
    }
}

android {
    namespace = "io.github.kentprodan.mediatheq"

    defaultConfig {
        applicationId = "io.github.kentprodan.mediatheq"
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

// NOTICE files of the shipped libraries, for the licenses screen (#87, ADR 0022): AboutLibraries does not carry them.
androidComponents {
    onVariants { variant ->
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(projects.core.designsystem)
    implementation(projects.core.navigation)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.playback)
    implementation(projects.core.download)
    implementation(projects.core.server)
    implementation(projects.core.network)
    implementation(projects.provider.jellyfin)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.library)
    implementation(projects.feature.album)
    implementation(projects.feature.artist)
    implementation(projects.feature.nowplaying)
    implementation(projects.feature.playlist)
    implementation(projects.feature.search)
    implementation(libs.androidx.lifecycle.process)
    implementation(projects.feature.downloads)
    implementation(projects.feature.home)
    implementation(projects.feature.settings)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    testImplementation(projects.core.testing)
    testImplementation(projects.core.ui)
    testImplementation(libs.kotlinx.serialization.json)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.androidx.media3.session)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}

// LicensesReportTest checks what the release build ships, also when the unit tests run on debug (#70).
tasks.withType<Test>().configureEach {
    val report = layout.buildDirectory.file("generated/aboutLibraries/release/res/raw/aboutlibraries.json")
    val license = rootProject.file("LICENSE")
    val notices = layout.buildDirectory.file("generated/res/collectReleaseLicenseNotices/raw/license_notices.json")
    dependsOn("prepareLibraryDefinitionsRelease", "collectReleaseLicenseNotices")
    inputs.file(report).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(notices).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(license).withPathSensitivity(PathSensitivity.RELATIVE)
    // Relative to the module, the tests' working directory: absolute paths would tie the build cache to this machine.
    systemProperty(
        "mediatheq.licensesReport",
        report
            .get()
            .asFile
            .relativeTo(projectDir)
            .invariantSeparatorsPath,
    )
    systemProperty("mediatheq.license", license.relativeTo(projectDir).invariantSeparatorsPath)
    systemProperty(
        "mediatheq.licenseNotices",
        notices
            .get()
            .asFile
            .relativeTo(projectDir)
            .invariantSeparatorsPath,
    )
}
