import io.github.kentprodan.mediatheq.buildlogic.configureTvDependencyCheck
import org.gradle.api.Plugin
import org.gradle.api.Project

/** Any TV module (ADR 0037, 0038): the build fails if it ships the phone's UI or material3. */
class AndroidTvConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = target.configureTvDependencyCheck()
}
