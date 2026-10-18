import com.android.build.api.dsl.ApplicationExtension
import io.github.kentprodan.mediatheq.buildlogic.configureAndroid
import io.github.kentprodan.mediatheq.buildlogic.configureCompose
import io.github.kentprodan.mediatheq.buildlogic.intVersion
import io.github.kentprodan.mediatheq.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            configureAndroid(this)
            configureCompose(this)
            defaultConfig.targetSdk = libs.intVersion("targetSdk")
        }
    }
}
