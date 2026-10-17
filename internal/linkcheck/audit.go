package linkcheck

import (
	"encoding/json"
	"fmt"
	"os/exec"
	"sort"
	"time"

	"github.com/pyahu/initializer/internal/guidedata"
)

// Audit runs npm audit in dir and fails on high or critical advisories, except those with an
// unexpired record in data/audit-exceptions.yaml.
func Audit(r *guidedata.Repo, dir string, now time.Time) []error {
	cmd := exec.Command("npm", "audit", "--json")
	cmd.Dir = dir
	out, _ := cmd.Output() // npm audit exits 1 when it finds anything
	var rep struct {
		Vulnerabilities map[string]struct {
			Severity string            `json:"severity"`
			Via      []json.RawMessage `json:"via"`
		} `json:"vulnerabilities"`
	}
	if err := json.Unmarshal(out, &rep); err != nil {
		return []error{fmt.Errorf("npm audit --json: %v: %s", err, out)}
	}
	allowed := map[string]guidedata.AuditException{}
	var errs []error
	for _, x := range r.Audit {
		exp, _ := time.Parse("2006-01-02", x.Expires)
		if now.UTC().After(exp.Add(24 * time.Hour)) {
			errs = append(errs, fmt.Errorf("audit exception %s (%s) expired on %s", x.Advisory, x.Package, x.Expires))
			continue
		}
		allowed[x.Package+"\x00"+x.Advisory] = x
	}
	for pkg, v := range rep.Vulnerabilities {
		if v.Severity != "high" && v.Severity != "critical" {
			continue
		}
		for _, raw := range v.Via {
			var via struct {
				URL   string `json:"url"`
				Title string `json:"title"`
			}
			if json.Unmarshal(raw, &via) != nil || via.URL == "" {
				continue // a reference to another vulnerable package, reported on its own
			}
			ghsa := via.URL[len(via.URL)-19:]
			if _, ok := allowed[pkg+"\x00"+ghsa]; ok {
				continue
			}
			errs = append(errs, fmt.Errorf("%s %s: %s %s", v.Severity, pkg, ghsa, via.Title))
		}
	}
	sort.Slice(errs, func(i, j int) bool { return errs[i].Error() < errs[j].Error() })
	return errs
}
