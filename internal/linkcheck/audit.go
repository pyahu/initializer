package linkcheck

import (
	"encoding/json"
	"fmt"
	"os/exec"
	"regexp"
	"sort"
	"time"

	"github.com/pyahu/initializer/internal/guidedata"
)

// Audit runs npm audit in dir and fails on high or critical advisories, except those with an
// unexpired record in data/audit-exceptions.yaml.
func Audit(r *guidedata.Repo, dir string, now time.Time) []error {
	cmd := exec.Command("npm", "audit", "--json")
	cmd.Dir = dir
	out, runErr := cmd.Output() // npm audit exits 1 when it reports findings.
	if runErr != nil {
		if exit, ok := runErr.(*exec.ExitError); !ok || exit.ExitCode() != 1 {
			return []error{fmt.Errorf("npm audit did not complete: %w", runErr)}
		}
	}
	var rep struct {
		AuditReportVersion int             `json:"auditReportVersion"`
		Error              json.RawMessage `json:"error"`
		Vulnerabilities    map[string]struct {
			Severity string            `json:"severity"`
			Via      []json.RawMessage `json:"via"`
		} `json:"vulnerabilities"`
	}
	if err := json.Unmarshal(out, &rep); err != nil {
		return []error{fmt.Errorf("npm audit --json: %v: %s", err, out)}
	}
	if rep.AuditReportVersion != 2 || rep.Vulnerabilities == nil || (len(rep.Error) > 0 && string(rep.Error) != "null") {
		return []error{fmt.Errorf("npm audit returned an error or an incomplete version 2 report")}
	}
	if runErr != nil && len(rep.Vulnerabilities) == 0 {
		return []error{fmt.Errorf("npm audit failed without reporting findings: %w", runErr)}
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
		if len(v.Via) == 0 {
			errs = append(errs, fmt.Errorf("npm audit %s has no advisory details", pkg))
		}
		for _, raw := range v.Via {
			var via struct {
				URL   string `json:"url"`
				Title string `json:"title"`
			}
			var dependency string
			if json.Unmarshal(raw, &dependency) == nil {
				if _, ok := rep.Vulnerabilities[dependency]; !ok {
					errs = append(errs, fmt.Errorf("npm audit %s references an unreported vulnerable package %s", pkg, dependency))
				}
				continue // Dependency findings are reported on their own package.
			}
			if json.Unmarshal(raw, &via) != nil || !advisoryURL.MatchString(via.URL) {
				errs = append(errs, fmt.Errorf("npm audit %s has an invalid advisory", pkg))
				continue
			}
			ghsa := advisoryURL.FindStringSubmatch(via.URL)[1]
			if _, ok := allowed[pkg+"\x00"+ghsa]; ok {
				continue
			}
			errs = append(errs, fmt.Errorf("%s %s: %s %s", v.Severity, pkg, ghsa, via.Title))
		}
	}
	sort.Slice(errs, func(i, j int) bool { return errs[i].Error() < errs[j].Error() })
	return errs
}

var advisoryURL = regexp.MustCompile(`^https://github\.com/advisories/(GHSA-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4})$`)
