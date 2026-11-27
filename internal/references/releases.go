package references

import (
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"github.com/pyahu/initializer/internal/guidedata"
)

// ReleasesPagePath is the Release notes page on the site.
const ReleasesPagePath = "guides/src/content/docs/releases.md"

// releaseLinks decides how a rendered release links to the site: relative on the site itself, absolute in the
// Agent Skill and in GitHub release bodies.
type releaseLinks struct{ base string }

func (l releaseLinks) step(r *guidedata.Repo, ref string) string {
	guide, card, _ := strings.Cut(ref, "#")
	g := r.Guide(guide)
	label := guide
	if g != nil {
		label = g.Title
		for i, c := range g.Cards {
			if c.ID == card {
				label = fmt.Sprintf("%s, step %d", g.Title, i+1)
			}
		}
	}
	url := l.base + "/guides/" + guide + "/"
	if card != "" {
		url += "#" + card
	}
	return fmt.Sprintf("[%s](%s)", label, url)
}

// Releases renders the page, the skill's releases.md, every GitHub release body and the active baseline snapshot.
func Releases(r *guidedata.Repo) (Outputs, error) {
	out := Outputs{}
	page := "---\ntitle: Release notes\ndescription: What each baseline changed and how to bring a generated project up to it.\n" +
		"tableOfContents: { maxHeadingLevel: 3 }\n---\n\n" + Header + "\n\n" + releasesIntro +
		renderReleases(r, releaseLinks{base: ""}, r.Releases.Releases, "##")
	out[ReleasesPagePath] = []byte(page)
	skill := Header + "\n\n# Release notes\n\n" + releasesIntro + renderReleases(r, releaseLinks{base: Site}, r.Releases.Releases, "##")
	out[SkillDir+"/releases.md"] = []byte(skill)
	for i, rel := range r.Releases.Releases {
		if rel.Status != "released" {
			continue
		}
		body := Header + "\n\n" + renderReleases(r, releaseLinks{base: Site}, r.Releases.Releases[i:i+1], "##") +
			fmt.Sprintf("\nThe same notes, with every release, are at %s/releases/#%s.\n", Site, guidedata.ReleaseAnchor(rel.ID))
		out[guidedata.ReleaseNotesDir+"/"+rel.ID+".md"] = []byte(body)
	}
	snap, err := os.ReadFile(filepath.Join(r.Root, "data", "versions.yaml"))
	if err != nil {
		return nil, err
	}
	out["data/baselines/"+r.Versions.Active+".yaml"] = snap
	return out, nil
}

const releasesIntro = `A generated project says in its ` + "`README.md`" + ` and ` + "`AGENTS.md`" + ` which baseline it was generated on. To bring it
up to date, apply the upgrade steps of every release after that one, oldest first. Each step says how to tell
whether your project still needs it: run the **Needed when** command from the project root; exit status 0 means
the step applies. Steps that change the Gradle wrapper, ` + "`.github/`" + ` or ` + "`config/pmd/`" + ` touch files your ` + "`AGENTS.md`" + `
leaves to a person: put them in a pull request of their own. **Required** steps keep the project building and
scanning clean on the new versions, **recommended** ones follow changed advice and **optional** ones adopt what new
projects get.

`

