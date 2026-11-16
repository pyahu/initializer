package evidence

import (
	"slices"
	"strings"
	"testing"

	"github.com/pyahu/initializer/internal/guidedata"
)

func qualificationCase() (*guidedata.Repo, *Report) {
	const baseline = "v0.1-b1"
	const test = "acceptance/jvm::TestStartup"
	r := &guidedata.Repo{
		Versions: guidedata.Versions{Revisions: []guidedata.Revision{{ID: baseline, Components: map[string]guidedata.Component{
			"java": {Kind: "jdk", Build: "25", Version: "25", Claims: []string{"runtime"}},
		}}}},
		Fixtures: []guidedata.Fixture{{Name: "example", Baseline: baseline, RequiredTests: []string{test}, Exercises: []string{"java"}}},
		Evidence: []guidedata.EvidenceRecord{{ID: "ev-startup-1", Badge: "reproduced", Baseline: baseline,
			Fixture: "example", Test: test, Capabilities: []string{"runtime"}, Components: []string{"java"}}},
	}
	rep := &Report{Schema: ReportSchema, Fixture: "example", Baseline: baseline, Commit: strings.Repeat("a", 40),
		RunID: 1, RunAttempt: 1, OK: true,
		Tests:      []TestResult{{Test: test, Status: "pass", Runs: 1}},
		Components: []ComponentResult{{Key: "java", Expected: "25", Resolved: "25", Status: "match"}},
	}
	return r, rep
}

func TestQualificationKeepsRevisionsAndTheirInventoriesSeparate(t *testing.T) {
	r, oldReport := qualificationCase()
	r.HistoricalFixtures = slices.Clone(r.Fixtures)
	r.HistoricalEvidence = slices.Clone(r.Evidence)
	newComponent := r.Versions.Revisions[0].Components["java"]
	newComponent.Version, newComponent.Build = "26", "26"
	r.Versions.Revisions = append(r.Versions.Revisions, guidedata.Revision{
		ID: "v0.1-b2", Components: map[string]guidedata.Component{"java": newComponent},
	})
	r.Fixtures[0].Baseline = "v0.1-b2"
	r.Fixtures[0].RequiredTests = append(slices.Clone(r.Fixtures[0].RequiredTests), "acceptance/jvm::TestNew")
	r.Evidence[0].Baseline = "v0.1-b2"
	// A guide may still display its old evidence while waiting to be re-verified.
	// That record must not duplicate the saved historical proof.
	r.Evidence = append(r.Evidence, r.HistoricalEvidence[0])
	newReport := *oldReport
	newReport.Baseline = "v0.1-b2"
	newReport.Components = []ComponentResult{{Key: "java", Expected: "26", Resolved: "26", Status: "match"}}
	newReport.Tests = append(slices.Clone(oldReport.Tests), TestResult{Test: "acceptance/jvm::TestNew", Status: "pass", Runs: 1})
	if err := oldReport.ValidateQualification(r); err != nil {
		t.Fatalf("historical report must use its saved inventory, not today's: %v", err)
	}
	if err := newReport.ValidateQualification(r); err != nil {
		t.Fatal(err)
	}
	q := Qualify(r, map[string]*Report{oldReport.Key(): oldReport, newReport.Key(): &newReport})
	for _, baseline := range []string{"v0.1-b1", "v0.1-b2"} {
		if got := q[baseline]["java"]; got.State != "qualified" || len(got.Capabilities["runtime"]) != 1 {
			t.Fatalf("%s lost or duplicated its qualification: %+v", baseline, got)
		}
	}
	oldReport.OK = false
	q = Qualify(r, map[string]*Report{oldReport.Key(): oldReport, newReport.Key(): &newReport})
	if q["v0.1-b1"]["java"].State != "provisional" || q["v0.1-b2"]["java"].State != "qualified" {
		t.Fatal("a successful new revision must not qualify a failed historical report")
	}
}

func TestQualificationRequiresACompletePassingReport(t *testing.T) {
	cases := map[string]func(*Report){
		"failed report":       func(r *Report) { r.OK = false },
		"component mismatch":  func(r *Report) { r.Components[0].Status = "mismatch" },
		"wrong expected pin":  func(r *Report) { r.Components[0].Expected = "26" },
		"unknown component":   func(r *Report) { r.Components[0].Key = "unknown" },
		"missing component":   func(r *Report) { r.Components = nil },
		"duplicate component": func(r *Report) { r.Components = append(r.Components, r.Components[0]) },
		"missing test":        func(r *Report) { r.Tests = nil },
		"failed test":         func(r *Report) { r.Tests[0].Status = "fail" },
		"skipped test":        func(r *Report) { r.Tests[0].Status = "skip" },
		"unexecuted test":     func(r *Report) { r.Tests[0].Runs = 0 },
		"duplicate test":      func(r *Report) { r.Tests = append(r.Tests, r.Tests[0]) },
		"wrong baseline":      func(r *Report) { r.Baseline = "v0.1-b2" },
		"wrong fixture":       func(r *Report) { r.Fixture = "other" },
		"missing run":         func(r *Report) { r.RunID = 0 },
		"missing attempt":     func(r *Report) { r.RunAttempt = 0 },
		"invalid commit":      func(r *Report) { r.Commit = strings.Repeat("0", 40) },
	}
	for name, mutate := range cases {
		t.Run(name, func(t *testing.T) {
			r, report := qualificationCase()
			mutate(report)
			if report.ValidateQualification(r) == nil {
				t.Fatal("invalid report accepted")
			}
			if got := Qualify(r, map[string]*Report{"example": report})["v0.1-b1"]["java"].State; got != "provisional" {
				t.Fatalf("invalid report qualified the component: %s", got)
			}
		})
	}
	t.Run("passing report", func(t *testing.T) {
		r, report := qualificationCase()
		if err := report.ValidateQualification(r); err != nil {
			t.Fatal(err)
		}
		if got := Qualify(r, map[string]*Report{"example": report})["v0.1-b1"]["java"].State; got != "qualified" {
			t.Fatalf("passing report did not qualify: %s", got)
		}
	})
}
