package references

import (
	"fmt"
	"sort"

	"github.com/pyahu/initializer/internal/guidedata"
)

type evidenceOut struct {
	ID          string `json:"id"`
	Badge       string `json:"badge"`
	Canonical   string `json:"canonical"`
	Test        string `json:"test"`
	Run         string `json:"run,omitempty"`
	RunURL      string `json:"runUrl,omitempty"`
	Measurement string `json:"measurement,omitempty"`
}

type cardOut struct {
	Title    string        `json:"title"`
	Coverage string        `json:"coverage"`
	Records  []evidenceOut `json:"records"`
}

type checklistOut struct {
	ID   string   `json:"id"`
	Type string   `json:"type"`
	Card string   `json:"card"`
	Text string   `json:"text"`
	Tags []string `json:"tags"`
}

type refOut struct {
	ID         string `json:"id"`
	Title      string `json:"title"`
	URL        string `json:"url"`
	Version    string `json:"version"`
	DocVersion string `json:"docVersion"`
	Pinned     bool   `json:"pinned"`
	Purpose    string `json:"purpose"`
	Later      bool   `json:"later"`
}

type questionOut struct {
	ID         string `json:"id"`
	Text       string `json:"text"`
	AnsweredIn string `json:"answeredIn"`
	CardTitle  string `json:"cardTitle"`
}

type contentOut struct {
	ID    string `json:"id"`
	Title string `json:"title"`
}

type guideOut struct {
	ID         string             `json:"id"`
	Title      string             `json:"title"`
	State      string             `json:"state"`
	Baseline   string             `json:"baseline"`
	Coverage   string             `json:"coverage"`
	Sources    []guidedata.Source `json:"sources"`
	Questions  []questionOut      `json:"questions"`
	Cards      map[string]cardOut `json:"cards"`
	Contents   []contentOut       `json:"contents"`
	Checklist  []checklistOut     `json:"checklist"`
	References []refOut           `json:"references"`
}

// ProofNote explains what a tested claim is and what the other steps rest on. The site and the skill both show it.
const ProofNote = "A tested claim names a fixture test that CI runs on every change, so it fails as soon as the claim stops holding. " +
	"Compiled means the code builds; reproduced means a test observed the behaviour; measured means a recorded measurement. " +
	"The other steps rest on the official documentation listed at the end of the guide and on Pyahu's practice; no test here proves them."

// BadgeOrder orders badges for coverage lines.
var BadgeOrder = []string{"measured", "reproduced", "compiled"}

// Coverage renders a step's tested claims: "2 tested claims: 1 reproduced, 1 compiled", or "not tested here".
func Coverage(recs []guidedata.EvidenceRecord) string {
	if len(recs) == 0 {
		return "not tested here"
	}
	return claims(recs)
}

func claims(recs []guidedata.EvidenceRecord) string {
	n := map[string]int{}
	for _, e := range recs {
		n[e.Badge]++
	}
	s := fmt.Sprintf("%d tested claim", len(recs))
	if len(recs) != 1 {
		s += "s"
	}
	sep := ": "
	for _, b := range BadgeOrder {
		if n[b] > 0 {
			s += fmt.Sprintf("%s%d %s", sep, n[b], b)
			sep = ", "
		}
	}
	return s
}

func cardEvidence(r *guidedata.Repo, card string) []guidedata.EvidenceRecord {
	var records []guidedata.EvidenceRecord
	for _, e := range r.Evidence {
		if e.Card == card {
			records = append(records, e)
		}
	}
	return records
}

// GuideCoverage summarises a guide's tests: "3 of 8 steps are tested here (5 tested claims: 3 reproduced, 2 compiled)".
// The verb agrees with the tested steps: "1 of 8 steps is tested here".
func GuideCoverage(r *guidedata.Repo, g *guidedata.Guide) string {
	var all []guidedata.EvidenceRecord
	tested := 0
	for _, c := range g.Cards {
		recs := cardEvidence(r, c.ID)
		if len(recs) > 0 {
			tested++
		}
		all = append(all, recs...)
	}
	if tested == 0 {
		return fmt.Sprintf("None of its %d steps is tested here yet", len(g.Cards))
	}
	verb := "are"
	if tested == 1 {
		verb = "is"
	}
	return fmt.Sprintf("%d of %d steps %s tested here (%s)", tested, len(g.Cards), verb, claims(all))
}

