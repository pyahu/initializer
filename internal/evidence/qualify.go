package evidence

import (
	"bytes"
	"fmt"
	"os"
	"path/filepath"
	"sort"

	"go.yaml.in/yaml/v3"

	"github.com/pyahu/initializer/internal/guidedata"
)

// Qualify recomputes every component's qualification from the eligible records (5.3)
// and the latest fixture reports, keyed by fixture name.
func Qualify(r *guidedata.Repo, reports map[string]*Report) map[string]map[string]guidedata.Qualification {
	out := map[string]map[string]guidedata.Qualification{}
	for _, rev := range r.Versions.Revisions {
		q := map[string]guidedata.Qualification{}
		for key, c := range rev.Components {
			caps := map[string][]string{}
			for _, e := range r.Evidence {
				if !eligible(r, e, rev.ID, reports) || !containsStr(e.Components, key) {
					continue
				}
				for _, cap := range e.Capabilities {
					if containsStr(c.Claims, cap) {
						caps[cap] = append(caps[cap], e.ID)
					}
				}
			}
			for k := range caps {
				sort.Strings(caps[k])
			}
			state := "provisional"
			n := 0
			for _, cl := range c.Claims {
				if len(caps[cl]) > 0 {
					n++
				}
			}
			switch {
			case n == len(c.Claims):
				state = "qualified"
			case n > 0:
				state = "partial"
			}
			q[key] = guidedata.Qualification{State: state, Capabilities: caps}
		}
		out[rev.ID] = q
	}
	return out
}

func eligible(r *guidedata.Repo, e guidedata.EvidenceRecord, revision string, reports map[string]*Report) bool {
	if !e.Exercised() || e.Baseline != revision {
		return false
	}
	for _, c := range e.Capabilities {
		if !containsStr(guidedata.BadgeCapabilities[e.Badge], c) {
			return false
		}
	}
	fx := r.Fixture(e.Fixture)
	if fx == nil || !containsStr(fx.RequiredTests, e.Test) {
		return false
	}
	rep := reports[e.Fixture]
	return rep != nil && rep.Baseline == revision && rep.Passed(e.Test)
}

// Differences lists components whose qualification in versions.yaml differs.
func Differences(r *guidedata.Repo, q map[string]map[string]guidedata.Qualification) []string {
	var d []string
	for _, rev := range r.Versions.Revisions {
		for key, c := range rev.Components {
			want := q[rev.ID][key]
			if c.Qualification.State != want.State || fmt.Sprint(norm(c.Qualification.Capabilities)) != fmt.Sprint(norm(want.Capabilities)) {
				d = append(d, fmt.Sprintf("%s %s: file says %s %v, evidence gives %s %v", rev.ID, key, c.Qualification.State, norm(c.Qualification.Capabilities), want.State, norm(want.Capabilities)))
			}
		}
	}
	sort.Strings(d)
	return d
}

func norm(m map[string][]string) map[string][]string {
	if m == nil {
		return map[string][]string{}
	}
	return m
}

// WriteQualification rewrites only the qualification blocks of data/versions.yaml,
// keeping every other node, comment and style.
func WriteQualification(r *guidedata.Repo, q map[string]map[string]guidedata.Qualification) error {
	path := filepath.Join(r.Root, "data", "versions.yaml")
	b, err := os.ReadFile(path)
	if err != nil {
		return err
	}
	var doc yaml.Node
	if err := yaml.Unmarshal(b, &doc); err != nil {
		return err
	}
	revs := mapGet(doc.Content[0], "revisions")
	for _, revNode := range revs.Content {
		id := mapGet(revNode, "id").Value
		comps := mapGet(revNode, "components")
		for i := 0; i < len(comps.Content); i += 2 {
			key := comps.Content[i].Value
			want := q[id][key]
			qn := mapGet(comps.Content[i+1], "qualification")
			*qn = *qualNode(want)
		}
	}
	var buf bytes.Buffer
	enc := yaml.NewEncoder(&buf)
	enc.SetIndent(2)
	if err := enc.Encode(&doc); err != nil {
		return err
	}
	return os.WriteFile(path, buf.Bytes(), 0o644)
}

func qualNode(q guidedata.Qualification) *yaml.Node {
	caps := &yaml.Node{Kind: yaml.MappingNode, Tag: "!!map"}
	keys := make([]string, 0, len(q.Capabilities))
	for k := range q.Capabilities {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	if len(keys) == 0 {
		caps.Style = yaml.FlowStyle
	}
	for _, k := range keys {
		list := &yaml.Node{Kind: yaml.SequenceNode, Tag: "!!seq", Style: yaml.FlowStyle}
		for _, id := range q.Capabilities[k] {
			list.Content = append(list.Content, &yaml.Node{Kind: yaml.ScalarNode, Tag: "!!str", Value: id})
		}
		caps.Content = append(caps.Content, &yaml.Node{Kind: yaml.ScalarNode, Tag: "!!str", Value: k}, list)
	}
	return &yaml.Node{Kind: yaml.MappingNode, Tag: "!!map", Content: []*yaml.Node{
		{Kind: yaml.ScalarNode, Tag: "!!str", Value: "state"}, {Kind: yaml.ScalarNode, Tag: "!!str", Value: q.State},
		{Kind: yaml.ScalarNode, Tag: "!!str", Value: "capabilities"}, caps,
	}}
}

func mapGet(n *yaml.Node, key string) *yaml.Node {
	for i := 0; i+1 < len(n.Content); i += 2 {
		if n.Content[i].Value == key {
			return n.Content[i+1]
		}
	}
	return &yaml.Node{}
}
