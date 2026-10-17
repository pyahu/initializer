// Package evidence verifies fixture runs and recomputes qualification.
package evidence

import (
	"bufio"
	"encoding/json"
	"encoding/xml"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"

	"github.com/pyahu/initializer/internal/guidedata"
)

// ReportSchema is the report's schema id.
const ReportSchema = "initializer.pyahu.io/evidence-report/v1"

// Report is out/evidence-report.json.
type Report struct {
	Schema     string            `json:"schema"`
	Fixture    string            `json:"fixture"`
	Baseline   string            `json:"baseline"`
	Commit     string            `json:"commit"`
	RunID      int64             `json:"runId"`
	RunAttempt int64             `json:"runAttempt"`
	Tests      []TestResult      `json:"tests"`
	Components []ComponentResult `json:"components"`
	OK         bool              `json:"ok"`
}

// TestResult is one required test's outcome.
type TestResult struct {
	Test   string `json:"test"`
	Status string `json:"status"`
	Runs   int    `json:"runs"`
	Output string `json:"output"`
}

// ComponentResult compares a pinned component with what the run resolved.
type ComponentResult struct {
	Key      string `json:"key"`
	Expected string `json:"expected"`
	Resolved string `json:"resolved"`
	Status   string `json:"status"`
}

// Passed reports whether the test passed and ran.
func (r *Report) Passed(test string) bool {
	for _, t := range r.Tests {
		if t.Test == test {
			return t.Status == "pass" && t.Runs >= 1
		}
	}
	return false
}

// ResultPrefix marks a test's decisive output line.
const ResultPrefix = "RESULT: "

type goEvent struct {
	Action  string
	Package string
	Test    string
	Output  string
}

// ReadGoTest parses `go test -json` output into results keyed by <pkg path>::<Test>,
// where pkg path is relative to the module (acceptance/jvm).
func ReadGoTest(path, module string) (map[string]TestResult, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	res := map[string]TestResult{}
	sc := bufio.NewScanner(f)
	sc.Buffer(make([]byte, 1<<20), 1<<24)
	for sc.Scan() {
		line := sc.Bytes()
		if len(line) == 0 || line[0] != '{' {
			continue
		}
		var ev goEvent
		if err := json.Unmarshal(line, &ev); err != nil {
			continue
		}
		if ev.Test == "" || strings.Contains(ev.Test, "/") {
			continue
		}
		key := strings.TrimPrefix(strings.TrimPrefix(ev.Package, module), "/") + "::" + ev.Test
		t := res[key]
		t.Test = key
		switch ev.Action {
		case "run":
			t.Runs++
		case "pass", "fail", "skip":
			t.Status = ev.Action
		case "output":
			if i := strings.Index(ev.Output, ResultPrefix); i >= 0 {
				t.Output = truncate(strings.TrimSpace(ev.Output[i+len(ResultPrefix):]), 2000)
			}
		}
		res[key] = t
	}
	return res, sc.Err()
}

type junitSuite struct {
	Name     string `xml:"name,attr"`
	Tests    int    `xml:"tests,attr"`
	Failures int    `xml:"failures,attr"`
	Errors   int    `xml:"errors,attr"`
	Skipped  int    `xml:"skipped,attr"`
}

// ReadJUnit parses Gradle's TEST-*.xml reports into results keyed by gradle/<fixture>::<Class>.
func ReadJUnit(dir, fixture string) (map[string]TestResult, error) {
	files, _ := filepath.Glob(filepath.Join(dir, "*", "TEST-*.xml"))
	more, _ := filepath.Glob(filepath.Join(dir, "TEST-*.xml"))
	files = append(files, more...)
	res := map[string]TestResult{}
	for _, f := range files {
		b, err := os.ReadFile(f)
		if err != nil {
			return nil, err
		}
		var s junitSuite
		if err := xml.Unmarshal(b, &s); err != nil {
			return nil, fmt.Errorf("%s: %w", f, err)
		}
		cls := s.Name[strings.LastIndex(s.Name, ".")+1:]
		key := "gradle/" + fixture + "::" + cls
		status := "pass"
		switch {
		case s.Failures+s.Errors > 0:
			status = "fail"
		case s.Skipped > 0 || s.Tests == 0:
			status = "skip"
		}
		res[key] = TestResult{Test: key, Status: status, Runs: s.Tests, Output: fmt.Sprintf("%s: %d tests, %d failures, %d errors, %d skipped", s.Name, s.Tests, s.Failures, s.Errors, s.Skipped)}
	}
	return res, nil
}

func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n]
}

// ReadReport loads and validates a report.
func ReadReport(r *guidedata.Repo, path string) (*Report, error) {
	v, err := readJSON(path)
	if err != nil {
		return nil, err
	}
	if err := r.Schemas.Validate("evidence-report", path, v); err != nil {
		return nil, err
	}
	var rep Report
	if err := guidedata.Decode(v, &rep); err != nil {
		return nil, err
	}
	return &rep, nil
}

func readJSON(path string) (any, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	dec := json.NewDecoder(f)
	dec.UseNumber()
	var v any
	return v, dec.Decode(&v)
}

// envInt reads an integer environment variable, 0 when unset.
func envInt(name string) int64 {
	n, _ := strconv.ParseInt(os.Getenv(name), 10, 64)
	return n
}

var reFrom = regexp.MustCompile(`(?m)^FROM\s+(\S+)`)

// dockerfileImages returns the image references of the FROM lines.
func dockerfileImages(path string) ([]string, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var out []string
	for _, m := range reFrom.FindAllStringSubmatch(string(b), -1) {
		out = append(out, m[1])
	}
	sort.Strings(out)
	return out, nil
}
