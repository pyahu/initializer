package guidedata

import (
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
)

// Guide is a parsed guide page.
type Guide struct {
	Path        string
	Title       string
	Description string
	Meta        GuideMeta
	Cards       []Card
	Recipes     []Recipe
	Diagrams    []string // diagram component names used, in order
	Legends     [][]string
	Anchors     []string // every anchor id on the page, retired ones included
}

// Card is a decision card.
type Card struct {
	ID    string
	Title string
	Line  int
}

// Recipe is one <Recipe /> use.
type Recipe struct {
	ID, Path, Start, End, Lang string
	Line                       int
}

// CardParts is the fixed order of a card's sections.
var CardParts = []string{"Problem", "Solution", "Use cases", "Trade-offs", "Evidence"}

// GuideComponents are the components a guide may use besides diagrams.
var GuideComponents = map[string]bool{
	"GuideStatus": true, "Evidence": true, "Recipe": true, "Checklist": true,
	"GuideReferences": true, "DiagramLegend": true,
}

var (
	reImport    = regexp.MustCompile(`^import ([A-Z][A-Za-z0-9]*) from '(\.\./\.\./\.\./components/[A-Za-z0-9/]+\.astro)';$`)
	reAnchor    = regexp.MustCompile(`^<a id="([a-z0-9-]+)"></a>$`)
	reTagStart  = regexp.MustCompile(`^<([A-Z][A-Za-z0-9]*)(\s|/>|$)`)
	reAttr      = regexp.MustCompile(`([a-zA-Z]+)=(?:"([^"]*)"|\{(\[[^\]]*\])\})`)
	reCodeSpan  = regexp.MustCompile("`[^`]*`")
	reDashChars = regexp.MustCompile("[–—]")
)

