package guidedata

import (
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"time"
)

// PilotQuestions are the question IDs the pilot guides must answer.
var PilotQuestions = map[string][]string{
	"jvm-in-containers": {"JVM-Q1", "JVM-Q2", "JVM-Q3", "JVM-Q4", "JVM-Q5", "JVM-Q6", "JVM-Q7", "JVM-Q8"},
	"observability":     {"OBS-Q1", "OBS-Q2", "OBS-Q3", "OBS-Q4", "OBS-Q5", "OBS-Q6", "OBS-Q7", "OBS-Q8"},
}

// Check runs every cross-reference rule. It does not compare with history.
func (r *Repo) Check() []error {
	var errs []error
	add := func(format string, a ...any) { errs = append(errs, fmt.Errorf(format, a...)) }

	// Versions.
	if r.Versions.ActiveRevision() == nil {
		add("versions: active %s is not a revision", r.Versions.Active)
	}
	seenRev := map[string]bool{}
	for _, rev := range r.Versions.Revisions {
		if seenRev[rev.ID] {
			add("versions: duplicate revision %s", rev.ID)
		}
		seenRev[rev.ID] = true
		if !strings.HasPrefix(rev.ID, rev.Release+"-") {
			add("versions: revision %s does not belong to release %s", rev.ID, rev.Release)
		}
		for key, c := range rev.Components {
			if c.ManagedBy != nil {
				if _, ok := rev.Components[*c.ManagedBy]; !ok {
					add("versions %s: %s managedBy %s is not a component", rev.ID, key, *c.ManagedBy)
				}
			}
		}
	}

	// Guides.
	prefixes := map[string]string{}
	cards := map[string]string{} // card -> guide
	for _, g := range r.Guides {
		m := g.Meta
		if p, dup := prefixes[m.Prefix]; dup {
			add("%s: prefix %s already used by %s", g.Path, m.Prefix, p)
		}
		prefixes[m.Prefix] = m.ID
		pageCards := map[string]bool{}
		for _, c := range g.Cards {
			if other, dup := cards[c.ID]; dup {
				add("%s: card %s already defined in %s", g.Path, c.ID, other)
			}
			cards[c.ID] = m.ID
			pageCards[c.ID] = true
		}
		qids := map[string]bool{}
		for _, q := range m.Questions {
			if !strings.HasPrefix(q.ID, m.Prefix+"-Q") {
				add("%s: question %s must start with %s-Q", g.Path, q.ID, m.Prefix)
			}
			if qids[q.ID] {
				add("%s: duplicate question %s", g.Path, q.ID)
			}
			qids[q.ID] = true
			if !pageCards[q.AnsweredIn] {
				add("%s: question %s answeredIn %s is not a card on the page", g.Path, q.ID, q.AnsweredIn)
			}
		}
		// Each card answers one question, and its heading is that question, numbered.
		for i, c := range g.Cards {
			var qs []Question
			for _, q := range m.Questions {
				if q.AnsweredIn == c.ID {
					qs = append(qs, q)
				}
			}
			if len(qs) != 1 {
				add("%s: card %s must answer exactly one question, %d do", g.Path, c.ID, len(qs))
				continue
			}
			if want := fmt.Sprintf("%d. %s", i+1, qs[0].Text); normalizeQuotes(c.Title) != want {
				add("%s: card %s heading %q must be %q", g.Path, c.ID, c.Title, want)
			}
		}
		if want, pilot := PilotQuestions[m.ID]; pilot {
			got := make([]string, 0, len(m.Questions))
			for _, q := range m.Questions {
				got = append(got, q.ID)
			}
			if strings.Join(got, ",") != strings.Join(want, ",") {
				add("%s: pilot questions must be exactly %v, got %v", g.Path, want, got)
			}
		}
		for _, d := range g.Diagrams {
			if _, ok := r.Diagrams[d]; !ok {
				add("%s: diagram component %s not found", g.Path, d)
			}
		}
		for _, items := range g.Legends {
			if len(items) == 0 {
				add("%s: empty DiagramLegend", g.Path)
			}
			for _, it := range items {
				if _, ok := LegendText[it]; !ok {
					add("%s: unknown legend item %q", g.Path, it)
				}
			}
		}
		for _, rc := range g.Recipes {
			if err := r.CheckRecipe(rc); err != nil {
				add("%s:%d: %v", g.Path, rc.Line, err)
			}
		}
	}

	// Evidence: one tested claim per record, proven by a required test of a fixture.
	evIDs := map[string]bool{}
	for _, e := range r.Evidence {
		if evIDs[e.ID] {
			add("evidence: duplicate id %s", e.ID)
		}
		evIDs[e.ID] = true
		if !regexp.MustCompile(`^ev-` + regexp.QuoteMeta(e.Card) + `-[0-9]+$`).MatchString(e.ID) {
			add("evidence %s: id must be ev-%s-<n>", e.ID, e.Card)
		}
		if cards[e.Card] != e.Guide {
			add("evidence %s: card %s is not a card of guide %s", e.ID, e.Card, e.Guide)
		}
		if !r.isRequiredTest(e.Test) {
			add("evidence %s: test %s is in no fixture's required tests", e.ID, e.Test)
		}
	}

	// Checklist.
	clIDs := map[string]bool{}
	for _, c := range r.Checklist {
		if clIDs[c.ID] {
			add("checklist: duplicate id %s", c.ID)
		}
		clIDs[c.ID] = true
		g := r.Guide(c.Guide)
		if g == nil {
			add("checklist %s: unknown guide %s", c.ID, c.Guide)
			continue
		}
		if !strings.HasPrefix(c.ID, g.Meta.Prefix+"-") {
			add("checklist %s: id must start with %s-", c.ID, g.Meta.Prefix)
		}
		if cards[c.Card] != c.Guide {
			add("checklist %s: card %s is not on guide %s", c.ID, c.Card, c.Guide)
		}
		if err := CheckFilter(c.AppliesWhen); err != nil {
			add("checklist %s: %v", c.ID, err)
		}
	}

	// References.
	refIDs := map[string]bool{}
	for _, e := range r.References.Entries {
		if refIDs[e.ID] {
			add("references: duplicate id %s", e.ID)
		}
		refIDs[e.ID] = true
		if err := CheckFilter(e.AppliesWhen); err != nil {
			add("references %s: %v", e.ID, err)
		}
		for _, g := range e.Guides {
			if r.Guide(g) == nil {
				add("references %s: unknown guide %s", e.ID, g)
			}
		}
		if e.Component == nil && e.Match != "none" {
			add("references %s: an entry without a component must use match none", e.ID)
		}
		for _, b := range e.Baselines {
			rev := r.Versions.Revision(b)
			if rev == nil {
				add("references %s: baseline %s unknown", e.ID, b)
				continue
			}
			if e.Component == nil {
				continue
			}
			c, ok := rev.Components[*e.Component]
			if !ok {
				add("references %s: component %s not in %s", e.ID, *e.Component, b)
				continue
			}
			if !VersionMatches(e.Match, e.DocVersion, c.Version) {
				add("references %s: docVersion %s does not match %s %s (%s) in %s", e.ID, e.DocVersion, *e.Component, c.Version, e.Match, b)
			}
		}
	}

	// Retired, exceptions, fixtures.
	live := r.liveIDs()
	for _, x := range r.Retired {
		if live[x.Kind][x.ID] {
			add("retired %s %s is still in use", x.Kind, x.ID)
		}
		if x.ReplacedBy != "" && !live[x.Kind][x.ReplacedBy] {
			add("retired %s %s: replacedBy %s does not exist", x.Kind, x.ID, x.ReplacedBy)
		}
	}
	for _, x := range r.Exceptions {
		a, err1 := time.Parse("2006-01-02", x.Added)
		e, err2 := time.Parse("2006-01-02", x.Expires)
		if err1 != nil || err2 != nil || e.Before(a) || e.Sub(a) > 30*24*time.Hour {
			add("link exception %s: expires must be within 30 days after added", x.URL)
		}
	}
	for _, x := range r.Audit {
		a, err1 := time.Parse("2006-01-02", x.Added)
		e, err2 := time.Parse("2006-01-02", x.Expires)
		if err1 != nil || err2 != nil || e.Before(a) || e.Sub(a) > 30*24*time.Hour {
			add("audit exception %s: expires must be within 30 days after added", x.Advisory)
		}
	}
	if rev := r.Versions.ActiveRevision(); rev != nil {
		for _, fx := range r.Fixtures {
			for _, c := range fx.Exercises {
				if _, ok := rev.Components[c]; !ok {
					add("fixture %s: exercises %s, not in %s", fx.Name, c, rev.ID)
				}
			}
		}
	}
	errs = append(errs, r.checkReleases(cards)...)
	errs = append(errs, r.checkDashes()...)
	sort.Slice(errs, func(i, j int) bool { return errs[i].Error() < errs[j].Error() })
	return errs
}

