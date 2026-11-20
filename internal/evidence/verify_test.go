package evidence

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/pyahu/initializer/internal/guidedata"
)

func TestPostgresEvidenceRequiresThePinnedTestImage(t *testing.T) {
	const digest = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
	for _, tc := range []struct {
		name string
		pin  string
		pass bool
	}{
		{"matching version and digest", "18.6@" + digest, true},
		{"different version", "18.5@" + digest, false},
		{"different digest", "18.6@sha256:" + strings.Repeat("b", 64), false},
		{"tag without digest", "18.6", false},
		{"missing pin", "", false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			dir := t.TempDir()
			write := func(path, text string) {
				t.Helper()
				path = filepath.Join(dir, path)
				if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
					t.Fatal(err)
				}
				if err := os.WriteFile(path, []byte(text), 0o644); err != nil {
					t.Fatal(err)
				}
			}
			write("gradle/libs.versions.toml", "[versions]\npostgres-image = \""+tc.pin+"\"\n")
			write("Dockerfile", "FROM scratch\n")
			write("junit/TEST-DatabaseRolesTest.xml", `<testsuite name="example.DatabaseRolesTest" tests="6" failures="0" errors="0" skipped="0"/>`)
			r := &guidedata.Repo{
				Versions: guidedata.Versions{Active: "v0.1-b2", Revisions: []guidedata.Revision{{ID: "v0.1-b2", Components: map[string]guidedata.Component{
					"postgres-image": {Kind: "image", Image: "postgres:18.6", Version: "18.6", Digest: digest},
				}}}},
				Fixtures: []guidedata.Fixture{{Name: "example", Dir: dir, Exercises: []string{"postgres-image"}, RequiredTests: []string{"gradle/example::DatabaseRolesTest"}}},
			}
			report, errs := Verify(r, VerifyInput{Fixture: "example", JUnitDir: filepath.Join(dir, "junit"), GradleOutcome: "success"})
			if len(errs) != 0 {
				t.Fatalf("verify errors: %v", errs)
			}
			if report.OK != tc.pass {
				t.Fatalf("pass=%v, report=%+v", tc.pass, report)
			}
		})
	}
}
