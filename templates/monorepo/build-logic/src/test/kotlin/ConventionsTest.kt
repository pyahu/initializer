import java.io.File
import kotlin.io.path.createTempDirectory
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Applies the conventions to small builds: each kind of project gets the tasks it promises, and the build stops
 * when a project crosses a boundary (monorepo guide, steps 3 and 5).
 */
class ConventionsTest {

    /** A build with one project per entry, named and built from the given build file. */
    private fun build(vararg projects: Pair<String, String>): File {
        val dir = createTempDirectory("conventions").toFile()
        val catalog = System.getProperty("catalog")
        File(dir, "settings.gradle.kts").writeText(
            """
            dependencyResolutionManagement {
                repositories { mavenCentral() }
                versionCatalogs { create("libs") { from(files("$catalog")) } }
            }
            rootProject.name = "probe"
            """.trimIndent() + "\n" + projects.joinToString("\n") { "include(\":${it.first}\")" },
        )
        for ((name, buildFile) in projects) {
            File(dir, name).mkdirs()
            File(dir, "$name/build.gradle.kts").writeText(buildFile)
        }
        File(dir, "config/pmd").mkdirs()
        File(dir, "config/pmd/ruleset.xml").writeText(
            """<ruleset name="probe" xmlns="http://pmd.sourceforge.net/ruleset/2.0.0"><description>probe</description></ruleset>""",
        )
        return dir
    }

    private fun runner(dir: File, vararg args: String) =
        GradleRunner.create().withProjectDir(dir).withPluginClasspath().withArguments(*args)

    private fun tasksOf(plugin: String, project: String = "probe-project"): String {
        val dir = build(project to "plugins { id(\"$plugin\") }\n")
        return runner(dir, ":$project:tasks", "--all").build().output
    }

    @Test
    fun `a service builds a Boot jar, runs the integration tests and the quality gates`() {
        val out = tasksOf("platform.service")
        for (task in listOf("bootJar", "integrationTest", "pitest", "pmdMain", "spotlessCheck", "cyclonedxBom")) {
            assertTrue(task in out, "$task missing:\n$out")
        }
    }

    @Test
    fun `an api project generates code from its contract and is no application`() {
        val out = tasksOf("platform.api", "orders-api")
        assertTrue("openApiGenerate" in out, out)
        assertFalse("bootJar" in out, out)
    }

    @Test
    fun `a library is plain Java without the Spring Boot plugin`() {
        val out = tasksOf("platform.library", "lib-money")
        assertTrue("spotlessCheck" in out, out)
        assertFalse("bootJar" in out, out)
    }

    @Test
    fun `a service may use another vertical's api project`() {
        val dir = build(
            "orders-api" to "plugins { id(\"platform.api\") }\n",
            "billing" to "plugins { id(\"platform.service\") }\ndependencies { implementation(project(\":orders-api\")) }\n",
        )
        runner(dir, "help").build()
    }

    @Test
    fun `a service that depends on another service stops the build`() {
        val dir = build(
            "orders" to "plugins { id(\"platform.service\") }\n",
            "billing" to "plugins { id(\"platform.service\") }\ndependencies { implementation(project(\":orders\")) }\n",
        )
        val out = runner(dir, "help").buildAndFail().output
        assertTrue(":billing must not depend on :orders" in out, out)
    }

    @Test
    fun `an api project that depends on its service stops the build`() {
        val dir = build(
            "orders" to "plugins { id(\"platform.service\") }\n",
            "orders-api" to "plugins { id(\"platform.api\") }\ndependencies { implementation(project(\":orders\")) }\n",
        )
        val out = runner(dir, "help").buildAndFail().output
        assertTrue(":orders-api must not depend on :orders" in out, out)
    }

    @Test
    fun `a library that depends on a vertical stops the build`() {
        val dir = build(
            "orders-api" to "plugins { id(\"platform.api\") }\n",
            "lib-money" to "plugins { id(\"platform.library\") }\ndependencies { implementation(project(\":orders-api\")) }\n",
        )
        val out = runner(dir, "help").buildAndFail().output
        assertTrue(":lib-money must not depend on :orders-api" in out, out)
    }
}