func (r *Repo) isRequiredTest(t string) bool {
	for _, fx := range r.Fixtures {
		if containsStr(fx.RequiredTests, t) {
			return true
		}
	}
	return false
}

// CheckRecipe verifies the recipe's file and its start and end anchors.
func (r *Repo) CheckRecipe(rc Recipe) error {
	if !strings.HasPrefix(rc.Path, "fixtures/") || strings.Contains(rc.Path, "..") {
		return fmt.Errorf("recipe %s: path must be under fixtures/", rc.ID)
	}
	_, err := r.Excerpt(rc)
	return err
}

// Excerpt returns the recipe's lines.
func (r *Repo) Excerpt(rc Recipe) (string, error) {
	b, err := os.ReadFile(filepath.Join(r.Root, rc.Path))
	if err != nil {
		return "", fmt.Errorf("recipe %s: %v", rc.ID, err)
	}
	lines := strings.Split(strings.TrimRight(string(b), "\n"), "\n")
	find := func(prefix string, from int) []int {
		var at []int
		for i := from; i < len(lines); i++ {
			if strings.HasPrefix(strings.TrimSpace(lines[i]), prefix) {
				at = append(at, i)
			}
		}
		return at
	}
	s := find(rc.Start, 0)
	if len(s) != 1 {
		return "", fmt.Errorf("recipe %s: start %q matches %d lines, want 1", rc.ID, rc.Start, len(s))
	}
	e := s[0]
	if rc.End != "" {
		ends := find(rc.End, s[0]+1)
		if len(ends) == 0 {
			return "", fmt.Errorf("recipe %s: end %q matches no later line", rc.ID, rc.End)
		}
		e = ends[0]
	}
	return strings.Join(lines[s[0]:e+1], "\n"), nil
}

