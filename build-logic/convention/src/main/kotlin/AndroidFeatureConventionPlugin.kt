import io.github.kentprodan.mediatheq.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** A `:feature:*` module with the `:core:*` dependencies the architecture skill allows (section 2). */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("mediatheq.android.library.compose")
            pluginManager.apply("mediatheq.android.hilt")
            dependencies {
                listOf("model", "common", "navigation", "data", "designsystem", "ui").forEach {
                    add("implementation", project(":core:$it"))
                }
                add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
                add("implementation", libs.findLibrary("androidx-hilt-lifecycle-viewmodel-compose").get())
                add("testImplementation", project(":core:testing"))
            }
        }
}
