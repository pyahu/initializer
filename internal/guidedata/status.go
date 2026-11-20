package guidedata

import (
	"fmt"
	"sort"
	"strings"
)

// CanonicalLine is the one-line form of a tested claim, used on the pages and in the exports.
func CanonicalLine(e EvidenceRecord) string {
	s := fmt.Sprintf("Evidence %s · %s by %s. %s", e.ID, e.Badge, e.Test, e.Claim)
	if e.Result != "" {
		s += " Result: " + strings.TrimSuffix(e.Result, ".") + "."
	}
	if e.Run != "" {
		s += fmt.Sprintf(" Measured in %s on %s.", RunText(e.Run), e.Date)
	}
	return s + " Limitations: " + e.Limitations
}

// FilterTags renders a filter as human tags, one per condition.
func FilterTags(f Filter) []string {
	keys := make([]string, 0, len(f))
	for k := range f {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	tags := []string{}
	for _, k := range keys {
		vals := make([]string, len(f[k]))
		for i, v := range f[k] {
			vals[i] = fmt.Sprint(v)
		}
		tags = append(tags, fmt.Sprintf("%s: %s", k, strings.Join(vals, " or ")))
	}
	return tags
}

// Repository is the public repository the evidence runs belong to.
const Repository = "https://github.com/pyahu/initializer"

// RunText labels a run: "run 123 attempt 1".
func RunText(run string) string {
	id, attempt := run, ""
	if p := strings.Split(run, "/actions/runs/"); len(p) == 2 {
		parts := strings.Split(p[1], "/attempts/")
		id = parts[0]
		if len(parts) == 2 {
			attempt = parts[1]
		}
	}
	return fmt.Sprintf("run %s attempt %s", id, attempt)
}
