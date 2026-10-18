package references

import (
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"

	"github.com/pyahu/initializer/internal/guidedata"
)

// SkillName is the skill's folder and frontmatter name.
const SkillName = "pyahu-production-guides"

// SkillDir is where the skill is generated, relative to the repository root.
const SkillDir = "skills/" + SkillName

// Site is the public site the skill links back to.
const Site = "https://initializer.pyahu.io"

// Part is one group of the guides index page (guides/src/content/docs/guides.mdx).
type Part struct {
	Title  string
	Lead   string
	Guides []string
}

var (
	rePartHeading = regexp.MustCompile(`^## Part (\d+): (.+)$`)
	rePartGuide   = regexp.MustCompile(`^- \[[^\]]+\]\(/guides/([a-z0-9-]+)/\)`)
	reSiteLink    = regexp.MustCompile(`\]\((/[^)\s]*)\)`)
	reTagLine     = regexp.MustCompile(`^<([A-Z][A-Za-z0-9]*)\b`)
	reTagAttr     = regexp.MustCompile(`([a-zA-Z]+)="([^"]*)"`)
	reAnchorLine  = regexp.MustCompile(`^<a id="[a-z0-9-]+"></a>$`)
)

// ChecklistTypes orders and names the checklist groups, as the site's Checklist component does.
var ChecklistTypes = [][2]string{
	{"generated-config", "Generated configuration"},
	{"verified-behaviour", "Verified behaviour"},
	{"deployment-prerequisite", "Deployment prerequisites"},
	{"operator-task", "Recurring operator tasks"},
}

// Parts reads the parts of the guides index page in order.
func Parts(r *guidedata.Repo) ([]Part, error) {
	path := filepath.Join(r.Root, "guides/src/content/docs/guides.mdx")
	b, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var parts []Part
	for _, l := range strings.Split(string(b), "\n") {
		if m := rePartHeading.FindStringSubmatch(l); m != nil {
			parts = append(parts, Part{Title: m[2]})
			continue
		}
		if len(parts) == 0 {
			continue
		}
		p := &parts[len(parts)-1]
		if m := rePartGuide.FindStringSubmatch(l); m != nil {
			p.Guides = append(p.Guides, m[1])
		} else if p.Lead == "" && strings.TrimSpace(l) != "" && !strings.HasPrefix(l, "- ") {
			p.Lead = l
		}
	}
	listed := map[string]bool{}
	for _, p := range parts {
		for _, id := range p.Guides {
			if listed[id] {
				return nil, fmt.Errorf("%s: guide %s listed twice", path, id)
			}
			listed[id] = true
		}
	}
	for _, g := range r.Guides {
		if !listed[g.Meta.ID] {
			return nil, fmt.Errorf("%s: guide %s is in no part", path, g.Meta.ID)
		}
	}
	return parts, nil
}

// Skill renders the agent skill: SKILL.md, one Markdown file per guide and checklist.md.
func Skill(r *guidedata.Repo) (Outputs, error) {
	parts, err := Parts(r)
	if err != nil {
		return nil, err
	}
	byID := map[string]*guidedata.Guide{}
	for _, g := range r.Guides {
		byID[g.Meta.ID] = g
	}
	out := Outputs{}
	for _, g := range r.Guides {
		b, err := skillGuide(r, g)
		if err != nil {
			return nil, err
		}
		out[SkillDir+"/guides/"+g.Meta.ID+".md"] = b
	}
	out[SkillDir+"/SKILL.md"] = skillIndex(r, parts, byID)
	out[SkillDir+"/checklist.md"] = skillChecklist(r, parts, byID)
	return out, nil
}

func baselineLine(r *guidedata.Repo) string {
	rev := r.Versions.Revision(r.Versions.Active)
	if rev == nil {
		return "baseline " + r.Versions.Active
	}
	java, _, _ := strings.Cut(rev.Components["java"].Version, ".")
	return fmt.Sprintf("baseline %s: Java %s, Spring Boot %s", r.Versions.Active, java, rev.Components["spring-boot"].Version)
}

