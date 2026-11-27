package io.pyahu.quality;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.logging.StandardOutputListener;

/**
 * Gradle reports a failed compile as "Compilation failed; see the compiler output below": the errors only reach
 * the console. This keeps a copy of what the compiler printed, so the report can say which line broke and why.
 */
final class CompilerOutput implements Action<Task> {

    private final Path file;

    CompilerOutput(Path file) {
        this.file = file;
    }

    static Path fileFor(Path buildDir, String taskName) {
        return buildDir.resolve("reports/pyahu/compiler/" + taskName + ".log");
    }

    @Override
    public void execute(Task task) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "", StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Without the copy the report names the failed task only; the compile itself must still run.
            task.getLogger().warn("Quality report: compiler output not captured: {}", e.toString());
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // The report checks the file's time too, so an old copy is not read as this run's.
            }
            return;
        }
        StandardOutputListener copy = text -> {
            try {
                Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            } catch (IOException e) {
                // A lost copy only makes the report shorter.
            }
        };
        task.getLogging().addStandardErrorListener(copy);
        task.getLogging().addStandardOutputListener(copy);
    }
}
