import com.android.build.api.dsl.TestExtension
import io.github.kentprodan.mediatheq.buildlogic.configureSdkAndToolchain
import io.github.kentprodan.mediatheq.buildlogic.intVersion
import io.github.kentprodan.mediatheq.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** A `com.android.test` module (e.g. `:baselineprofile`): SDK levels and toolchain, no unit-test setup. */
class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.android.test")
            extensions.configure<TestExtension> {
                configureSdkAndToolchain(this)
                defaultConfig.targetSdk = libs.intVersion("targetSdk")
            }
        }
}
