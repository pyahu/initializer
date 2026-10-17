package guidedata

import (
	"fmt"
	"sort"
	"strings"
)

// GuideStatus is the computed state of a guide.
type GuideStatus struct {
	Reverify        bool     `json:"reverify"`
	ReverifyReasons []string `json:"reverifyReasons"`
	Stale           bool     `json:"stale"`
}

// Status computes re-verify and stale for a guide.
func (r *Repo) Status(g *Guide) GuideStatus {
	st := GuideStatus{ReverifyReasons: []string{}}
	i := r.Versions.RevisionIndex(g.Meta.Baseline)
	j := r.Versions.RevisionIndex(r.Versions.Active)
	if i >= 0 && j > i {
		for k := i + 1; k <= j; k++ {
			prev, cur := r.Versions.Revisions[k-1], r.Versions.Revisions[k]
			for _, c := range g.Meta.Components {
				if prev.Components[c].Version != cur.Components[c].Version {
					st.ReverifyReasons = append(st.ReverifyReasons, fmt.Sprintf("%s changed in %s", c, cur.ID))
				}
			}
		}
		st.Stale = j-i > 2
	}
	for _, inv := range r.Invalidations {
		if inv.Closed != nil {
			continue
		}
		hit := containsStr(inv.Guides, g.Meta.ID)
		for _, c := range inv.Components {
			hit = hit || containsStr(g.Meta.Components, c)
		}
		if hit {
			st.ReverifyReasons = append(st.ReverifyReasons, fmt.Sprintf("%s: %s", inv.ID, inv.Reason))
		}
	}
	sort.Strings(st.ReverifyReasons)
	st.Reverify = len(st.ReverifyReasons) > 0
	return st
}

// CanonicalLine is the first paragraph of a rendered evidence record.
func CanonicalLine(e EvidenceRecord) string {
	if !e.Exercised() {
		return fmt.Sprintf("Evidence %s · not exercised · baseline %s. %s Limitations: %s", e.ID, e.Baseline, e.Claim, e.Limitations)
	}
	return fmt.Sprintf("Evidence %s · %s · baseline %s · %s. %s Result: %s. Limitations: %s",
		e.ID, e.Badge, e.Baseline, e.Date, e.Claim, strings.TrimSuffix(e.Result, "."), e.Limitations)
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

// Repository is the public repository the evidence runs and commits belong to.
const Repository = "https://github.com/pyahu/initializer"

// CommitURL links a commit of Repository.
func CommitURL(commit string) string { return Repository + "/commit/" + commit }

// RunText labels a run and its commit: "run 123 attempt 1, commit 1a2b3c4".
func RunText(run, commit string) string {
	id, attempt := run, ""
	if p := strings.Split(run, "/actions/runs/"); len(p) == 2 {
		parts := strings.Split(p[1], "/attempts/")
		id = parts[0]
		if len(parts) == 2 {
			attempt = parts[1]
		}
	}
	short := commit
	if len(short) > 7 {
		short = short[:7]
	}
	return fmt.Sprintf("run %s attempt %s, commit %s", id, attempt, short)
}
