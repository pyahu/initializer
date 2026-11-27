package io.pyahu.quality;

/** How one task ended. {@code current} is false for a run recorded by an earlier Gradle invocation. */
record TaskRun(String path, String outcome, long startedAt, long finishedAt, String message, boolean current) {

    static final String PASSED = "passed";
    static final String UP_TO_DATE = "up-to-date";
    static final String SKIPPED = "skipped";
    static final String FAILED = "failed";

    boolean failed() {
        return FAILED.equals(outcome);
    }

    /**
     * Whether this run's output is in the file. An up-to-date task kept its earlier output, which still holds; a task
     * that ran rewrote it, so a file older than the run is left over from another one, such as before a --tests filter.
     */
    boolean wrote(java.nio.file.Path file) {
        try {
            return UP_TO_DATE.equals(outcome) || java.nio.file.Files.getLastModifiedTime(file).toMillis() >= startedAt - 1000;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    boolean succeeded() {
        return PASSED.equals(outcome) || UP_TO_DATE.equals(outcome);
    }
}
