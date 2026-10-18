package io.github.kentprodan.mediatheq.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Compose compiler and the Compose BOM, shared by the app and Compose library modules. */
internal fun Project.configureCompose(android: CommonExtension) {
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
    android.buildFeatures.compose = true
    dependencies {
        val bom = platform(libs.findLibrary("androidx-compose-bom").get())
        add("implementation", bom)
        add("implementation", libs.findLibrary("androidx-compose-foundation").get())
        add("implementation", libs.findLibrary("androidx-compose-ui").get())
        add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
    }
}