// ParseGuide reads and checks one guide file. errs collects every rule violation.
func ParseGuide(path string, s *Schemas, retiredCards map[string]string) (*Guide, []error) {
	var errs []error
	fail := func(line int, format string, a ...any) {
		errs = append(errs, fmt.Errorf("%s:%d: %s", path, line, fmt.Sprintf(format, a...)))
	}
	b, err := os.ReadFile(path)
	if err != nil {
		return nil, []error{err}
	}
	text := string(b)
	lines := strings.Split(text, "\n")
	for i, l := range lines {
		if reDashChars.MatchString(l) {
			fail(i+1, "em or en dash; write ranges as \"2 to 4\"")
		}
	}
	if len(lines) < 3 || lines[0] != "---" {
		return nil, append(errs, fmt.Errorf("%s: missing frontmatter", path))
	}
	end := -1
	for i := 1; i < len(lines); i++ {
		if lines[i] == "---" {
			end = i
			break
		}
	}
	if end < 0 {
		return nil, append(errs, fmt.Errorf("%s: unterminated frontmatter", path))
	}
	fm, err := ParseYAML(path, []byte(strings.Join(lines[1:end], "\n")))
	if err != nil {
		return nil, append(errs, err)
	}
	g := &Guide{Path: path}
	m, _ := fm.(map[string]any)
	for k := range m {
		switch k {
		case "title", "description", "tableOfContents", "guide":
		default:
			fail(1, "frontmatter key %q not allowed (Starlight draft drops pages from the llms export)", k)
		}
	}
	g.Title, _ = m["title"].(string)
	g.Description, _ = m["description"].(string)
	if g.Title == "" || g.Description == "" {
		fail(1, "title and description are required")
	}
	if toc, ok := m["tableOfContents"].(map[string]any); !ok || fmt.Sprint(toc["maxHeadingLevel"]) != "2" || len(toc) != 1 {
		fail(1, "tableOfContents must be { maxHeadingLevel: 2 }")
	}
	if err := s.Validate("guide-frontmatter", path, m["guide"]); err != nil {
		return nil, append(errs, err)
	}
	if err := Decode(m["guide"], &g.Meta); err != nil {
		return nil, append(errs, err)
	}
	if want := strings.TrimSuffix(filepath.Base(path), ".mdx"); g.Meta.ID != want {
		fail(1, "guide.id %q must equal the file name %q", g.Meta.ID, want)
	}

	// Tokenize the body into significant lines, skipping fenced code.
	type tok struct {
		line int
		text string
		kind string // import, anchor, h2, h3, tag, text, blank, fence
		name string
		attr map[string]string
	}
	var toks []tok
	imports := map[string]string{}
	inFence, seenContent := false, false
	for i := end + 1; i < len(lines); i++ {
		l := lines[i]
		n := i + 1
		if strings.HasPrefix(l, "```") {
			if !inFence {
				lang := strings.TrimSpace(strings.TrimPrefix(l, "```"))
				if lang == "mermaid" {
					fail(n, "Mermaid is not used; draw the diagram with the kit")
				}
			}
			inFence = !inFence
			toks = append(toks, tok{line: n, kind: "fence", text: l})
			continue
		}
		if inFence {
			continue
		}
		if strings.TrimSpace(l) == "" {
			toks = append(toks, tok{line: n, kind: "blank"})
			continue
		}
		if strings.HasPrefix(l, "import ") {
			mm := reImport.FindStringSubmatch(l)
			if mm == nil || seenContent {
				fail(n, "imports go first and only from ../../../components/")
				continue
			}
			imports[mm[1]] = mm[2]
			continue
		}
		seenContent = true
		if strings.HasPrefix(l, "export ") || strings.Contains(l, "{/*") {
			fail(n, "export and MDX comments are not allowed")
			continue
		}
		if mm := reAnchor.FindStringSubmatch(l); mm != nil {
			toks = append(toks, tok{line: n, kind: "anchor", name: mm[1]})
			continue
		}
		if strings.HasPrefix(l, "## ") {
			toks = append(toks, tok{line: n, kind: "h2", text: strings.TrimPrefix(l, "## ")})
			continue
		}
		if strings.HasPrefix(l, "### ") {
			toks = append(toks, tok{line: n, kind: "h3", text: strings.TrimPrefix(l, "### ")})
			continue
		}
		if strings.HasPrefix(l, "#") {
			fail(n, "only ## and ### headings are allowed")
			continue
		}
		if mm := reTagStart.FindStringSubmatch(l); mm != nil {
			full := l
			for !strings.HasSuffix(strings.TrimSpace(full), "/>") && i+1 < len(lines) {
				i++
				full += " " + strings.TrimSpace(lines[i])
			}
			if !strings.HasSuffix(strings.TrimSpace(full), "/>") {
				fail(n, "components must be self-closing")
				continue
			}
			name := mm[1]
			if _, ok := imports[name]; !ok {
				fail(n, "component %s is not imported", name)
			}
			if !GuideComponents[name] && !strings.HasSuffix(name, "Diagram") {
				fail(n, "component %s is not allowed in a guide", name)
			}
			attrs := map[string]string{}
			inner := strings.TrimSuffix(strings.TrimSpace(strings.TrimPrefix(full, "<"+name)), "/>")
			rest := reAttr.ReplaceAllStringFunc(inner, func(a string) string {
				am := reAttr.FindStringSubmatch(a)
				if am[3] != "" {
					if name != "DiagramLegend" || am[1] != "items" {
						fail(n, "only DiagramLegend items may use an expression")
					}
					attrs[am[1]] = am[3]
				} else {
					attrs[am[1]] = am[2]
				}
				return ""
			})
			if strings.TrimSpace(rest) != "" {
				fail(n, "props must be string literals: %q", strings.TrimSpace(rest))
			}
			toks = append(toks, tok{line: n, kind: "tag", name: name, attr: attrs})
			continue
		}
		prose := reCodeSpan.ReplaceAllString(l, "")
		if strings.ContainsAny(prose, "{}") {
			fail(n, "braces outside code spans are MDX expressions; put them in backticks")
		}
		if regexp.MustCompile(`<[A-Za-z/!]`).MatchString(prose) {
			fail(n, "raw HTML or JSX in prose; use a component or a code span")
		}
		toks = append(toks, tok{line: n, kind: "text", text: l})
	}
	if inFence {
		fail(len(lines), "unterminated code fence")
	}
	for name, imp := range imports {
		if !GuideComponents[name] && !(strings.HasSuffix(name, "Diagram") && strings.HasPrefix(imp, "../../../components/diagrams/")) {
			fail(1, "import %s is not an allowed component", name)
		}
		if GuideComponents[name] && imp != "../../../components/"+name+".astro" {
			fail(1, "import %s must come from ../../../components/%s.astro", name, name)
		}
	}

	// Structure.
	sig := []tok{}
	for _, t := range toks {
		if t.kind != "blank" {
			sig = append(sig, t)
		}
	}
	p := 0
	peek := func() *tok {
		if p < len(sig) {
			return &sig[p]
		}
		return nil
	}
	lineAt := func(t *tok) int {
		if t == nil {
			return len(lines)
		}
		return t.line
	}
	expectTag := func(name, attr, val string) bool {
		t := peek()
		if t == nil || t.kind != "tag" || t.name != name || t.attr[attr] != val {
			ln := len(lines)
			if t != nil {
				ln = t.line
			}
			fail(ln, "expected <%s %s=%q />", name, attr, val)
			return false
		}
		p++
		return true
	}
	expectTag("GuideStatus", "guide", g.Meta.ID)
	if t := peek(); t == nil || t.kind != "h2" || t.text != "Decision summary" {
		fail(lineAt(peek()), "expected ## Decision summary")
		return g, errs
	}
	p++
	// Decision summary content until the first anchor.
	for t := peek(); t != nil && t.kind != "anchor" && !(t.kind == "h2"); t = peek() {
		if t.kind == "h3" {
			fail(t.line, "no ### in the decision summary")
		}
		if t.kind == "tag" {
			if strings.HasSuffix(t.name, "Diagram") {
				g.Diagrams = append(g.Diagrams, t.name)
			} else if t.name == "DiagramLegend" {
				g.Legends = append(g.Legends, parseItems(t.attr["items"]))
			} else {
				fail(t.line, "%s does not belong in the decision summary", t.name)
			}
		}
		p++
	}
	// Cards.
	for t := peek(); t != nil && t.kind == "anchor"; t = peek() {
		var anchors []tok
		for t = peek(); t != nil && t.kind == "anchor"; t = peek() {
			anchors = append(anchors, *t)
			p++
		}
		card := anchors[len(anchors)-1]
		for _, a := range anchors[:len(anchors)-1] {
			if retiredCards[a.name] != card.name {
				fail(a.line, "anchor %s is not a retired card replaced by %s", a.name, card.name)
			}
			g.Anchors = append(g.Anchors, a.name)
		}
		g.Anchors = append(g.Anchors, card.name)
		h := peek()
		if h == nil || h.kind != "h2" {
			fail(card.line, "an anchor must be followed by its ## heading")
			return g, errs
		}
		if !strings.HasPrefix(card.name, strings.ToLower(g.Meta.Prefix)+"-") {
			fail(card.line, "card id %s must start with %s-", card.name, strings.ToLower(g.Meta.Prefix))
		}
		g.Cards = append(g.Cards, Card{ID: card.name, Title: h.text, Line: h.line})
		p++
		for i, part := range CardParts {
			t := peek()
			if t == nil || t.kind != "h3" || t.text != part {
				fail(lineAt(t), "card %s: expected ### %s", card.name, part)
				return g, errs
			}
			p++
			if i == len(CardParts)-1 {
				if !expectTag("Evidence", "card", card.name) {
					return g, errs
				}
				if t := peek(); t != nil && t.kind != "anchor" && t.kind != "h2" {
					fail(t.line, "card %s: the Evidence section holds only <Evidence />", card.name)
				}
				break
			}
			for t := peek(); t != nil && t.kind != "h3" && t.kind != "h2" && t.kind != "anchor"; t = peek() {
				if t.kind == "tag" {
					if strings.HasSuffix(t.name, "Diagram") {
						g.Diagrams = append(g.Diagrams, t.name)
					} else if t.name == "DiagramLegend" {
						g.Legends = append(g.Legends, parseItems(t.attr["items"]))
					} else {
						fail(t.line, "%s does not belong in a card", t.name)
					}
				}
				p++
			}
		}
	}
	if len(g.Cards) == 0 {
		fail(len(lines), "a guide needs at least one card")
	}
	// Recipes.
	if t := peek(); t == nil || t.kind != "h2" || t.text != "Recipes" {
		fail(lineAt(t), "expected ## Recipes after the cards")
		return g, errs
	}
	p++
	for t := peek(); t != nil && t.kind == "h3"; t = peek() {
		p++
		r := peek()
		if r == nil || r.kind != "tag" || r.name != "Recipe" {
			fail(t.line, "recipe %q: expected <Recipe />", t.text)
			return g, errs
		}
		g.Recipes = append(g.Recipes, Recipe{ID: r.attr["id"], Path: r.attr["path"], Start: r.attr["start"], End: r.attr["end"], Lang: r.attr["lang"], Line: r.line})
		if r.attr["id"] == "" || r.attr["path"] == "" || r.attr["start"] == "" || r.attr["lang"] == "" {
			fail(r.line, "Recipe needs id, path, start, lang")
		}
		p++
		gotExpected, gotFails := false, false
		for t := peek(); t != nil && t.kind != "h3" && t.kind != "h2"; t = peek() {
			if t.kind == "text" && strings.HasPrefix(t.text, "**Expected:**") {
				gotExpected = true
			}
			if t.kind == "text" && strings.HasPrefix(t.text, "**If it fails:**") && gotExpected {
				gotFails = true
			}
			if t.kind == "tag" {
				fail(t.line, "one component per recipe")
			}
			p++
		}
		if !gotExpected || !gotFails {
			fail(t.line, "recipe %q needs **Expected:** then **If it fails:**", t.text)
		}
	}
	for _, sec := range []struct{ h, tag string }{{"Checklist", "Checklist"}, {"References", "GuideReferences"}} {
		t := peek()
		if t == nil || t.kind != "h2" || t.text != sec.h {
			fail(lineAt(t), "expected ## %s", sec.h)
			return g, errs
		}
		p++
		expectTag(sec.tag, "guide", g.Meta.ID)
	}
	if t := peek(); t != nil {
		fail(t.line, "nothing may follow ## References")
	}
	return g, errs
}

func parseItems(s string) []string {
	s = strings.Trim(s, "[]")
	var out []string
	for _, p := range strings.Split(s, ",") {
		p = strings.Trim(strings.TrimSpace(p), "'\"")
		if p != "" {
			out = append(out, p)
		}
	}
	return out
}

// GuideFiles lists guide MDX files.
func GuideFiles(root string) ([]string, error) {
	files, err := filepath.Glob(filepath.Join(root, "guides/src/content/docs/guides/*.mdx"))
	sort.Strings(files)
	return files, err
}