func renderReleases(r *guidedata.Repo, l releaseLinks, rels []guidedata.Release, h string) string {
	var b strings.Builder
	for _, rel := range rels {
		anchor := guidedata.ReleaseAnchor(rel.ID)
		title := rel.ID
		switch rel.Status {
		case "unreleased":
			title = "Unreleased, on start.pyahu.io now"
		case "released":
			title = fmt.Sprintf("%s, %s", rel.ID, rel.Date)
		}
		fmt.Fprintf(&b, "<a id=\"%s\"></a>\n\n%s %s\n\n%s\n\n", anchor, h, title, strings.TrimSpace(rel.Summary))
		if rel.Status == "initial" {
			continue
		}
		prev := ""
		if rel.Previous != nil {
			prev = *rel.Previous
		}
		if len(rel.Actions) > 0 {
			fmt.Fprintf(&b, "%s# Upgrade a project generated on %s\n\n", h, prev)
			for n, a := range rel.Actions {
				fmt.Fprintf(&b, "<a id=\"%s-%s\"></a>\n\n**%d. %s** (%s)\n\n", anchor, a.ID, n+1, a.Title, a.Kind)
				if len(a.AppliesWhen) > 0 || a.When != "" {
					var conds []string
					keys := make([]string, 0, len(a.AppliesWhen))
					for k := range a.AppliesWhen {
						keys = append(keys, k)
					}
					sort.Strings(keys)
					for _, k := range keys {
						var vals []string
						for _, v := range a.AppliesWhen[k] {
							vals = append(vals, fmt.Sprint(v))
						}
						conds = append(conds, fmt.Sprintf("%s is %s", k, strings.Join(vals, " or ")))
					}
					if a.When != "" {
						conds = append(conds, a.When)
					}
					fmt.Fprintf(&b, "- Applies when: %s.\n", strings.Join(conds, "; "))
				}
				fmt.Fprintf(&b, "- Files: %s\n", codeList(a.Files))
				fmt.Fprintf(&b, "- Needed when: `%s`\n", detectCommand(r, rel, a))
				fmt.Fprintf(&b, "- Check: `%s`\n", a.Verify)
				if a.Guide != "" {
					fmt.Fprintf(&b, "- Why: %s\n", l.step(r, a.Guide))
				}
				fmt.Fprintf(&b, "\n%s\n\n", strings.TrimSpace(a.Steps))
				if len(a.Components) > 0 {
					b.WriteString(pinTable(r, rel, a.Components))
				}
			}
		}
		if rel.Baseline != nil && rel.Previous != nil {
			fmt.Fprintf(&b, "%s# Versions\n\n", h)
			b.WriteString(pinTable(r, rel, nil))
		}
		if len(rel.Guides) > 0 {
			fmt.Fprintf(&b, "%s# Guide steps that changed\n\n", h)
			for _, g := range rel.Guides {
				fmt.Fprintf(&b, "- %s: %s\n", l.step(r, g.Step), g.Text)
			}
			b.WriteString("\n")
		}
		if len(rel.Changes) > 0 {
			fmt.Fprintf(&b, "%s# Other changes\n\n", h)
			for _, c := range rel.Changes {
				fmt.Fprintf(&b, "- %s\n", c)
			}
			b.WriteString("\n")
		}
	}
	return b.String()
}

func codeList(xs []string) string {
	q := make([]string, len(xs))
	for i, x := range xs {
		q[i] = "`" + x + "`"
	}
	return strings.Join(q, ", ")
}

