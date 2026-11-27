package references

import (
	"strings"
	"testing"

	"github.com/pyahu/initializer/internal/guidedata"
)

func TestReplacements(t *testing.T) {
	o := guidedata.Component{Version: "18.6", Image: "postgres:18.6", Digest: "sha256:aaa"}
	n := guidedata.Component{Version: "18.7", Image: "postgres:18.7", Digest: "sha256:bbb"}
	got := replacements(o, n)
	if len(got) != 2 || got[0] != [2]string{"postgres:18.6", "postgres:18.7"} || got[1] != [2]string{"sha256:aaa", "sha256:bbb"} {
		t.Errorf("image replacements = %v", got)
	}
	got = replacements(guidedata.Component{Version: "9.8.1", DistributionSha256Sum: "x"},
		guidedata.Component{Version: "9.9.2", DistributionSha256Sum: "y"})
	if len(got) != 2 || got[0] != [2]string{"9.8.1", "9.9.2"} || got[1] != [2]string{"x", "y"} {
		t.Errorf("tool replacements = %v", got)
	}
}

func TestPinTable(t *testing.T) {
	boot := "spring-boot"
	prev := "v0.1-b2"
	r := &guidedata.Repo{Releases: guidedata.Releases{Releases: []guidedata.Release{
		{ID: "v0.1-b3", Status: "released", Previous: &prev, Baseline: &guidedata.Revision{Components: map[string]guidedata.Component{
			"spring-boot":      {Version: "4.2.0"},
			"spring-framework": {Version: "7.1.0", ManagedBy: &boot},
			"syft":             {Version: "1.57.2"},
			"archunit":         {Version: "1.5.1"},
		}}},
		{ID: "v0.1-b2", Status: "initial", Baseline: &guidedata.Revision{Components: map[string]guidedata.Component{
			"spring-boot":      {Version: "4.1.2"},
			"spring-framework": {Version: "7.0.10", ManagedBy: &boot},
			"syft":             {Version: "1.54.1"},
			"archunit":         {Version: "1.5.1"},
		}}},
	}}}
	rel := r.Releases.Releases[0]
	edit := pinTable(r, rel, []string{"spring-boot", "spring-framework"})
	for _, want := range []string{"# spring-boot\n-4.1.2\n+4.2.0\n", "Managed by a BOM, nothing to edit: spring-framework 7.1.0."} {
		if !strings.Contains(edit, want) {
			t.Errorf("editable table lacks %q:\n%s", want, edit)
		}
	}
	if strings.Contains(edit, "syft") {
		t.Errorf("editable table lists a component the project does not carry:\n%s", edit)
	}
	full := pinTable(r, rel, nil)
	for _, want := range []string{"| syft | 1.54.1 | 1.57.2 |", "| spring-framework | 7.0.10, from spring-boot | 7.1.0, from spring-boot |", "1 other components are unchanged."} {
		if !strings.Contains(full, want) {
			t.Errorf("full table lacks %q:\n%s", want, full)
		}
	}
}

func TestDetectAuto(t *testing.T) {
	prev := "v0.1-b2"
	r := &guidedata.Repo{Releases: guidedata.Releases{Releases: []guidedata.Release{
		{ID: "v0.1-b3", Previous: &prev, Baseline: &guidedata.Revision{Components: map[string]guidedata.Component{
			"spring-boot": {Version: "4.2.0"}, "postgres-image": {Version: "18.7", Image: "postgres:18.7", Digest: "sha256:new"},
		}}},
		{ID: "v0.1-b2", Baseline: &guidedata.Revision{Components: map[string]guidedata.Component{
			"spring-boot": {Version: "4.1.2"}, "postgres-image": {Version: "18.6", Image: "postgres:18.6", Digest: "sha256:old"},
		}}},
	}}}
	a := guidedata.UpgradeStep{Detect: "auto", Components: []string{"spring-boot", "postgres-image"},
		Files: []string{"gradle/libs.versions.toml", "src/test/java/**/Postgres.java"}}
	got := detectCommand(r, r.Releases.Releases[0], a)
	want := `grep -rqsF -e '"4.1.2"' -e 'postgres:18.6' -e 'sha256:old' gradle/libs.versions.toml src/test/java`
	if got != want {
		t.Errorf("detectCommand =\n%s\nwant\n%s", got, want)
	}
}
