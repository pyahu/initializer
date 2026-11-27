package guidedata

import (
	"strings"
	"testing"
)

func TestCheckReleasesRejects(t *testing.T) {
	prev := func(id string) *string { return &id }
	base := func() *Repo {
		return &Repo{
			Root:     t.TempDir(),
			Versions: Versions{Active: "v0.1-b3"},
			Releases: Releases{Releases: []Release{
				{ID: "v0.1-b3", Status: "released", Date: "2026-11-27", Previous: prev("v0.1-b2"), Summary: "s"},
				{ID: "v0.1-b2", Status: "initial", Summary: "s"},
			}},
		}
	}
	if errs := base().checkReleases(nil); len(errs) != 0 {
		t.Fatalf("valid releases: %v", errs)
	}
	cases := map[string]func(r *Repo){
		"has no release notes":             func(r *Repo) { r.Versions.Active = "v0.1-b4" },
		"previous must be v0.1-b2":         func(r *Repo) { r.Releases.Releases[0].Previous = prev("v0.1-b1") },
		"needs its date":                   func(r *Repo) { r.Releases.Releases[0].Date = "" },
		"the oldest entry must be initial": func(r *Repo) { r.Releases.Releases[1].Status = "released" },
		"duplicate action pins": func(r *Repo) {
			a := UpgradeStep{ID: "pins", Kind: "required", AppliesWhen: Filter{}}
			r.Releases.Releases[0].Actions = []UpgradeStep{a, a}
		},
		"unknown option": func(r *Repo) {
			r.Releases.Releases[0].Actions = []UpgradeStep{{ID: "x", AppliesWhen: Filter{"colour": {"red"}}}}
		},
		"unknown guide nope": func(r *Repo) {
			r.Releases.Releases[0].Guides = []GuideChange{{Step: "nope#np-step", Text: "t"}}
		},
		"components need this release's and the previous snapshot": func(r *Repo) {
			r.Releases.Releases[0].Actions = []UpgradeStep{{ID: "x", AppliesWhen: Filter{}, Components: []string{"java"}}}
		},
		"only the newest entry may be unreleased": func(r *Repo) {
			r.Releases.Releases = append([]Release{{ID: "v0.1-b4", Status: "released", Date: "2026-12-01",
				Previous: prev("v0.1-b3"), Summary: "s"}}, r.Releases.Releases...)
			r.Releases.Releases[1].Status = "unreleased"
			r.Releases.Releases[1].Date = ""
		},
	}
	for want, mutate := range cases {
		r := base()
		mutate(r)
		errs := r.checkReleases(nil)
		found := false
		for _, e := range errs {
			found = found || strings.Contains(e.Error(), want)
		}
		if !found {
			t.Errorf("%q: not reported in %v", want, errs)
		}
	}
}

func TestReleaseAnchor(t *testing.T) {
	if got := ReleaseAnchor("v0.1-b3"); got != "v0-1-b3" {
		t.Errorf("ReleaseAnchor = %q", got)
	}
}

func TestSamePinsIgnoresVerifiedAt(t *testing.T) {
	a := map[string]Component{"x": {Version: "1", VerifiedAt: "then"}}
	if !samePins(a, map[string]Component{"x": {Version: "1", VerifiedAt: "now"}}) {
		t.Error("a new verifiedAt is not a new pin")
	}
	if samePins(a, map[string]Component{"x": {Version: "2", VerifiedAt: "then"}}) {
		t.Error("a new version is a new pin")
	}
	if samePins(a, map[string]Component{"x": {Version: "1"}, "y": {Version: "1"}}) {
		t.Error("an added component is a new pin")
	}
}
