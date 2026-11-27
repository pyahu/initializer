package evidence

import (
	"os"
	"path/filepath"
	"strconv"
	"testing"
)

// Every vertical of a monorepo fixture has its own ArchitectureTest: one failing must fail the required test, in
// whichever order the reports are read.
func TestReadJUnitAddsUpOneClassNameAcrossProjects(t *testing.T) {
	dir := t.TempDir()
	write := func(name, suite string, tests, failures int) {
		xml := `<testsuite name="` + suite + `" tests="` + itoa(tests) + `" failures="` + itoa(failures) + `" errors="0" skipped="0"></testsuite>`
		if err := os.WriteFile(filepath.Join(dir, name), []byte(xml), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	write("TEST-io.acme.billing.ArchitectureTest.xml", "io.acme.billing.ArchitectureTest", 10, 1)
	write("TEST-io.acme.orders.ArchitectureTest.xml", "io.acme.orders.ArchitectureTest", 11, 0)

	res, err := ReadJUnit(dir, "monorepo")
	if err != nil {
		t.Fatal(err)
	}
	got := res["gradle/monorepo::ArchitectureTest"]
	if got.Status != "fail" || got.Runs != 21 {
		t.Fatalf("got %+v, want status fail and 21 runs", got)
	}
}

func itoa(n int) string { return strconv.Itoa(n) }
