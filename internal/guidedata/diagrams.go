package guidedata

import (
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
)

// DiagramDef is what guidekit reads from one diagram component file.
type DiagramDef struct {
	Component   string
	File        string
	ID          string
	Caption     string
	Description string
	SVGIDs      []string
}

var (
	reDiagramCall = regexp.MustCompile(`(?s)<Diagram\s+id="([a-z0-9-]+)"\s+caption="([^"]+)"\s+description="([^"]+)"\s*>`)
	reSVGID       = regexp.MustCompile(`\sid="([^"]+)"`)
)

// LegendText is the single source of the legend labels, rendered by DiagramLegend through
// guides.json and asserted in the exports.
var LegendText = map[string]string{
	"recommended": "Recommended: what the guide recommends, or what changed.",
	"context":     "Context: existing parts.",
	"optional":    "Optional: an optional or later step (dashed).",
	"failure":     "Failure: what breaks (red, short dash, marked ✕).",
	"dependency":  "Dependency: the arrow points from the dependent to its dependency.",
}

// LoadDiagrams parses guides/src/components/diagrams/*Diagram.astro.
func LoadDiagrams(root string) (map[string]DiagramDef, error) {
	files, _ := filepath.Glob(filepath.Join(root, "guides/src/components/diagrams/*Diagram.astro"))
	sort.Strings(files)
	out := map[string]DiagramDef{}
	seen := map[string]string{}
	for _, f := range files {
		b, err := os.ReadFile(f)
		if err != nil {
			return nil, err
		}
		src := string(b)
		m := reDiagramCall.FindAllStringSubmatch(src, -1)
		if len(m) != 1 {
			return nil, fmt.Errorf("%s: expected exactly one <Diagram id caption description> call", f)
		}
		d := DiagramDef{Component: strings.TrimSuffix(filepath.Base(f), ".astro"), File: f, ID: m[0][1], Caption: m[0][2], Description: m[0][3]}
		if prev, dup := seen[d.ID]; dup {
			return nil, fmt.Errorf("%s: diagram id %s already used in %s", f, d.ID, prev)
		}
		seen[d.ID] = f
		if i := strings.Index(src, "<svg"); i >= 0 {
			for _, id := range reSVGID.FindAllStringSubmatch(src[i:], -1) {
				d.SVGIDs = append(d.SVGIDs, id[1])
				if !strings.HasPrefix(id[1], d.ID+"-") {
					return nil, fmt.Errorf("%s: svg id %q must start with %s-", f, id[1], d.ID)
				}
			}
		} else {
			return nil, fmt.Errorf("%s: no <svg>", f)
		}
		if !strings.Contains(src, `viewBox="0 0 1000 `) {
			return nil, fmt.Errorf("%s: the svg viewBox must be 1000 wide", f)
		}
		if reDashChars.MatchString(src) {
			return nil, fmt.Errorf("%s: em or en dash", f)
		}
		out[d.Component] = d
	}
	return out, nil
}
