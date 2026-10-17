//go:build acceptance

// Package jvm proves the JVM-in-containers claims against the fixture image and the
// baseline JRE.
package jvm

import (
	"fmt"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"testing"
	"time"

	h "github.com/pyahu/initializer/acceptance/harness"
)

func jre(t *testing.T) string { return h.ImageRef(t, "temurin-jre-image") }

func flag(t *testing.T, out, name string) string {
	t.Helper()
	m := regexp.MustCompile(`(?m)^\s*\S+\s+` + name + `\s+=\s+(\S+)`).FindStringSubmatch(out)
	if m == nil {
		t.Fatalf("flag %s not in output:\n%s", name, out)
	}
	return m[1]
}

func flags(t *testing.T, extra ...string) string {
	t.Helper()
	args := append([]string{"run", "--rm"}, extra...)
	return h.MustDocker(t, args...)
}

func TestAOTCacheFailFast(t *testing.T) {
	out := h.MustDocker(t, "run", "--rm", "--entrypoint", "java", h.FixtureImage(),
		"-XX:+UseG1GC", "-XX:AOTMode=on", "-XX:AOTCache=/app/app.aot", "-cp", "/app/application.jar", "-version")
	h.Result(t, "AOTMode=on with the image's GC and class path: exit 0 (%s)", strings.Split(strings.TrimSpace(out), "\n")[1])
}

func TestAOTCacheLoadedOnLaunch(t *testing.T) {
	name := h.Name("aot-launch")
	h.Start(t, name, "-p", "127.0.0.1::8080", "-e", "JDK_JAVA_OPTIONS=-Xlog:aot", h.FixtureImage())
	h.WaitHTTP(t, "http://"+h.Port(t, name, "8080")+"/readyz", 250*time.Millisecond, 60*time.Second)
	logs := h.MustDocker(t, "logs", name)
	// The line HotSpot 25.0.4.1+1 prints when it opens the cache, read from the first run.
	const opened = "Opened AOT cache /app/app.aot."
	if !strings.Contains(logs, opened) {
		t.Fatalf("the production launch did not open the cache; logs:\n%s", logs)
	}
	h.Result(t, "unchanged ENTRYPOINT with -Xlog:aot logs %q", opened)
}

func TestHardenedStartup(t *testing.T) {
	name := h.Name("hardened")
	h.Start(t, name, "--read-only", "--tmpfs", "/tmp", "--user", "10001:10001", "-p", "127.0.0.1::8080",
		"-e", "OTEL_EXPORTER_OTLP_ENDPOINT=http://127.0.0.1:1", h.FixtureImage())
	d := h.WaitHTTP(t, "http://"+h.Port(t, name, "8080")+"/readyz", 250*time.Millisecond, 60*time.Second)
	h.Result(t, "read-only root, /tmp tmpfs, UID 10001, unreachable OTLP endpoint: /readyz 200 after %.1f s", d.Seconds())
}

func TestSerialGCOneCPU(t *testing.T) {
	h.RequireResources(t, 2, 4)
	out := flags(t, "--cpus=1", "--memory=4g", "--entrypoint", "java", jre(t), "-XX:+PrintFlagsFinal", "-version")
	if flag(t, out, "UseSerialGC") != "true" || flag(t, out, "UseG1GC") != "false" {
		t.Fatalf("1 CPU, 4 GiB: UseSerialGC=%s UseG1GC=%s", flag(t, out, "UseSerialGC"), flag(t, out, "UseG1GC"))
	}
	h.Result(t, "--cpus=1 --memory=4g, no GC flag: UseSerialGC = true, UseG1GC = false")
}

func TestSerialGCLowMemory(t *testing.T) {
	h.RequireResources(t, 2, 4)
	out := flags(t, "--cpus=2", "--memory=1700m", "--entrypoint", "java", jre(t), "-XX:+PrintFlagsFinal", "-version")
	if flag(t, out, "UseSerialGC") != "true" {
		t.Fatalf("2 CPUs, 1700 MiB: UseSerialGC=%s", flag(t, out, "UseSerialGC"))
	}
	h.Result(t, "--cpus=2 --memory=1700m, no GC flag: UseSerialGC = true")
}

func TestG1AboveThresholds(t *testing.T) {
	h.RequireResources(t, 2, 4)
	out := flags(t, "--cpus=2", "--memory=2048m", "--entrypoint", "java", jre(t), "-XX:+PrintFlagsFinal", "-version")
	if flag(t, out, "UseG1GC") != "true" {
		t.Fatalf("2 CPUs, 2048 MiB: UseG1GC=%s", flag(t, out, "UseG1GC"))
	}
	h.Result(t, "--cpus=2 --memory=2048m, no GC flag: UseG1GC = true")
}

