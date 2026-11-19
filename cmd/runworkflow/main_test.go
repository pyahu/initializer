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

func TestGradleNetworkRetriesAreBoundedAndNeverRetryTestFailures(t *testing.T) {
	script, err := filepath.Abs("../../.github/scripts/gradle-ci.sh")
	if err != nil {
		t.Fatal(err)
	}
	for _, tc := range []struct {
		name, mode, pom string
		attempts        string
		pass            bool
	}{
		{"429 recovers", "transient", "404", "2", true},
		{"existing artifact recovers", "missing", "200", "2", true},
		{"rate limited probe recovers", "missing", "429", "2", true},
		{"unavailable probe recovers", "missing", "503", "2", true},
		{"probe connection failure recovers", "missing", "000", "2", true},
		{"real missing artifact fails", "missing", "404", "1", false},
		{"test failure is final", "tests", "200", "1", false},
		{"compiler failure is final", "compile", "200", "1", false},
		{"network retry budget", "always", "404", "3", false},
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
  tests|compile)
    echo "Could not resolve all artifacts for configuration 'classpath'"
    echo 'Received status code 429 from server: Too Many Requests'
    if [ "$FAKE_GRADLE_MODE" = tests ]; then echo 'There were failing tests'; else echo 'Compilation failed'; fi
    exit 21;;
esac
if [ "$n" -gt 1 ] && [ "$FAKE_GRADLE_MODE" != always ]; then echo 'BUILD SUCCESSFUL'; exit 0; fi
echo "Could not resolve all artifacts for configuration 'classpath'"
if [ "$FAKE_GRADLE_MODE" = missing ]; then
  echo 'Could not find example:artifact:1.0'
  echo 'https://repo.maven.apache.org/maven2/example/artifact/1.0/artifact-1.0.pom'
else
  echo 'Received status code 429 from server: Too Many Requests'
fi
exit 21
`)
			write("curl", "#!/bin/sh\nprintf '%s' \"$FAKE_POM_STATUS\"\n")
			cmd := exec.Command("bash", script, fake, "a task with spaces")
			cmd.Dir = dir
			cmd.Env = append(os.Environ(), "PATH="+dir+string(os.PathListSeparator)+os.Getenv("PATH"),
				"GRADLE_CI_RETRY_DELAY_SECONDS=0", "FAKE_GRADLE_MODE="+tc.mode, "FAKE_POM_STATUS="+tc.pom,
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
