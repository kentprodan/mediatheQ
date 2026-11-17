package io.github.kentprodan.mediatheq.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/** Compose compiler and the Compose BOM, shared by the app and Compose library modules. */
internal fun Project.configureCompose(android: CommonExtension) {
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
    // The shared models are compiled without Compose; this file lists the immutable ones as stable (#505).
    extensions.configure<ComposeCompilerGradlePluginExtension> {
        stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose-stability.conf"))
        // `-PcomposeReports=true` writes the compiler's stability and skippability reports to build/compose.
        if (providers.gradleProperty("composeReports").orNull == "true") {
            reportsDestination.set(layout.buildDirectory.dir("compose/reports"))
            metricsDestination.set(layout.buildDirectory.dir("compose/metrics"))
        }
    }
    android.buildFeatures.compose = true
    android.testOptions.unitTests.isIncludeAndroidResources = true
    // Robolectric reads FileDescriptor internals, closed by default on JDK 17+.
    android.testOptions.unitTests.all { it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED") }
    dependencies {
        val bom = platform(libs.findLibrary("androidx-compose-bom").get())
        add("implementation", bom)
        add("implementation", libs.findLibrary("androidx-compose-foundation").get())
        add("implementation", libs.findLibrary("androidx-compose-ui").get())
        add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
        // Compose UI behavior tests on Robolectric (testing skill, section 7).
        add("testImplementation", bom)
        add("testImplementation", libs.findLibrary("androidx-compose-ui-test-junit4").get())
        add("testImplementation", libs.findLibrary("robolectric").get())
        // ui-test brings espresso-core 3.5, which crashes on SDK 37 (InputManager.getInstance removed).
        add("testImplementation", libs.findLibrary("androidx-test-espresso-core").get())
        // Hosts createComposeRule's activity; also for unit tests so release unit tests resolve it too.
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-test-manifest").get())
        add("testImplementation", libs.findLibrary("androidx-compose-ui-test-manifest").get())
    }
}
