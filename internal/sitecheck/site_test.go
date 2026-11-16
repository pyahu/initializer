package sitecheck

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/pyahu/initializer/internal/guidedata"
)

// The compact exporter strips note asides by default. Coverage must survive in both exports.
func TestExportsRequireEvidenceCoverage(t *testing.T) {
	r := &guidedata.Repo{
		Guides: []*guidedata.Guide{{
			Title: "Example guide",
			Meta:  guidedata.GuideMeta{ID: "example"},
			Cards: []guidedata.Card{{ID: "example-step"}},
		}},
		Evidence: []guidedata.EvidenceRecord{{Card: "example-step", Badge: "not-exercised"}},
	}
	const site = "https://example.test"
	const coverage = "1 claim: 1 not exercised"
	for _, missing := range []string{"", "llms-full.txt", "llms-small.txt"} {
		t.Run("missing="+missing, func(t *testing.T) {
			dist := t.TempDir()
			write := func(name, body string) {
				t.Helper()
				if err := os.WriteFile(filepath.Join(dist, name), []byte(body), 0o644); err != nil {
					t.Fatal(err)
				}
			}
			write("llms.txt", site+"/guides/ "+site+"/reference/docs/ "+site+"/guides/example/")
			for _, name := range []string{"llms-full.txt", "llms-small.txt"} {
				body := "# Example guide\n\n"
				if name != missing {
					body += coverage
				}
				write(name, body+PageSeparator+"# Reference docs\n")
			}
			errs := checkExports(r, dist, site)
			if missing == "" {
				if len(errs) != 0 {
					t.Fatalf("complete exports rejected: %v", errs)
				}
			} else if len(errs) != 1 || !strings.Contains(errs[0].Error(), missing+": evidence coverage missing") {
				t.Fatalf("expected missing coverage in %s, got %v", missing, errs)
			}
		})
	}
}
