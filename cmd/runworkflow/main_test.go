package main

import (
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
)

func TestDependencyCacheNeverSkipsTheBuildOrHidesItsFailure(t *testing.T) {
	for _, fail := range []bool{false, true} {
		dir := t.TempDir()
		command := "printf built > build-marker"
		if fail {
			command = "exit 23"
		}
		workflow := "jobs:\n  build:\n    steps:\n      - uses: actions/cache@pinned\n        with:\n          key: ${{ runner.os }}-dependencies\n          path: ~/.cache/pyahu-gradle\n      - run: " + command + "\n"
		if err := os.WriteFile(filepath.Join(dir, "ci.yml"), []byte(workflow), 0o644); err != nil {
			t.Fatal(err)
		}
		err := run(dir, "ci.yml", "build")
		if fail {
			if err == nil || !strings.Contains(err.Error(), "exit status 23") {
				t.Fatalf("build failure lost: %v", err)
			}
		} else if content, readErr := os.ReadFile(filepath.Join(dir, "build-marker")); err != nil || readErr != nil || string(content) != "built" {
			t.Fatalf("build skipped: %v %v %q", err, readErr, content)
		}
	}
}

func TestReportUploadIsSkippedAndLaterStepsStillRun(t *testing.T) {
	dir := t.TempDir()
	workflow := "jobs:\n  build:\n    steps:\n      - uses: actions/upload-artifact@pinned\n        with:\n          name: reports\n          path: build/reports/\n      - run: printf built > build-marker\n"
	if err := os.WriteFile(filepath.Join(dir, "ci.yml"), []byte(workflow), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := run(dir, "ci.yml", "build"); err != nil {
		t.Fatal(err)
	}
	if content, err := os.ReadFile(filepath.Join(dir, "build-marker")); err != nil || string(content) != "built" {
		t.Fatalf("the step after the upload did not run: %v %q", err, content)
	}
}

func TestGradleNetworkRetriesAreBoundedAndNeverRetryTestFailures(t *testing.T) {
	script, err := filepath.Abs("../../.github/scripts/gradle-ci.sh")
	if err != nil {
		t.Fatal(err)
	}
	for _, tc := range []struct {
		name, mode string
		attempts   string
		pass       bool
	}{
		{"429 recovers", "transient", "2", true},
		{"negative Maven metadata recovers", "missing", "2", true},
		{"real missing artifact still fails", "missing-permanent", "2", false},
		{"private missing artifact is final", "private-missing", "1", false},
		{"test failure is final", "tests", "1", false},
		{"compiler failure is final", "compile", "1", false},
		{"formatting failure is final", "format", "1", false},
		{"network retry budget", "always", "3", false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			dir := t.TempDir()
			write := func(name, content string) string {
				t.Helper()
				path := filepath.Join(dir, name)
				if err := os.WriteFile(path, []byte(content), 0o755); err != nil {
					t.Fatal(err)
				}
				return path
			}
			fake := write("fake-gradle", `#!/usr/bin/env bash
set -eu
n=0
if [ -f "$FAKE_GRADLE_COUNT" ]; then read -r n < "$FAKE_GRADLE_COUNT"; fi
n=$((n + 1))
printf '%s\n' "$n" > "$FAKE_GRADLE_COUNT"
printf '%s\n' "$@" >> "$FAKE_GRADLE_ARGS"
case "$FAKE_GRADLE_MODE" in
  tests|compile|format)
    echo "Could not resolve all artifacts for configuration 'classpath'"
    echo 'Received status code 429 from server: Too Many Requests'
    case "$FAKE_GRADLE_MODE" in
      tests) echo 'There were failing tests';;
      compile) echo 'Compilation failed';;
      format) echo 'format violations';;
    esac
    exit 21;;
esac
if [ "$n" -gt 1 ] && [ "$FAKE_GRADLE_MODE" != always ] && [ "$FAKE_GRADLE_MODE" != missing-permanent ]; then echo 'BUILD SUCCESSFUL'; exit 0; fi
echo "Could not resolve all artifacts for configuration 'classpath'"
if [ "$FAKE_GRADLE_MODE" = missing ] || [ "$FAKE_GRADLE_MODE" = missing-permanent ] || [ "$FAKE_GRADLE_MODE" = private-missing ]; then
  echo 'Could not find example:artifact:1.0'
  if [ "$FAKE_GRADLE_MODE" = private-missing ]; then
    echo 'https://private.example/example/artifact/1.0/artifact-1.0.pom'
  else
    echo 'https://repo.maven.apache.org/maven2/example/artifact/1.0/artifact-1.0.pom'
  fi
else
  echo 'Received status code 429 from server: Too Many Requests'
fi
exit 21
`)
			cmd := exec.Command("bash", script, fake, "a task with spaces")
			cmd.Dir = dir
			cmd.Env = append(os.Environ(), "PATH="+dir+string(os.PathListSeparator)+os.Getenv("PATH"),
				"GRADLE_CI_RETRY_DELAY_SECONDS=0", "FAKE_GRADLE_MODE="+tc.mode,
				"FAKE_GRADLE_COUNT="+filepath.Join(dir, "count"), "FAKE_GRADLE_ARGS="+filepath.Join(dir, "args"))
			out, err := cmd.CombinedOutput()
			if (err == nil) != tc.pass {
				t.Fatalf("pass=%v, error=%v\n%s", tc.pass, err, out)
			}
			count, _ := os.ReadFile(filepath.Join(dir, "count"))
			if strings.TrimSpace(string(count)) != tc.attempts {
				t.Fatalf("unexpected attempt count %q\n%s", count, out)
			}
			args, _ := os.ReadFile(filepath.Join(dir, "args"))
			if !strings.Contains(string(args), "a task with spaces\n") || (tc.attempts != "1" && !strings.Contains(string(args), "--refresh-dependencies")) {
				t.Fatalf("command arguments or metadata refresh lost: %s", args)
			}
		})
	}
}

func TestDistributedGradleRetryHelpersMatch(t *testing.T) {
	want, err := os.ReadFile("../../.github/scripts/gradle-ci.sh")
	if err != nil {
		t.Fatal(err)
	}
	for _, path := range []string{
		"../../templates/base/.github/scripts/gradle-ci.sh",
		"../../fixtures/single-hexagonal/.github/scripts/gradle-ci.sh",
	} {
		got, err := os.ReadFile(path)
		if err != nil || string(got) != string(want) {
			t.Fatalf("retry behavior differs in %s: %v", path, err)
		}
	}
}
