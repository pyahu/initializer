package sitecheck

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/pyahu/initializer/internal/guidedata"
)

// Both llms exports must carry every guide; the compact one is built with a different exporter.
func TestExportsRequireEveryGuide(t *testing.T) {
	r := &guidedata.Repo{
		Guides: []*guidedata.Guide{{
			Title: "Example guide",
			Meta:  guidedata.GuideMeta{ID: "example"},
			Cards: []guidedata.Card{{ID: "example-step"}},
		}},
	}
	const site = "https://example.test"
	for _, missing := range []string{"", "llms-full.txt", "llms-small.txt"} {
		t.Run("missing="+missing, func(t *testing.T) {
			dist := t.TempDir()
			write := func(name, body string) {
				t.Helper()
				if err := os.WriteFile(filepath.Join(dist, name), []byte(body), 0o644); err != nil {
					t.Fatal(err)
				}
			}
			write("llms.txt", site+"/guides/ "+site+"/reference/docs/ "+site+"/releases/ "+site+"/guides/example/")
			for _, name := range []string{"llms-full.txt", "llms-small.txt"} {
				body := ""
				if name != missing {
					body = "# Example guide\n\nText.\n" + PageSeparator
				}
				write(name, body+"# Reference docs\n")
			}
			errs := checkExports(r, dist, site)
			if missing == "" {
				if len(errs) != 0 {
					t.Fatalf("complete exports rejected: %v", errs)
				}
			} else if len(errs) != 1 || !strings.Contains(errs[0].Error(), missing+": no page segment") {
				t.Fatalf("expected the missing guide in %s, got %v", missing, errs)
			}
		})
	}
}