// GuidesJSON renders guides/src/generated/guides.json.
func GuidesJSON(r *guidedata.Repo) ([]byte, error) {
	guides := map[string]guideOut{}
	for _, g := range r.Guides {
		cardTitle := map[string]string{}
		for _, c := range g.Cards {
			cardTitle[c.ID] = c.Title
		}
		out := guideOut{ID: g.Meta.ID, Title: g.Title, State: g.Meta.State, Baseline: r.Versions.Active,
			Coverage: GuideCoverage(r, g), Sources: g.Meta.Sources, Cards: map[string]cardOut{},
			Questions: []questionOut{}, Contents: []contentOut{}, Checklist: []checklistOut{}, References: []refOut{}}
		if out.Sources == nil {
			out.Sources = []guidedata.Source{}
		}
		for _, c := range g.Cards {
			out.Contents = append(out.Contents, contentOut{ID: c.ID, Title: c.Title})
		}
		for _, q := range g.Meta.Questions {
			out.Questions = append(out.Questions, questionOut{ID: q.ID, Text: q.Text, AnsweredIn: q.AnsweredIn, CardTitle: cardTitle[q.AnsweredIn]})
		}
		for _, c := range g.Cards {
			recs := cardEvidence(r, c.ID)
			co := cardOut{Title: c.Title, Coverage: Coverage(recs), Records: []evidenceOut{}}
			for _, e := range recs {
				eo := evidenceOut{ID: e.ID, Badge: e.Badge, Canonical: guidedata.CanonicalLine(e), Test: e.Test, RunURL: e.Run}
				if e.Run != "" {
					eo.Run = guidedata.RunText(e.Run)
				}
				if m := e.Measurement; m != nil {
					eo.Measurement = fmt.Sprintf("%d samples, %s, in %s. %s", m.Samples, m.Statistic, m.Unit, m.Protocol)
				}
				co.Records = append(co.Records, eo)
			}
			out.Cards[c.ID] = co
		}
		for _, c := range r.Checklist {
			if c.Guide != g.Meta.ID {
				continue
			}
			out.Checklist = append(out.Checklist, checklistOut{ID: c.ID, Type: c.Type, Card: c.Card, Text: c.Text, Tags: guidedata.FilterTags(c.AppliesWhen)})
		}
		sort.Slice(out.Checklist, func(i, j int) bool { return out.Checklist[i].ID < out.Checklist[j].ID })
		out.References = append(out.References, guideRefs(r, g)...)
		guides[g.Meta.ID] = out
	}
	return marshal(map[string]any{
		"generatedBy": "guidekit generate",
		"active":      r.Versions.Active,
		"proofNote":   ProofNote,
		"legend":      guidedata.LegendText,
		"guides":      guides,
	})
}

// guideRefs lists a guide's official documents: the baseline's first, then those for a later release.
func guideRefs(r *guidedata.Repo, g *guidedata.Guide) []refOut {
	var refs []refOut
	for _, e := range entriesFor(r, r.Versions.Active, nil) {
		if contains(e.Guides, g.Meta.ID) {
			refs = append(refs, refOut{ID: e.ID, Title: e.Title, URL: e.URL, Version: versionOf(r, r.Versions.Active, e), DocVersion: e.DocVersion, Pinned: e.Pinned, Purpose: e.Purpose})
		}
	}
	// Documents for a later release: no baseline pins them yet, so they carry no version.
	for _, e := range r.References.Entries {
		if len(e.Baselines) == 0 && contains(e.Guides, g.Meta.ID) {
			refs = append(refs, refOut{ID: e.ID, Title: e.Title, URL: e.URL, Pinned: false, Purpose: e.Purpose, Later: true})
		}
	}
	return refs
}
