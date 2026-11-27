package guidedata

import (
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
)

// ReleaseNotesDir holds the generated GitHub release bodies, one file per released entry.
const ReleaseNotesDir = "releases"

// checkReleases validates data/releases.yaml against the baseline, the guides and the files on disk.
func (r *Repo) checkReleases(cards map[string]string) []error {
	var errs []error
	add := func(format string, a ...any) { errs = append(errs, fmt.Errorf(format, a...)) }
	rels := r.Releases.Releases
	seen := map[string]bool{}
	for i, rel := range rels {
		if seen[rel.ID] {
			add("releases: duplicate %s", rel.ID)
		}
		seen[rel.ID] = true
		last := i == len(rels)-1
		switch rel.Status {
		case "initial":
			if !last {
				add("releases %s: only the oldest entry may be initial", rel.ID)
			}
			if rel.Previous != nil {
				add("releases %s: an initial entry has no previous", rel.ID)
			}
			if len(rel.Actions)+len(rel.Guides)+len(rel.Changes) > 0 {
				add("releases %s: an initial entry has no notes", rel.ID)
			}
		case "unreleased":
			if i != 0 {
				add("releases %s: only the newest entry may be unreleased", rel.ID)
			}
			if rel.Date != "" {
				add("releases %s: an unreleased entry has no date", rel.ID)
			}
			if rel.ID == r.Versions.Active {
				add("releases %s: the active baseline is released, not unreleased", rel.ID)
			}
		case "released":
			if rel.Date == "" {
				add("releases %s: a released entry needs its date", rel.ID)
			}
		}
		if rel.Status != "initial" {
			if last {
				add("releases %s: the oldest entry must be initial", rel.ID)
			} else if rel.Previous == nil || *rel.Previous != rels[i+1].ID {
				add("releases %s: previous must be %s, the next entry down", rel.ID, rels[i+1].ID)
			}
			if i+1 < len(rels) && rels[i+1].Date != "" && rel.Date != "" && rel.Date < rels[i+1].Date {
				add("releases %s: dated before %s", rel.ID, rels[i+1].ID)
			}
		}
		actionIDs := map[string]bool{}
		for _, a := range rel.Actions {
			if actionIDs[a.ID] {
				add("releases %s: duplicate action %s", rel.ID, a.ID)
			}
			actionIDs[a.ID] = true
			if err := CheckFilter(a.AppliesWhen); err != nil {
				add("releases %s action %s: %v", rel.ID, a.ID, err)
			}
			if a.Guide != "" {
				if err := r.checkStepRef(a.Guide, cards); err != nil {
					add("releases %s action %s: %v", rel.ID, a.ID, err)
				}
			}
			if a.Detect == "auto" && len(a.Components) == 0 {
				add("releases %s action %s: detect auto needs components", rel.ID, a.ID)
			}
			if len(a.Components) > 0 {
				prev := (*Release)(nil)
				if rel.Previous != nil {
					prev = r.Releases.Release(*rel.Previous)
				}
				if rel.Baseline == nil || prev == nil || prev.Baseline == nil {
					add("releases %s action %s: components need this release's and the previous snapshot", rel.ID, a.ID)
				} else {
					for _, c := range a.Components {
						_, before := prev.Baseline.Components[c]
						_, after := rel.Baseline.Components[c]
						if !before || !after {
							add("releases %s action %s: component %s is not in both snapshots", rel.ID, a.ID, c)
						}
					}
				}
			}
		}
		for _, gc := range rel.Guides {
			if err := r.checkStepRef(gc.Step, cards); err != nil {
				add("releases %s: %v", rel.ID, err)
			}
		}
	}
	active := r.Releases.Release(r.Versions.Active)
	switch {
	case active == nil:
		add("releases: the active baseline %s has no release notes (data/releases.yaml)", r.Versions.Active)
	case active.Status != "released" && active.Status != "initial":
		add("releases %s: the active baseline must be released", active.ID)
	}

	// Snapshots: one per released or initial entry, none left over.
	snaps, _ := filepath.Glob(filepath.Join(r.Root, "data", "baselines", "*.yaml"))
	for _, p := range snaps {
		id := strings.TrimSuffix(filepath.Base(p), ".yaml")
		if rel := r.Releases.Release(id); rel == nil || rel.Status == "unreleased" {
			add("data/baselines/%s.yaml: no released entry %s in data/releases.yaml", id, id)
		}
	}
	// GitHub release bodies: generated for released entries only, so a removed entry leaves no file behind.
	bodies, _ := filepath.Glob(filepath.Join(r.Root, ReleaseNotesDir, "*.md"))
	sort.Strings(bodies)
	for _, p := range bodies {
		id := strings.TrimSuffix(filepath.Base(p), ".md")
		if rel := r.Releases.Release(id); rel == nil || rel.Status != "released" {
			add("%s/%s.md: no released entry %s", ReleaseNotesDir, id, id)
		}
	}
	return errs
}