func skillIndex(r *guidedata.Repo, parts []Part, byID map[string]*guidedata.Guide) []byte {
	var b strings.Builder
	b.WriteString("---\nname: " + SkillName + "\n")
	b.WriteString("description: Production practices for Spring Boot 4.1 services on Java 25, Kubernetes and a Next.js frontend, from the Pyahu Initializer guides. Use when building, configuring, reviewing or deploying a Spring Boot service, including build and architecture, tests, REST and gRPC APIs, PostgreSQL with JDBC, JPA or jOOQ, Kafka, RabbitMQ, caching, JVM sizing, configuration, observability, security, container images, CI, Kubernetes, local development, monorepos and Next.js.\n---\n\n")
	b.WriteString(Header + "\n\n")
	b.WriteString("# Pyahu production guides\n\n")
	b.WriteString("The practices Pyahu follows in every service it generates, written as guides for people and tools alike.\n")
	b.WriteString("Each guide answers a few questions, one step per question. The code in them is cut from a service that CI\n")
	b.WriteString("builds and tests on every change. Written for " + baselineLine(r) + ".\n")
	b.WriteString("The same guides are on the web at " + Site + "/guides/.\n\n")
	b.WriteString("## How to use this skill\n\n")
	b.WriteString("1. Find the question closest to the task in the index below. Open only that guide and read the step it\n")
	b.WriteString("   points to: each step is a `## N. question` heading.\n")
	b.WriteString("2. Follow **Do this**. Read **When this is not for you** before applying it and say so when the advice does\n")
	b.WriteString("   not fit the project.\n")
	b.WriteString("3. When writing files, start from **Put it all together** at the end of the guide.\n")
	b.WriteString("4. To review a service, use [checklist.md](checklist.md). Report each item as pass, fail or not applicable,\n")
	b.WriteString("   with its ID (for example `DB-03`) and the step that explains it.\n")
	b.WriteString("5. Name the guide and step behind each change, so a reviewer can read the reason.\n\n")
	b.WriteString("Keep the versions the guides use. When a guide marks a document as \"not in the baseline yet\" or a practice\n")
	b.WriteString("as an example, say so instead of presenting it as settled.\n\n")
	b.WriteString("## Index\n")
	for i, p := range parts {
		fmt.Fprintf(&b, "\n### Part %d: %s\n\n%s\n", i+1, p.Title, p.Lead)
		for _, id := range p.Guides {
			g := byID[id]
			fmt.Fprintf(&b, "\n**[%s](guides/%s.md)**: %s\n\n", g.Title, id, g.Description)
			for n, q := range g.Meta.Questions {
				fmt.Fprintf(&b, "%d. %s\n", n+1, q.Text)
			}
		}
	}
	return []byte(b.String())
}

func stepIndex(g *guidedata.Guide) map[string]int {
	m := map[string]int{}
	for i, c := range g.Cards {
		m[c.ID] = i + 1
	}
	return m
}

func checklistFor(r *guidedata.Repo, id string) []guidedata.ChecklistItem {
	var items []guidedata.ChecklistItem
	for _, c := range r.Checklist {
		if c.Guide == id {
			items = append(items, c)
		}
	}
	sort.Slice(items, func(i, j int) bool { return items[i].ID < items[j].ID })
	return items
}

func checklistLine(c guidedata.ChecklistItem, step int, box bool) string {
	s := "- "
	if box {
		s += "[ ] "
	}
	s += fmt.Sprintf("**%s** %s (step %d", c.ID, c.Text, step)
	if tags := guidedata.FilterTags(c.AppliesWhen); len(tags) > 0 {
		s += "; when " + strings.Join(tags, "; ")
	}
	return s + ")\n"
}

func skillChecklist(r *guidedata.Repo, parts []Part, byID map[string]*guidedata.Guide) []byte {
	var b strings.Builder
	b.WriteString(Header + "\n\n# Production checklist\n\n")
	b.WriteString("Every item of every guide, grouped by part. Use it to review a service or to agree on a standard as a team:\n")
	b.WriteString("mark each item pass, fail or not applicable and write down the reason for every exception. An item's step\n")
	b.WriteString("explains why it matters. Items with a \"when\" apply only to services that use that option.\n")
	for i, p := range parts {
		fmt.Fprintf(&b, "\n## Part %d: %s\n", i+1, p.Title)
		for _, id := range p.Guides {
			g := byID[id]
			fmt.Fprintf(&b, "\n### [%s](guides/%s.md)\n\n", g.Title, id)
			steps := stepIndex(g)
			for _, c := range checklistFor(r, id) {
				b.WriteString(checklistLine(c, steps[c.Card], true))
			}
		}
	}
	return []byte(b.String())
}

