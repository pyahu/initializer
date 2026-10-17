// Package harness runs containers for the acceptance tests with the Docker CLI only.
package harness

import (
	"bytes"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"strings"
	"testing"
	"time"

	"github.com/pyahu/initializer/internal/guidedata"
)

// FixtureImage is the image under test (FIXTURE_IMAGE, default single-hexagonal:ci).
func FixtureImage() string {
	if v := os.Getenv("FIXTURE_IMAGE"); v != "" {
		return v
	}
	return "single-hexagonal:ci"
}

// ImageRef returns repository@digest for an image component of the fixture's baseline.
func ImageRef(t *testing.T, key string) string {
	t.Helper()
	root, err := guidedata.FindRoot(".")
	if err != nil {
		t.Fatal(err)
	}
	v, err := guidedata.LoadYAMLFile(root + "/data/versions.yaml")
	if err != nil {
		t.Fatal(err)
	}
	var vs guidedata.Versions
	if err := guidedata.Decode(v, &vs); err != nil {
		t.Fatal(err)
	}
	rev := vs.Revision(vs.Active)
	c, ok := rev.Components[key]
	if !ok || c.Kind != "image" {
		t.Fatalf("%s is not an image component of %s", key, vs.Active)
	}
	return c.Image[:strings.LastIndex(c.Image, ":")] + "@" + c.Digest
}

// Docker runs docker and returns combined output.
func Docker(t *testing.T, args ...string) (string, error) {
	t.Helper()
	cmd := exec.Command("docker", args...)
	var out bytes.Buffer
	cmd.Stdout, cmd.Stderr = &out, &out
	err := cmd.Run()
	return out.String(), err
}

// MustDocker is Docker that fails the test on error.
func MustDocker(t *testing.T, args ...string) string {
	t.Helper()
	out, err := Docker(t, args...)
	if err != nil {
		t.Fatalf("docker %s: %v\n%s", strings.Join(args, " "), err, out)
	}
	return out
}

// Name returns a unique container or network name.
func Name(prefix string) string {
	b := make([]byte, 4)
	_, _ = rand.Read(b)
	return prefix + "-" + hex.EncodeToString(b)
}

// RandomHex returns n random bytes as hex.
func RandomHex(n int) string {
	b := make([]byte, n)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}

// Start runs a detached container and removes it when the test ends.
func Start(t *testing.T, name string, args ...string) {
	t.Helper()
	MustDocker(t, append([]string{"run", "-d", "--name", name}, args...)...)
	t.Cleanup(func() {
		if t.Failed() {
			logs, _ := Docker(t, "logs", "--tail", "60", name)
			t.Logf("last logs of %s:\n%s", name, logs)
		}
		_, _ = Docker(t, "rm", "-f", "-v", name)
	})
}

// Port returns the host address of a published container port.
func Port(t *testing.T, name, port string) string {
	t.Helper()
	out := MustDocker(t, "port", name, port)
	line := strings.TrimSpace(strings.Split(strings.TrimSpace(out), "\n")[0])
	return strings.Replace(line, "0.0.0.0", "127.0.0.1", 1)
}

// Network creates a Docker network removed at the end of the test.
func Network(t *testing.T) string {
	t.Helper()
	n := Name("acc-net")
	MustDocker(t, "network", "create", n)
	t.Cleanup(func() { _, _ = Docker(t, "network", "rm", n) })
	return n
}

// WaitHTTP polls url until it returns 200 or the timeout passes; it returns the elapsed time.
func WaitHTTP(t *testing.T, url string, every, timeout time.Duration) time.Duration {
	t.Helper()
	start := time.Now()
	c := &http.Client{Timeout: 2 * time.Second}
	for time.Since(start) < timeout {
		resp, err := c.Get(url)
		if err == nil {
			_, _ = io.Copy(io.Discard, resp.Body)
			resp.Body.Close()
			if resp.StatusCode == 200 {
				return time.Since(start)
			}
		}
		time.Sleep(every)
	}
	t.Fatalf("%s did not return 200 within %s", url, timeout)
	return 0
}

// WaitLog polls a container's logs until they contain s.
func WaitLog(t *testing.T, name, s string, timeout time.Duration) string {
	t.Helper()
	start := time.Now()
	for time.Since(start) < timeout {
		out, _ := Docker(t, "logs", name)
		if strings.Contains(out, s) {
			return out
		}
		time.Sleep(500 * time.Millisecond)
	}
	t.Fatalf("container %s did not log %q within %s", name, s, timeout)
	return ""
}

// Result records the decisive output for the evidence report.
func Result(t *testing.T, format string, a ...any) {
	t.Helper()
	t.Logf("RESULT: %s", fmt.Sprintf(format, a...))
}

// RequireResources fails unless Docker offers at least cpus CPUs and memGiB of memory.
func RequireResources(t *testing.T, cpus int, memGiB float64) {
	t.Helper()
	out := strings.TrimSpace(MustDocker(t, "info", "--format", "{{.NCPU}} {{.MemTotal}}"))
	var n int
	var mem int64
	if _, err := fmt.Sscanf(out, "%d %d", &n, &mem); err != nil {
		t.Fatalf("docker info: %q", out)
	}
	if n < cpus || float64(mem) < memGiB*(1<<30)*0.95 {
		t.Fatalf("needs %d CPUs and %.0f GiB for Docker, has %d CPUs and %.1f GiB", cpus, memGiB, n, float64(mem)/(1<<30))
	}
}
