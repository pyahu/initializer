// Package sitecheck checks the built site and its llms exports (sections 4.7, 11.1, 11.4).
package sitecheck

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"

	"golang.org/x/net/html"

	"github.com/pyahu/initializer/internal/guidedata"
)

// PageSeparator is the llms export page separator configured in astro.config.mjs.
const PageSeparator = "\n\n<!-- page -->\n\n"

type page struct {
	path   string
	ids    map[string]int
	links  []string
	events []event
	text   string
	pres   []string // text of <pre class="fixture-file"> blocks
}

type event struct {
	kind  string // anchor, h2, h3
	value string
}

func parsePage(path string) (*page, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	doc, err := html.Parse(f)
	if err != nil {
		return nil, err
	}
	p := &page{path: path, ids: map[string]int{}}
	var text strings.Builder
	var walk func(n *html.Node, inMain bool)
	walk = func(n *html.Node, inMain bool) {
		if n.Type == html.ElementNode {
			attrs := map[string]string{}
			for _, a := range n.Attr {
				attrs[a.Key] = a.Val
			}
			if id, ok := attrs["id"]; ok {
				p.ids[id]++
			}
			for _, k := range []string{"href", "src"} {
				if v, ok := attrs[k]; ok {
					p.links = append(p.links, v)
				}
			}
			if n.Data == "main" {
				inMain = true
			}
			if inMain {
				switch {
				case n.Data == "a" && attrs["id"] != "" && attrs["href"] == "":
					p.events = append(p.events, event{"anchor", attrs["id"]})
				case n.Data == "h2" || n.Data == "h3":
					p.events = append(p.events, event{n.Data, strings.TrimSpace(textOf(n))})
				}
			}
			if n.Data == "pre" && strings.Contains(attrs["class"], "fixture-file") {
				p.pres = append(p.pres, textOf(n))
			}
			if n.Data == "script" || n.Data == "style" {
				return
			}
		}
		if n.Type == html.TextNode {
			text.WriteString(n.Data)
			text.WriteString(" ")
		}
		for c := n.FirstChild; c != nil; c = c.NextSibling {
			walk(c, inMain)
		}
	}
	walk(doc, false)
	p.text = normalize(text.String())
	return p, nil
}

func textOf(n *html.Node) string {
	var b strings.Builder
	var walk func(*html.Node)
	walk = func(n *html.Node) {
		if n.Type == html.TextNode {
			b.WriteString(n.Data)
		}
		for c := n.FirstChild; c != nil; c = c.NextSibling {
			walk(c)
		}
	}
	walk(n)
	return b.String()
}

var (
	reSpace  = regexp.MustCompile(`\s+`)
	reEscape = regexp.MustCompile("\\\\([!-/:-@\\[-`{-~])")
)

// normalize collapses whitespace and drops Markdown escapes and emphasis markers, so a
// rendered string and its Markdown export compare as text.
func normalize(s string) string {
	// Markdown typography turns straight quotes into curly ones in headings and prose.
	s = strings.NewReplacer("‘", "'", "’", "'", "“", "\"", "”", "\"").Replace(s)
	s = reEscape.ReplaceAllString(s, "$1")
	s = strings.NewReplacer("*", "", "`", "").Replace(s)
	return strings.TrimSpace(reSpace.ReplaceAllString(s, " "))
}