// skillGuide turns a guide page into plain Markdown: components become text, diagrams their description,
// recipes their code.
func skillGuide(r *guidedata.Repo, g *guidedata.Guide) ([]byte, error) {
	src, err := os.ReadFile(g.Path)
	if err != nil {
		return nil, err
	}
	lines := strings.Split(string(src), "\n")
	start := 1
	for start < len(lines) && lines[start] != "---" {
		start++
	}
	var b strings.Builder
	b.WriteString(Header + "\n\n")
	fmt.Fprintf(&b, "# %s\n\n%s\n\nOn the web: %s/guides/%s/. Written for %s.\n", g.Title, g.Description, Site, g.Meta.ID, baselineLine(r))
	inFence, blank := false, false
	emit := func(s string) {
		if s == "" {
			if blank {
				return
			}
			blank = true
		} else {
			blank = false
		}
		b.WriteString(s + "\n")
	}
	emit("")
	for i := start + 1; i < len(lines); i++ {
		l := lines[i]
		if strings.HasPrefix(strings.TrimLeft(l, " "), "```") {
			inFence = !inFence
			emit(l)
			continue
		}
		if inFence {
			b.WriteString(l + "\n")
			blank = false
			continue
		}
		if strings.HasPrefix(l, "import ") || reAnchorLine.MatchString(l) {
			continue
		}
		m := reTagLine.FindStringSubmatch(l)
		if m == nil {
			emit(siteLinks(l))
			continue
		}
		tag := l
		for !strings.Contains(tag, "/>") && i+1 < len(lines) {
			i++
			tag += " " + strings.TrimSpace(lines[i])
		}
		attrs := map[string]string{}
		for _, a := range reTagAttr.FindAllStringSubmatch(tag, -1) {
			attrs[a[1]] = a[2]
		}
		switch name := m[1]; {
		case name == "GuideStatus":
			emit("## What you'll learn")
			emit("")
			for n, q := range g.Meta.Questions {
				emit(fmt.Sprintf("%d. %s", n+1, q.Text))
			}
		case name == "Recipe":
			code, err := recipeCode(r, attrs)
			if err != nil {
				return nil, fmt.Errorf("%s: %w", g.Path, err)
			}
			emit(fmt.Sprintf("From [`%s`](%s/blob/main/%s):", filepath.Base(attrs["path"]), guidedata.Repository, attrs["path"]))
			emit("")
			emit("```" + attrs["lang"])
			b.WriteString(code + "\n")
			emit("```")
		case name == "Checklist":
			steps := stepIndex(g)
			items := checklistFor(r, g.Meta.ID)
			for _, t := range ChecklistTypes {
				first := true
				for _, c := range items {
					if c.Type != t[0] {
						continue
					}
					if first {
						emit("### " + t[1])
						emit("")
						first = false
					}
					b.WriteString(checklistLine(c, steps[c.Card], false))
					blank = false
				}
				if !first {
					emit("")
				}
			}
		case name == "GuideReferences":
			for _, ref := range guideRefs(r, g) {
				note := ref.Version
				if ref.Later {
					note = "not in the baseline yet"
				} else if !ref.Pinned {
					note += ", not version-pinned"
				}
				line := fmt.Sprintf("- [%s](%s) (%s)", ref.Title, ref.URL, note)
				if p := strings.TrimSuffix(ref.Purpose, "."); p != "" && p != ref.Title {
					line += ": " + ref.Purpose
				}
				emit(line)
			}
		case name == "DiagramLegend":
		case strings.HasSuffix(name, "Diagram"):
			d, ok := r.Diagrams[name]
			if !ok {
				return nil, fmt.Errorf("%s: unknown diagram %s", g.Path, name)
			}
			emit(fmt.Sprintf("> **Diagram:** %s %s", d.Caption, d.Description))
		default:
			return nil, fmt.Errorf("%s:%d: no Markdown form for <%s>", g.Path, i+1, name)
		}
	}
	return []byte(strings.TrimRight(b.String(), "\n") + "\n"), nil
}

// siteLinks makes links to the site absolute; a link to another guide points to its file in the skill.
func siteLinks(l string) string {
	return reSiteLink.ReplaceAllStringFunc(l, func(s string) string {
		p := s[2 : len(s)-1]
		if strings.HasPrefix(p, "/guides/") && strings.Count(p, "/") == 3 && strings.HasSuffix(p, "/") {
			return "](" + strings.TrimSuffix(strings.TrimPrefix(p, "/guides/"), "/") + ".md)"
		}
		return "](" + Site + p + ")"
	})
}

// recipeCode cuts the excerpt the same way the site's Recipe component does.
func recipeCode(r *guidedata.Repo, a map[string]string) (string, error) {
	b, err := os.ReadFile(filepath.Join(r.Root, a["path"]))
	if err != nil {
		return "", err
	}
	lines := strings.Split(strings.TrimSuffix(string(b), "\n"), "\n")
	find := func(prefix string, from int) []int {
		var idx []int
		for i := from; i < len(lines); i++ {
			if strings.HasPrefix(strings.TrimSpace(lines[i]), prefix) {
				idx = append(idx, i)
			}
		}
		return idx
	}
	s := find(a["start"], 0)
	if len(s) != 1 {
		return "", fmt.Errorf("recipe %s: start matches %d lines", a["id"], len(s))
	}
	e := s[0]
	if a["end"] != "" {
		ends := find(a["end"], s[0]+1)
		if len(ends) == 0 {
			return "", fmt.Errorf("recipe %s: end matches no later line", a["id"])
		}
		e = ends[0]
	}
	return dedent(lines[s[0] : e+1]), nil
}

// dedent removes the indentation every non-blank line shares.
func dedent(lines []string) string {
	cut := -1
	for _, l := range lines {
		if strings.TrimSpace(l) == "" {
			continue
		}
		if n := len(l) - len(strings.TrimLeft(l, " \t")); cut < 0 || n < cut {
			cut = n
		}
	}
	out := make([]string, len(lines))
	for i, l := range lines {
		if len(l) >= cut && cut > 0 {
			l = l[cut:]
		}
		out[i] = l
	}
	return strings.Join(out, "\n")
}
