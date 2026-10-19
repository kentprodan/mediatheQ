import com.android.build.api.dsl.CommonExtension
import io.github.kentprodan.mediatheq.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/** Compose Preview Screenshot Testing, standalone plugin (ADR 0007, testing skill section 8). */
class AndroidScreenshotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.compose.screenshot")
            extensions.getByType<CommonExtension>().apply {
                experimentalProperties["android.experimental.enableScreenshotTest"] = true
            }
            dependencies {
                add("screenshotTestImplementation", libs.findLibrary("screenshot-validation-api").get())
                add("screenshotTestImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
            }
        }
    }
}
