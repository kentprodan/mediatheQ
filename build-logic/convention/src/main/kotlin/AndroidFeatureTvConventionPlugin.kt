import io.github.kentprodan.mediatheq.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * A `:feature-tv:*` module (ADR 0038) with the dependencies it may use: the shared core, the TV design system and TV UI
 * helpers. Never the phone's `:core:designsystem`, `:core:ui` or `:feature:*` (checked on every build).
 */
class AndroidFeatureTvConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("mediatheq.android.library.compose")
            pluginManager.apply("mediatheq.android.hilt")
            pluginManager.apply("mediatheq.android.tv")
            dependencies {
                listOf("model", "common", "navigation", "data", "playback", "designsystem-tv", "ui-tv").forEach {
                    add("implementation", project(":core:$it"))
                }
                add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
                add("implementation", libs.findLibrary("androidx-hilt-lifecycle-viewmodel-compose").get())
                add("testImplementation", project(":core:testing"))
            }
        }
}
