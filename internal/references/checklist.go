package references

import "github.com/pyahu/initializer/internal/guidedata"

// ChecklistPath is the checklist the project generator reads (services/start packs it with the templates).
const ChecklistPath = "templates/checklist.json"

type checklistPart struct {
	Title  string           `json:"title"`
	Guides []checklistGuide `json:"guides"`
}

type checklistGuide struct {
	ID    string          `json:"id"`
	Title string          `json:"title"`
	Items []checklistItem `json:"items"`
}

type checklistItem struct {
	ID          string           `json:"id"`
	Card        string           `json:"card"`
	Step        int              `json:"step"`
	Type        string           `json:"type"`
	Text        string           `json:"text"`
	AppliesWhen guidedata.Filter `json:"appliesWhen"`
}

// ChecklistJSON renders every checklist item grouped by part and guide, in the order of the guides index, with
// the filter that decides whether it applies to a project.
func ChecklistJSON(r *guidedata.Repo) ([]byte, error) {
	parts, err := Parts(r)
	if err != nil {
		return nil, err
	}
	byID := map[string]*guidedata.Guide{}
	for _, g := range r.Guides {
		byID[g.Meta.ID] = g
	}
	out := []checklistPart{}
	for _, p := range parts {
		cp := checklistPart{Title: p.Title, Guides: []checklistGuide{}}
		for _, id := range p.Guides {
			g := byID[id]
			steps := stepIndex(g)
			cg := checklistGuide{ID: id, Title: g.Title, Items: []checklistItem{}}
			for _, c := range checklistFor(r, id) {
				f := c.AppliesWhen
				if f == nil {
					f = guidedata.Filter{}
				}
				cg.Items = append(cg.Items, checklistItem{ID: c.ID, Card: c.Card, Step: steps[c.Card], Type: c.Type, Text: c.Text, AppliesWhen: f})
			}
			cp.Guides = append(cp.Guides, cg)
		}
		out = append(out, cp)
	}
	return marshal(map[string]any{"generatedBy": "guidekit generate", "site": Site, "parts": out})
}
