package references

import (
	"fmt"
	"sort"

	"github.com/pyahu/initializer/internal/guidedata"
)

type evidenceOut struct {
	ID          string   `json:"id"`
	Badge       string   `json:"badge"`
	Canonical   string   `json:"canonical"`
	Reason      string   `json:"reason,omitempty"`
	Test        string   `json:"test,omitempty"`
	Commands    []string `json:"commands,omitempty"`
	Environment string   `json:"environment,omitempty"`
	Run         string   `json:"run,omitempty"`
	RunURL      string   `json:"runUrl,omitempty"`
	CommitURL   string   `json:"commitUrl,omitempty"`
	Measurement string   `json:"measurement,omitempty"`
	Cluster     string   `json:"cluster,omitempty"`
}

type cardOut struct {
	Title    string        `json:"title"`
	Coverage string        `json:"coverage"`
	Records  []evidenceOut `json:"records"`
}

type checklistOut struct {
	ID    string   `json:"id"`
	Type  string   `json:"type"`
	Card  string   `json:"card"`
	Text  string   `json:"text"`
	Claim string   `json:"claim"`
	Tests []string `json:"tests"`
	Tags  []string `json:"tags"`
}

type refOut struct {
	ID      string `json:"id"`
	Title   string `json:"title"`
	URL     string `json:"url"`
	Version string `json:"version"`
	Pinned  bool   `json:"pinned"`
	Purpose string `json:"purpose"`
	Later   bool   `json:"later"`
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
	ID           string                `json:"id"`
	Title        string                `json:"title"`
	State        string                `json:"state"`
	Baseline     string                `json:"baseline"`
	Status       guidedata.GuideStatus `json:"status"`
	Verification string                `json:"verification"`
	Coverage     string                `json:"coverage"`
	Sources      []guidedata.Source    `json:"sources"`
	Questions    []questionOut         `json:"questions"`
	Cards        map[string]cardOut    `json:"cards"`
	Contents     []contentOut          `json:"contents"`
	Checklist    []checklistOut        `json:"checklist"`
	References   []refOut              `json:"references"`
}

// BadgeOrder orders badges for coverage lines.
var BadgeOrder = []string{"cluster-run", "measured", "reproduced", "compiled", "not-exercised"}

// Coverage renders "3 claims: 2 reproduced, 1 not exercised".
func Coverage(recs []guidedata.EvidenceRecord) string {
	n := map[string]int{}
	for _, e := range recs {
		n[e.Badge]++
	}
	s := fmt.Sprintf("%d claim", len(recs))
	if len(recs) != 1 {
		s += "s"
	}
	sep := ": "
	for _, b := range BadgeOrder {
		if n[b] > 0 {
			label := b
			if b == "not-exercised" {
				label = "not exercised"
			}
			s += fmt.Sprintf("%s%d %s", sep, n[b], label)
			sep = ", "
		}
	}
	return s
}

// GuidesJSON renders guides/src/generated/guides.json.
func GuidesJSON(r *guidedata.Repo) ([]byte, error) {
	guides := map[string]guideOut{}
	for _, g := range r.Guides {
		cardTitle := map[string]string{}
		for _, c := range g.Cards {
			cardTitle[c.ID] = c.Title
		}
		out := guideOut{ID: g.Meta.ID, Title: g.Title, State: g.Meta.State, Baseline: g.Meta.Baseline,
			Status: r.Status(g), Sources: g.Meta.Sources, Cards: map[string]cardOut{},
			Questions: []questionOut{}, Contents: []contentOut{}, Checklist: []checklistOut{}, References: []refOut{}}
		if out.Sources == nil {
			out.Sources = []guidedata.Source{}
		}
		if v := g.Meta.Verification; v != nil {
			out.Verification = fmt.Sprintf("%s, %s, baseline %s", guidedata.RunText(v.Run, v.Commit), v.Date, v.Baseline)
		}
		for _, c := range g.Cards {
			out.Contents = append(out.Contents, contentOut{ID: c.ID, Title: c.Title})
		}
		for _, q := range g.Meta.Questions {
			out.Questions = append(out.Questions, questionOut{ID: q.ID, Text: q.Text, AnsweredIn: q.AnsweredIn, CardTitle: cardTitle[q.AnsweredIn]})
		}
		var all []guidedata.EvidenceRecord
		for _, c := range g.Cards {
			var recs []guidedata.EvidenceRecord
			for _, e := range r.Evidence {
				if e.Card == c.ID {
					recs = append(recs, e)
				}
			}
			all = append(all, recs...)
			co := cardOut{Title: c.Title, Coverage: Coverage(recs), Records: []evidenceOut{}}
			for _, e := range recs {
				eo := evidenceOut{ID: e.ID, Badge: e.Badge, Canonical: guidedata.CanonicalLine(e), Reason: e.Reason}
				if e.Exercised() {
					eo.Test, eo.Commands, eo.Environment = e.Test, e.Commands, e.Environment
					eo.Run = guidedata.RunText(e.Run, e.Commit)
					eo.RunURL, eo.CommitURL = e.Run, guidedata.CommitURL(e.Commit)
				}
				if m := e.Measurement; m != nil {
					eo.Measurement = fmt.Sprintf("%d samples, %s, in %s. %s", m.Samples, m.Statistic, m.Unit, m.Protocol)
				}
				if c := e.Cluster; c != nil {
					eo.Cluster = fmt.Sprintf("%s, Kubernetes %s, %s", c.Kind, c.KubernetesVersion, c.CNI)
				}
				co.Records = append(co.Records, eo)
			}
			out.Cards[c.ID] = co
		}
		out.Coverage = Coverage(all)
		for _, c := range r.Checklist {
			if c.Guide != g.Meta.ID {
				continue
			}
			tests := c.Tests
			if tests == nil {
				tests = []string{}
			}
			out.Checklist = append(out.Checklist, checklistOut{ID: c.ID, Type: c.Type, Card: c.Card, Text: c.Text, Claim: c.Claim, Tests: tests, Tags: guidedata.FilterTags(c.AppliesWhen)})
		}
		sort.Slice(out.Checklist, func(i, j int) bool { return out.Checklist[i].ID < out.Checklist[j].ID })
		for _, e := range entriesFor(r, g.Meta.Baseline, nil) {
			if contains(e.Guides, g.Meta.ID) {
				out.References = append(out.References, refOut{ID: e.ID, Title: e.Title, URL: e.URL, Version: versionOf(r, g.Meta.Baseline, e), Pinned: e.Pinned, Purpose: e.Purpose})
			}
		}
		// Documents for a later release: no baseline pins them yet, so they carry no version.
		for _, e := range r.References.Entries {
			if len(e.Baselines) == 0 && contains(e.Guides, g.Meta.ID) {
				out.References = append(out.References, refOut{ID: e.ID, Title: e.Title, URL: e.URL, Pinned: false, Purpose: e.Purpose, Later: true})
			}
		}
		guides[g.Meta.ID] = out
	}
	return marshal(map[string]any{
		"generatedBy": "guidekit generate",
		"active":      r.Versions.Active,
		"legend":      guidedata.LegendText,
		"guides":      guides,
	})
}