// VersionMatches applies a reference entry's match rule.
func VersionMatches(match, doc, pin string) bool {
	parts := func(v string) []string {
		v = strings.SplitN(v, "+", 2)[0]
		return strings.Split(v, ".")
	}
	d, p := parts(doc), parts(pin)
	n := map[string]int{"exact": -1, "minor": 2, "major": 1}[match]
	switch match {
	case "none":
		return true
	case "exact":
		return doc == pin
	}
	if len(d) < n || len(p) < n {
		return false
	}
	for i := 0; i < n; i++ {
		if d[i] != p[i] {
			return false
		}
	}
	return true
}

// liveIDs returns the IDs whose URLs or anchors readers may have saved, by retired kind.
func (r *Repo) liveIDs() map[string]map[string]bool {
	m := map[string]map[string]bool{"guide": {}, "card": {}, "checklist": {}}
	for _, g := range r.Guides {
		m["guide"][g.Meta.ID] = true
		for _, c := range g.Cards {
			m["card"][c.ID] = true
		}
	}
	for _, c := range r.Checklist {
		m["checklist"][c.ID] = true
	}
	return m
}

func (r *Repo) checkDashes() []error {
	var errs []error
	_ = filepath.Walk(filepath.Join(r.Root, "data"), func(p string, info os.FileInfo, err error) error {
		if err != nil || info.IsDir() || !(strings.HasSuffix(p, ".yaml") || strings.HasSuffix(p, ".json")) {
			return nil
		}
		b, _ := os.ReadFile(p)
		for i, l := range strings.Split(string(b), "\n") {
			if reDashChars.MatchString(l) {
				errs = append(errs, fmt.Errorf("%s:%d: em or en dash", r.Rel(p), i+1))
			}
		}
		return nil
	})
	return errs
}

func containsStr(list []string, s string) bool {
	for _, x := range list {
		if x == s {
			return true
		}
	}
	return false
}