// checkStepRef checks a "<guide>" or "<guide>#<card>" reference.
func (r *Repo) checkStepRef(ref string, cards map[string]string) error {
	guide, card, _ := strings.Cut(ref, "#")
	g := r.Guide(guide)
	if g == nil {
		for _, x := range r.Retired {
			if x.Kind == "guide" && x.ID == guide {
				return nil // published notes keep naming a retired guide; its URL is kept too
			}
		}
		return fmt.Errorf("unknown guide %s", guide)
	}
	if card != "" && cards[card] != guide && !containsStr(g.Anchors, card) {
		return fmt.Errorf("card %s is not on guide %s", card, guide)
	}
	return nil
}

// generatorInputs are the paths whose change alters what start.pyahu.io generates.
var generatorInputs = []string{"templates/", "services/start/src/main/java/io/pyahu/start/generation/", "data/versions.yaml",
	"gradle/wrapper/", "gradlew"} // the wrapper files are copied into every project

// checkReleaseHistory keeps published notes stable and makes a change to what the generator produces say so.
func (r *Repo) checkReleaseHistory(base string) []error {
	var errs []error
	add := func(format string, a ...any) { errs = append(errs, fmt.Errorf(format, a...)) }

	// Published entries keep their anchors, their date and their snapshot.
	if b, _ := r.showAt(base, "data/releases.yaml"); b != nil {
		var old Releases
		if v, err := ParseYAML("base:data/releases.yaml", b); err == nil && Decode(v, &old) == nil {
			for _, o := range old.Releases {
				if o.Status == "unreleased" {
					continue
				}
				cur := r.Releases.Release(o.ID)
				if cur == nil {
					add("release %s existed at base and is gone; published notes stay", o.ID)
					continue
				}
				if cur.Status != o.Status || cur.Date != o.Date {
					add("release %s: status and date are fixed once published", o.ID)
				}
				for _, a := range o.Actions {
					found := false
					for _, c := range cur.Actions {
						found = found || c.ID == a.ID
					}
					if !found {
						add("release %s: action %s existed at base and is gone; its anchor is a public URL", o.ID, a.ID)
					}
				}
				p := "data/baselines/" + o.ID + ".yaml"
				if was, _ := r.showAt(base, p); was != nil && cur.Baseline != nil {
					var snap Versions
					if v, err := ParseYAML("base:"+p, was); err == nil && Decode(v, &snap) == nil {
						if old := snap.Revision(o.ID); old != nil && !samePins(old.Components, cur.Baseline.Components) {
							add("release %s is published: its pins never change; give the baseline a new id", o.ID)
						}
					}
				}
				if o.ID != r.Versions.Active {
					if was, _ := r.showAt(base, p); was != nil {
						if now, _ := os.ReadFile(filepath.Join(r.Root, p)); string(now) != string(was) {
							add("%s: the snapshot of a past release never changes", p)
						}
					}
				}
			}
		}
	}

	// A change to the generator's output needs a line in the release notes, except Renovate's own updates.
	authors, err := r.git("log", "--format=%ae", base+"..HEAD")
	if err != nil {
		return append(errs, err)
	}
	onlyBots := strings.TrimSpace(string(authors)) != ""
	for _, a := range strings.Fields(string(authors)) {
		onlyBots = onlyBots && strings.Contains(a, "renovate")
	}
	if onlyBots {
		return errs
	}
	out, err := r.git("diff", "--name-only", base)
	if err != nil {
		return append(errs, err)
	}
	if untracked, err := r.git("ls-files", "--others", "--exclude-standard"); err == nil {
		out = append(out, untracked...)
	}
	var touched []string
	notes := false
	for _, p := range strings.Fields(string(out)) {
		notes = notes || p == "data/releases.yaml"
		for _, in := range generatorInputs {
			if strings.HasPrefix(p, in) {
				touched = append(touched, p)
				break
			}
		}
	}
	if len(touched) > 0 && !notes {
		add("%s changes what start.pyahu.io generates: say what in data/releases.yaml (an action, or a line in changes of the unreleased entry)", touched[0])
	}
	return errs
}

// samePins compares two pin lists, ignoring when each pin was last verified.
func samePins(a, b map[string]Component) bool {
	if len(a) != len(b) {
		return false
	}
	for k, x := range a {
		y, ok := b[k]
		x.VerifiedAt, y.VerifiedAt = "", ""
		if !ok || x.Version != y.Version || x.Image != y.Image || x.Digest != y.Digest ||
			x.DistributionSha256Sum != y.DistributionSha256Sum || x.Coordinates != y.Coordinates ||
			x.PluginID != y.PluginID || x.Build != y.Build || !sameManager(x.ManagedBy, y.ManagedBy) {
			return false
		}
	}
	return true
}

func sameManager(a, b *string) bool {
	return (a == nil && b == nil) || (a != nil && b != nil && *a == *b)
}