// Check runs every built-site check.
func Check(r *guidedata.Repo, dist, site string) []error {
	var errs []error
	add := func(format string, a ...any) { errs = append(errs, fmt.Errorf(format, a...)) }
	if fi, err := os.Stat(dist); err != nil || !fi.IsDir() {
		return []error{fmt.Errorf("%s: no built site", dist)}
	}
	pages := map[string]*page{}
	var htmlFiles []string
	_ = filepath.Walk(dist, func(p string, info os.FileInfo, err error) error {
		if err != nil || info.IsDir() {
			return nil
		}
		if strings.HasSuffix(p, ".html") {
			htmlFiles = append(htmlFiles, p)
		}
		return nil
	})
	if len(htmlFiles) == 0 {
		return append(errs, fmt.Errorf("%s: no HTML pages", dist))
	}
	sort.Strings(htmlFiles)
	for _, f := range htmlFiles {
		pg, err := parsePage(f)
		if err != nil {
			add("%s: %v", rel(dist, f), err)
			continue
		}
		pages[f] = pg
		for id, n := range pg.ids {
			if n > 1 {
				add("%s: duplicate id %q (%d times)", rel(dist, f), id, n)
			}
		}
	}
	siteURL, _ := url.Parse(site)
	for f, pg := range pages {
		for _, l := range pg.links {
			if l == "" || strings.HasPrefix(l, "mailto:") || strings.HasPrefix(l, "data:") || strings.HasPrefix(l, "javascript:") {
				continue
			}
			u, err := url.Parse(l)
			if err != nil {
				add("%s: bad link %q", rel(dist, f), l)
				continue
			}
			if u.Scheme != "" || u.Host != "" {
				if u.Host != siteURL.Host {
					continue // external, checked by guidekit links
				}
			} else if u.Path != "" && !strings.HasPrefix(u.Path, "/") {
				add("%s: relative link %q; use a root-relative link", rel(dist, f), l)
				continue
			}
			target := f
			if u.Path != "" {
				target = resolve(dist, u.Path)
				if target == "" {
					add("%s: link %q has no target", rel(dist, f), l)
					continue
				}
			}
			if u.Fragment != "" {
				tp := pages[target]
				if tp == nil {
					add("%s: link %q has a fragment on a non-HTML target", rel(dist, f), l)
				} else if tp.ids[u.Fragment] == 0 {
					add("%s: link %q: no id %q on %s", rel(dist, f), l, u.Fragment, rel(dist, target))
				}
			}
		}
	}
	errs = append(errs, checkGuides(r, dist, pages)...)
	errs = append(errs, checkFixturePages(r, dist, pages)...)
	errs = append(errs, checkExports(r, dist, site)...)
	return errs
}

func rel(dist, p string) string { r, _ := filepath.Rel(dist, p); return r }

func resolve(dist, p string) string {
	p, _ = url.PathUnescape(p)
	if strings.TrimSuffix(p, "/") == "/404" {
		p = "/404.html"
	}
	full := filepath.Join(dist, filepath.FromSlash(p))
	cands := []string{}
	if strings.HasSuffix(p, "/") {
		cands = append(cands, filepath.Join(full, "index.html"))
	} else {
		cands = append(cands, full, filepath.Join(full, "index.html"), full+".html")
	}
	for _, c := range cands {
		if fi, err := os.Stat(c); err == nil && !fi.IsDir() {
			return c
		}
	}
	return ""
}

func checkGuides(r *guidedata.Repo, dist string, pages map[string]*page) []error {
	var errs []error
	add := func(format string, a ...any) { errs = append(errs, fmt.Errorf(format, a...)) }
	for _, g := range r.Guides {
		f := filepath.Join(dist, "guides", g.Meta.ID, "index.html")
		pg := pages[f]
		if pg == nil {
			add("guide %s: built page %s missing", g.Meta.ID, rel(dist, f))
			continue
		}
		ev := pg.events
		for _, c := range g.Cards {
			i := -1
			for k, e := range ev {
				if e.kind == "anchor" && e.value == c.ID {
					i = k
				}
			}
			if i < 0 {
				add("guide %s: card anchor %s missing in HTML", g.Meta.ID, c.ID)
				continue
			}
			k := i + 1
			for k < len(ev) && ev[k].kind == "anchor" {
				k++
			}
			if k >= len(ev) || ev[k].kind != "h2" || normalize(ev[k].value) != normalize(c.Title) {
				add("guide %s: card %s: the ## heading %q does not follow its anchor in HTML", g.Meta.ID, c.ID, c.Title)
				continue
			}
			for n, part := range guidedata.CardParts {
				j := k + 1 + n
				if j >= len(ev) || ev[j].kind != "h3" || normalize(ev[j].value) != normalize(part) {
					add("guide %s: card %s: ### %s missing or out of order in HTML", g.Meta.ID, c.ID, part)
					break
				}
			}
		}
		for _, c := range r.Checklist {
			if c.Guide == g.Meta.ID && !strings.Contains(pg.text, normalize(c.ID+" "+c.Text)) {
				add("guide %s: checklist %s not on the page as \"ID text\"", g.Meta.ID, c.ID)
			}
		}
		for _, q := range g.Meta.Questions {
			if !strings.Contains(pg.text, normalize(q.Text)) {
				add("guide %s: question %s not on the page", g.Meta.ID, q.ID)
			}
		}
	}
	return errs
}

