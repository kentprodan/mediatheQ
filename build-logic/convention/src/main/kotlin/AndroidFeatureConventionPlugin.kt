import org.gradle.api.Plugin
import org.gradle.api.Project

/** A `:feature:*` module. Allowed `:core:*` dependencies are added once those modules exist (#3). */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("mediatheq.android.library.compose")
            pluginManager.apply("mediatheq.android.hilt")
        }
}