// pinTable compares a release's snapshot with its previous one. With only, it lists the strings a project replaces
// for those components; without, every change, BOM-managed versions included.
func pinTable(r *guidedata.Repo, rel guidedata.Release, only []string) string {
	editable := only != nil
	prevRel := r.Releases.Release(*rel.Previous)
	if prevRel == nil || prevRel.Baseline == nil || rel.Baseline == nil {
		return ""
	}
	before, after := prevRel.Baseline.Components, rel.Baseline.Components
	keys := map[string]bool{}
	for k := range before {
		keys[k] = true
	}
	for k := range after {
		keys[k] = true
	}
	sorted := make([]string, 0, len(keys))
	for k := range keys {
		if !editable || contains(only, k) {
			sorted = append(sorted, k)
		}
	}
	sort.Strings(sorted)
	var b strings.Builder
	var managed []string
	unchanged := 0
	if editable {
		b.WriteString("```diff\n")
	} else {
		fmt.Fprintf(&b, "| Component | %s | %s |\n| --- | --- | --- |\n", *rel.Previous, rel.ID)
	}
	for _, k := range sorted {
		o, had := before[k]
		n, has := after[k]
		switch {
		case !had:
			if !editable {
				fmt.Fprintf(&b, "| %s | (new) | %s |\n", k, pinText(n))
			}
		case !has:
			if !editable {
				fmt.Fprintf(&b, "| %s | %s | (removed) |\n", k, pinText(o))
			}
		case samePin(o, n):
			unchanged++
		case editable && n.ManagedBy != nil:
			managed = append(managed, fmt.Sprintf("%s %s", k, n.Version))
		case editable:
			fmt.Fprintf(&b, "# %s\n", k)
			for _, p := range replacements(o, n) {
				fmt.Fprintf(&b, "-%s\n+%s\n", p[0], p[1])
			}
		default:
			fmt.Fprintf(&b, "| %s | %s | %s |\n", k, pinText(o), pinText(n))
		}
	}
	if editable {
		b.WriteString("```\n")
	}
	b.WriteString("\n")
	if editable && len(managed) > 0 {
		fmt.Fprintf(&b, "Managed by a BOM, nothing to edit: %s.\n\n", strings.Join(managed, ", "))
	}
	if !editable && unchanged > 0 {
		fmt.Fprintf(&b, "%d other components are unchanged.\n\n", unchanged)
	}
	return b.String()
}

func samePin(a, b guidedata.Component) bool {
	return a.Version == b.Version && a.Image == b.Image && a.Digest == b.Digest &&
		a.DistributionSha256Sum == b.DistributionSha256Sum && a.Coordinates == b.Coordinates
}

func pinText(c guidedata.Component) string {
	s := c.Version
	if c.Digest != "" {
		s += " (" + shortDigest(c.Digest) + ")"
	}
	if c.ManagedBy != nil {
		s += ", from " + *c.ManagedBy
	}
	return s
}

func shortDigest(d string) string {
	if len(d) > 19 {
		return d[:19]
	}
	return d
}

// replacements are the exact strings a project replaces for one changed pin.
func replacements(o, n guidedata.Component) [][2]string {
	var ps [][2]string
	if o.Image != n.Image && o.Image != "" {
		ps = append(ps, [2]string{o.Image, n.Image})
	} else if o.Version != n.Version {
		ps = append(ps, [2]string{o.Version, n.Version})
	}
	if o.Digest != n.Digest && o.Digest != "" {
		ps = append(ps, [2]string{o.Digest, n.Digest})
	}
	if o.DistributionSha256Sum != n.DistributionSha256Sum && o.DistributionSha256Sum != "" {
		ps = append(ps, [2]string{o.DistributionSha256Sum, n.DistributionSha256Sum})
	}
	return ps
}

// detectCommand is the action's detect command; "auto" finds any old string of its components still in its files.
func detectCommand(r *guidedata.Repo, rel guidedata.Release, a guidedata.UpgradeStep) string {
	if a.Detect != "auto" {
		return a.Detect
	}
	prev := r.Releases.Release(*rel.Previous)
	var pats []string
	for _, k := range a.Components {
		o, n := prev.Baseline.Components[k], rel.Baseline.Components[k]
		if samePin(o, n) || n.ManagedBy != nil {
			continue
		}
		for _, p := range replacements(o, n) {
			old := p[0]
			if o.Image == "" || old == o.Version {
				old = "\"" + old + "\"" // a plain version, as the catalog quotes it
			}
			pats = append(pats, "-e '"+old+"'")
		}
	}
	seen := map[string]bool{}
	var paths []string
	for _, f := range a.Files {
		if i := strings.Index(f, "*"); i >= 0 {
			f = strings.TrimSuffix(f[:i], "/")
		}
		if !seen[f] {
			seen[f] = true
			paths = append(paths, f)
		}
	}
	return "grep -rqsF " + strings.Join(pats, " ") + " " + strings.Join(paths, " ")
}