func checkFixturePages(r *guidedata.Repo, dist string, pages map[string]*page) []error {
	var errs []error
	b, err := os.ReadFile(filepath.Join(r.Root, "guides/src/generated/fixture-files.json"))
	if err != nil {
		return []error{err}
	}
	var ff struct {
		Files []struct {
			Path   string `json:"path"`
			Sha256 string `json:"sha256"`
		} `json:"files"`
	}
	if err := json.Unmarshal(b, &ff); err != nil {
		return []error{err}
	}
	for _, f := range ff.Files {
		p := filepath.Join(dist, filepath.FromSlash(f.Path), "index.html")
		pg := pages[p]
		if pg == nil {
			errs = append(errs, fmt.Errorf("fixture file page %s missing", rel(dist, p)))
			continue
		}
		if len(pg.pres) != 1 {
			errs = append(errs, fmt.Errorf("%s: expected one <pre class=\"fixture-file\">", rel(dist, p)))
			continue
		}
		sum := sha256.Sum256([]byte(pg.pres[0]))
		if hex.EncodeToString(sum[:]) != f.Sha256 {
			errs = append(errs, fmt.Errorf("%s: rendered content does not match %s (sha256)", rel(dist, p), f.Path))
		}
	}
	return errs
}

func checkExports(r *guidedata.Repo, dist, site string) []error {
	var errs []error
	add := func(format string, a ...any) { errs = append(errs, fmt.Errorf(format, a...)) }
	idx, err := os.ReadFile(filepath.Join(dist, "llms.txt"))
	if err != nil {
		return []error{fmt.Errorf("llms.txt: %v", err)}
	}
	base := strings.TrimSuffix(site, "/")
	want := []string{"/guides/", "/reference/docs/"}
	for _, g := range r.Guides {
		want = append(want, "/guides/"+g.Meta.ID+"/")
	}
	for _, p := range want {
		if !strings.Contains(string(idx), base+p) {
			add("llms.txt does not list %s%s", base, p)
		}
	}
	for _, name := range []string{"llms-full.txt", "llms-small.txt"} {
		b, err := os.ReadFile(filepath.Join(dist, name))
		if err != nil {
			add("%s: %v", name, err)
			continue
		}
		text := string(b)
		for _, bad := range []string{"Click to enlarge", "Press Esc"} {
			if strings.Contains(text, bad) {
				add("%s contains the UI string %q", name, bad)
			}
		}
		segs := map[string]string{}
		for _, s := range strings.Split(text, PageSeparator) {
			s = strings.TrimSpace(s)
			if strings.HasPrefix(s, "<SYSTEM>") {
				if i := strings.Index(s, "</SYSTEM>"); i >= 0 {
					s = strings.TrimSpace(s[i+len("</SYSTEM>"):])
				}
			}
			if !strings.HasPrefix(s, "# ") {
				continue
			}
			title := strings.TrimSpace(strings.SplitN(s, "\n", 2)[0][2:])
			if _, dup := segs[title]; dup {
				add("%s: two pages titled %q", name, title)
			}
			segs[title] = normalize(s)
		}
		need := func(seg, what, s string) {
			if !strings.Contains(seg, normalize(s)) {
				add("%s: %s missing: %q", name, what, s)
			}
		}
		for _, g := range r.Guides {
			seg, ok := segs[g.Title]
			if !ok {
				add("%s: no page segment for %q", name, g.Title)
				continue
			}
			for _, d := range g.Diagrams {
				dd := r.Diagrams[d]
				need(seg, "diagram caption "+dd.ID, dd.Caption)
				need(seg, "diagram description "+dd.ID, dd.Description)
			}
			for _, items := range g.Legends {
				for _, it := range items {
					need(seg, "legend "+it, guidedata.LegendText[it])
				}
			}
			for _, c := range r.Checklist {
				if c.Guide == g.Meta.ID {
					need(seg, "checklist "+c.ID, c.ID+" "+c.Text)
				}
			}
			for _, q := range g.Meta.Questions {
				need(seg, "question "+q.ID, q.Text)
			}
		}
		seg, ok := segs["Reference docs"]
		if !ok {
			add("%s: no Reference docs segment", name)
			continue
		}
		for _, e := range r.References.Entries {
			if containsStr(e.Baselines, r.Versions.Active) && !strings.Contains(seg, e.URL) {
				add("%s: reference %s URL missing", name, e.ID)
			}
		}
	}
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
