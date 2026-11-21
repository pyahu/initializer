package io.pyahu.quality;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification;
import org.gradle.testing.jacoco.tasks.rules.JacocoLimit;
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRule;

/**
 * Records how every task of the build ended and, when the build finishes, writes the quality report to
 * build/reports/pyahu: quality.html for people, quality.json for tools and AI agents, job-summary.md for the
 * GitHub job summary. It writes the report when the build fails too, with the reason on top.
 */
public abstract class QualityReportPlugin implements Plugin<Project> {

    @Inject
    protected abstract BuildEventsListenerRegistry getEventsListenerRegistry();

    @Override
    public void apply(Project project) {
        project.getTasks().withType(JavaCompile.class).configureEach(task -> task.doFirst(new CompilerOutput(
                CompilerOutput.fileFor(project.getLayout().getBuildDirectory().get().getAsFile().toPath(), task.getName()))));
        // The thresholds come from the gates themselves, so the report never disagrees with the build.
        project.afterEvaluate(evaluated -> {
            Provider<TaskRecorder> recorder = project.getGradle()
                    .getSharedServices()
                    .registerIfAbsent("pyahuQualityReport", TaskRecorder.class, spec -> {
                        TaskRecorder.Params p = spec.getParameters();
                        p.getProjectDirectory().set(project.getLayout().getProjectDirectory());
                        p.getBuildDirectory().set(project.getLayout().getBuildDirectory());
                        p.getProjectName().set(project.getName());
                        p.getGradleVersion().set(project.getGradle().getGradleVersion());
                        JavaPluginExtension java = project.getExtensions().findByType(JavaPluginExtension.class);
                        if (java != null && java.getToolchain().getLanguageVersion().isPresent()) {
                            p.getJavaToolchain().set(java.getToolchain().getLanguageVersion().get().toString());
                        }
                        p.getThresholds().putAll(thresholds(project));
                    });
            getEventsListenerRegistry().onTaskCompletion(recorder);
        });
    }

    private static Map<String, String> thresholds(Project project) {
        Map<String, String> thresholds = new LinkedHashMap<>();
        // Iterating realizes the task, so its rules are configured by the time they are read.
        for (JacocoCoverageVerification task : project.getTasks().withType(JacocoCoverageVerification.class)) {
            for (JacocoViolationRule rule : task.getViolationRules().getRules()) {
                for (JacocoLimit limit : rule.getLimits()) {
                    if (limit.getMinimum() != null) {
                        thresholds.put(
                                limit.getCounter().toLowerCase(Locale.ROOT),
                                limit.getMinimum().toPlainString());
                    }
                }
            }
        }
        Object pitest = project.getExtensions().findByName("pitest");
        if (pitest != null) {
            try {
                Method getter = pitest.getClass().getMethod("getMutationThreshold");
                Object value = ((Provider<?>) getter.invoke(pitest)).getOrNull();
                if (value != null) {
                    thresholds.put("mutation", value.toString());
                }
            } catch (ReflectiveOperationException | ClassCastException e) {
                // An older or newer PIT plugin without the property: the report shows the score without a threshold.
            }
        }
        return thresholds;
    }
}
