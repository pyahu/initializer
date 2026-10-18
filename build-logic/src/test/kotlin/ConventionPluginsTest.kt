import java.io.File
import kotlin.io.path.createTempDirectory
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Applies each convention plugin to an empty project and checks the tasks it promises (monorepo guide, step 4). */
class ConventionPluginsTest {

    private fun tasksOf(plugin: String): String {
        val dir = createTempDirectory("conventions").toFile()
        File(dir, "settings.gradle.kts").writeText(
            """
            dependencyResolutionManagement {
                versionCatalogs { create("libs") { from(files("${File("../gradle/libs.versions.toml").absolutePath}")) } }
            }
            rootProject.name = "probe"
            """.trimIndent(),
        )
        File(dir, "build.gradle.kts").writeText("plugins { id(\"$plugin\") }\n")
        return GradleRunner.create()
            .withProjectDir(dir)
            .withPluginClasspath()
            .withArguments("tasks", "--all")
            .build()
            .output
    }

    @Test
    fun `java conventions add formatting checks`() {
        val out = tasksOf("pyahu.java-conventions")
        assertTrue("spotlessCheck" in out, out)
    }

    @Test
    fun `a service builds a Boot jar and an SBOM`() {
        val out = tasksOf("pyahu.spring-boot-service")
        assertTrue("bootJar" in out, out)
        assertTrue("cyclonedxBom" in out, out)
    }
}
