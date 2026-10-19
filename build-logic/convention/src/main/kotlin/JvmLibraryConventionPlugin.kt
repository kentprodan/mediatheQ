import io.github.kentprodan.mediatheq.buildlogic.configureKotlin
import org.gradle.api.Plugin
import org.gradle.api.Project

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("java-library")
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            configureKotlin()
            // Same task name as Android modules, so `./gradlew testDebugUnitTest` (CI, skills) covers JVM modules too.
            tasks.register("testDebugUnitTest") { dependsOn("test") }
        }
    }
}