func TestExplicitG1(t *testing.T) {
	h.RequireResources(t, 2, 4)
	for _, limits := range [][]string{{"--cpus=1", "--memory=4g"}, {"--cpus=2", "--memory=1700m"}} {
		out := flags(t, append(limits, "--entrypoint", "java", jre(t), "-XX:+UseG1GC", "-XX:+PrintFlagsFinal", "-version")...)
		if flag(t, out, "UseG1GC") != "true" {
			t.Fatalf("%v with -XX:+UseG1GC: UseG1GC=%s", limits, flag(t, out, "UseG1GC"))
		}
	}
	h.Result(t, "-XX:+UseG1GC at 1 CPU / 4 GiB and at 2 CPUs / 1700 MiB: UseG1GC = true")
}

func TestHeapIsSeventyFivePercent(t *testing.T) {
	out := flags(t, "--memory=1g", "--entrypoint", "java", jre(t), "-XX:MaxRAMPercentage=75", "-XX:+PrintFlagsFinal", "-version")
	if got := flag(t, out, "MaxHeapSize"); got != "805306368" {
		t.Fatalf("MaxHeapSize = %s, want 805306368", got)
	}
	h.Result(t, "--memory=1g -XX:MaxRAMPercentage=75: MaxHeapSize = 805306368")
}

func TestActiveProcessorCountOverride(t *testing.T) {
	out := flags(t, "--entrypoint", "sh", jre(t), "-c", "nproc; java -XX:ActiveProcessorCount=1 -XshowSettings:system -version 2>&1")
	lines := strings.Split(strings.TrimSpace(out), "\n")
	nproc, err := strconv.Atoi(strings.TrimSpace(lines[0]))
	if err != nil || nproc < 2 {
		t.Fatalf("nproc = %q; the runner must expose 2 or more CPUs", lines[0])
	}
	if !strings.Contains(out, "Effective CPU Count: 1") {
		t.Fatalf("ActiveProcessorCount=1 not reported:\n%s", out)
	}
	h.Result(t, "no CPU limit, nproc %d; -XX:ActiveProcessorCount=1 reports Effective CPU Count: 1", nproc)
}

var reRunning = regexp.MustCompile(`process running for ([0-9.]+)\)`)

func startSeconds(t *testing.T, aot bool) float64 {
	t.Helper()
	name := h.Name("startup")
	args := []string{"--cpus=2", "--memory=1g", "-e", "OTEL_EXPORTER_OTLP_ENDPOINT=http://127.0.0.1:1"}
	if !aot {
		args = append(args, "-e", "JDK_JAVA_OPTIONS=-XX:AOTMode=off")
	}
	h.MustDocker(t, append(append([]string{"run", "-d", "--name", name}, args...), h.FixtureImage())...)
	defer func() { _, _ = h.Docker(t, "rm", "-f", name) }()
	logs := h.WaitLog(t, name, "process running for", 120*time.Second)
	m := reRunning.FindStringSubmatch(logs)
	if m == nil {
		t.Fatalf("no startup line in:\n%s", logs)
	}
	s, _ := strconv.ParseFloat(m[1], 64)
	return s
}

func median(xs []float64) float64 {
	s := append([]float64{}, xs...)
	sort.Float64s(s)
	n := len(s)
	if n%2 == 1 {
		return s[n/2]
	}
	return (s[n/2-1] + s[n/2]) / 2
}

func TestAOTStartupDelta(t *testing.T) {
	startSeconds(t, false) // warm-up, discarded
	startSeconds(t, true)  // warm-up, discarded
	var on, off []float64
	for i := 0; i < 10; i++ {
		off = append(off, startSeconds(t, false))
		on = append(on, startSeconds(t, true))
	}
	mOn, mOff := median(on), median(off)
	h.Result(t, "process running for (s), --cpus=2 --memory=1g, 10 alternating rounds. AOT off %v median %.3f; AOT on %v median %.3f; delta %.3f s (%.2fx)",
		fmtList(off), mOff, fmtList(on), mOn, mOff-mOn, mOff/mOn)
}

func fmtList(xs []float64) string {
	p := make([]string, len(xs))
	for i, x := range xs {
		p[i] = fmt.Sprintf("%.2f", x)
	}
	return "[" + strings.Join(p, " ") + "]"
}
