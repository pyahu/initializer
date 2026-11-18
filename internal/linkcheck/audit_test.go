package linkcheck

import (
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/pyahu/initializer/internal/guidedata"
)

func TestAuditRejectsUnavailableReports(t *testing.T) {
	for _, tc := range []struct {
		name string
		body string
		code int
	}{
		{"registry error", `{"error":{"code":"ENOAUDIT","summary":"registry unavailable"}}`, 1},
		{"missing findings", `{"auditReportVersion":2}`, 0},
		{"null findings", `{"auditReportVersion":2,"vulnerabilities":null}`, 0},
		{"unsupported format", `{"auditReportVersion":1,"vulnerabilities":{}}`, 0},
		{"invalid JSON", `<html>unavailable</html>`, 1},
		{"unexpected exit", `{"auditReportVersion":2,"vulnerabilities":{}}`, 2},
		{"findings exit without findings", `{"auditReportVersion":2,"vulnerabilities":{}}`, 1},
		{"invalid advisory URL", auditFinding("x"), 1},
		{"missing advisory", `{"auditReportVersion":2,"vulnerabilities":{"picomatch":{"severity":"high","via":[]}}}`, 1},
		{"unreported dependency", `{"auditReportVersion":2,"vulnerabilities":{"astro":{"severity":"high","via":["picomatch"]}}}`, 1},
	} {
		t.Run(tc.name, func(t *testing.T) {
			dir := fakeNpmAudit(t, tc.body, tc.code)
			if errs := Audit(&guidedata.Repo{}, dir, time.Now()); len(errs) == 0 {
				t.Fatal("an unavailable or invalid audit report approved publication")
			}
		})
	}
}

func TestAuditFindingsAndExpiringExceptions(t *testing.T) {
	now := time.Date(2026, 11, 18, 12, 0, 0, 0, time.UTC)
	for _, tc := range []struct {
		name    string
		body    string
		code    int
		allow   bool
		pkg     string
		expires string
	}{
		{name: "clean", body: `{"auditReportVersion":2,"vulnerabilities":{}}`, allow: true},
		{name: "high finding", body: auditFinding("https://github.com/advisories/GHSA-fc5q-39xc-vjpf"), code: 1},
		{name: "scoped exception", body: auditFinding("https://github.com/advisories/GHSA-fc5q-39xc-vjpf"), code: 1, allow: true, pkg: "picomatch", expires: "2026-11-25"},
		{name: "wrong package", body: auditFinding("https://github.com/advisories/GHSA-fc5q-39xc-vjpf"), code: 1, pkg: "other", expires: "2026-11-25"},
		{name: "expired exception", body: auditFinding("https://github.com/advisories/GHSA-fc5q-39xc-vjpf"), code: 1, pkg: "picomatch", expires: "2026-11-17"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			dir := fakeNpmAudit(t, tc.body, tc.code)
			r := &guidedata.Repo{}
			if tc.pkg != "" {
				r.Audit = []guidedata.AuditException{{Advisory: "GHSA-fc5q-39xc-vjpf", Package: tc.pkg, Expires: tc.expires}}
			}
			if errs := Audit(r, dir, now); (len(errs) == 0) != tc.allow {
				t.Fatalf("allow=%v, errors=%v", tc.allow, errs)
			}
		})
	}
}

func auditFinding(url string) string {
	return `{"auditReportVersion":2,"vulnerabilities":{"picomatch":{"severity":"high","via":[{"url":` + strconv.Quote(url) + `,"title":"ReDoS"}]}}}`
}

func fakeNpmAudit(t *testing.T, body string, code int) string {
	t.Helper()
	dir := t.TempDir()
	script := "#!/bin/sh\nprintf '%s\\n' '" + strings.ReplaceAll(body, "'", "'\\''") + "'\nexit " + strconv.Itoa(code) + "\n"
	if err := os.WriteFile(filepath.Join(dir, "npm"), []byte(script), 0o755); err != nil {
		t.Fatal(err)
	}
	t.Setenv("PATH", dir+string(os.PathListSeparator)+os.Getenv("PATH"))
	return dir
}
