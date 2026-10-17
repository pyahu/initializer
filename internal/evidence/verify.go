package evidence

import (
	"bufio"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strings"

	"github.com/pyahu/initializer/internal/guidedata"
)

// VerifyInput names the artifacts of one fixture run.
type VerifyInput struct {
	Fixture    string
	GoTestJSON string
	JUnitDir   string
	SBOM       string
	Image      string
	Module     string
	// GradleOutcome is the Gradle build step's outcome; anything but success fails every
	// gradle/* test, so a passing JUnit class never hides a failed compile or Spotless check.
	GradleOutcome string
}

// Verify builds the evidence report of a fixture run.
func Verify(r *guidedata.Repo, in VerifyInput) (*Report, []error) {
	var errs []error
	fx := r.Fixture(in.Fixture)
	if fx == nil {
		return nil, []error{fmt.Errorf("unknown fixture %s", in.Fixture)}
	}
	rev := r.Versions.Revision(fx.Baseline)
	rep := &Report{Schema: ReportSchema, Fixture: fx.Name, Baseline: fx.Baseline, Commit: os.Getenv("GITHUB_SHA"),
		RunID: envInt("GITHUB_RUN_ID"), RunAttempt: envInt("GITHUB_RUN_ATTEMPT"), Tests: []TestResult{}, Components: []ComponentResult{}}
	results := map[string]TestResult{}
	if in.GoTestJSON != "" {
		gr, err := ReadGoTest(in.GoTestJSON, in.Module)
		if err != nil {
			errs = append(errs, fmt.Errorf("go test json: %w", err))
		}
		for k, v := range gr {
			results[k] = v
		}
	}
	if in.JUnitDir != "" {
		jr, err := ReadJUnit(in.JUnitDir, fx.Name)
		if err != nil {
			errs = append(errs, fmt.Errorf("junit: %w", err))
		}
		for k, v := range jr {
			results[k] = v
		}
	}
	for _, t := range fx.RequiredTests {
		res, ok := results[t]
		if strings.HasPrefix(t, "gradle/") && in.GradleOutcome != "" && in.GradleOutcome != "success" {
			res = TestResult{Test: t, Status: "fail", Runs: res.Runs, Output: "gradle build step: " + in.GradleOutcome}
			ok = true
		}
		if !ok {
			res = TestResult{Test: t, Status: "missing"}
		}
		if res.Status == "" {
			res.Status = "fail"
		}
		rep.Tests = append(rep.Tests, res)
	}
	for _, e := range r.Evidence {
		if e.Exercised() && e.Fixture == fx.Name && !containsStr(fx.RequiredTests, e.Test) {
			errs = append(errs, fmt.Errorf("evidence %s names test %s, which is not in the inventory", e.ID, e.Test))
		}
	}

	sbom := map[string]string{}
	if in.SBOM != "" {
		var err error
		if sbom, err = readSBOM(in.SBOM); err != nil {
			errs = append(errs, fmt.Errorf("sbom: %w", err))
		}
	}
	catalog, err := readCatalog(filepath.Join(fx.Dir, "gradle", "libs.versions.toml"))
	if err != nil {
		errs = append(errs, err)
	}
	froms, err := dockerfileImages(filepath.Join(fx.Dir, "Dockerfile"))
	if err != nil {
		errs = append(errs, err)
	}
	wrapper, _ := os.ReadFile(filepath.Join(fx.Dir, "gradle", "wrapper", "gradle-wrapper.properties"))
	javaVersion := ""
	if in.Image != "" {
		out, err := exec.Command("docker", "run", "--rm", "--entrypoint", "java", in.Image, "-version").CombinedOutput()
		if err != nil {
			errs = append(errs, fmt.Errorf("java -version in %s: %v: %s", in.Image, err, out))
		}
		javaVersion = string(out)
	}
	for _, key := range fx.Exercises {
		c := rev.Components[key]
		res := ComponentResult{Key: key, Expected: c.Version, Status: "missing"}
		set := func(resolved string, ok bool) {
			res.Resolved = resolved
			if ok {
				res.Status = "match"
			} else if resolved != "" {
				res.Status = "mismatch"
			}
		}
		switch c.Kind {
		case "jdk":
			res.Expected = c.Build
			if m := regexp.MustCompile(`\(build ([^)]+)\)`).FindStringSubmatch(javaVersion); m != nil {
				set(m[1], m[1] == c.Build || strings.HasPrefix(m[1], c.Build+"-"))
			}
		case "image":
			ref := c.Image[:strings.LastIndex(c.Image, ":")] + "@" + c.Digest
			res.Expected = c.Image + "@" + c.Digest
			if key == "otel-lgtm-image" {
				// acceptance/observability starts the image from versions.yaml (ImageRef).
				set(ref+" (read from versions.yaml by acceptance/observability)", true)
				break
			}
			found := ""
			for _, f := range froms {
				if f == c.Image+"@"+c.Digest || f == ref {
					found = f
				}
			}
			if found == "" && len(froms) > 0 {
				set(strings.Join(froms, ", "), false)
			} else if found != "" {
				set(found, true)
			}
		case "tool":
			if key == "gradle" {
				w := string(wrapper)
				urlOK := strings.Contains(w, "gradle-"+c.Version+"-bin.zip")
				sumOK := strings.Contains(w, "distributionSha256Sum="+c.DistributionSha256Sum)
				set(fmt.Sprintf("wrapper %s, sha256 %v", c.Version, sumOK), urlOK && sumOK)
			}
		case "gradle-plugin":
			v := catalog["plugin:"+c.PluginID]
			set(v, v == c.Version)
		case "maven-bom":
			v := catalog["lib:"+c.Coordinates]
			if v == "" && c.ManagedBy != nil {
				v = sbom[c.Coordinates]
			}
			ok := v == c.Version
			if c.PluginID != "" {
				pv := catalog["plugin:"+c.PluginID]
				ok = ok && pv == c.Version
				v += ", plugin " + pv
			}
			set(v, ok)
		case "maven":
			if v, ok := sbom[c.Coordinates]; ok {
				set(v, v == c.Version)
			} else if v := catalog["version:"+key]; v != "" {
				// Tools Gradle resolves off the classpath (Spotless loads palantir-java-format itself).
				set(v+" (version catalog; not on a resolved classpath)", v == c.Version)
			}
		default:
			res.Resolved = "kind " + c.Kind + " cannot be resolved from a fixture run"
		}
		rep.Components = append(rep.Components, res)
	}
	rep.OK = len(errs) == 0
	for _, t := range rep.Tests {
		if t.Status != "pass" || t.Runs < 1 {
			rep.OK = false
		}
	}
	for _, c := range rep.Components {
		if c.Status != "match" {
			rep.OK = false
		}
	}
	return rep, errs
}

// readSBOM maps group:name to version over every component of a CycloneDX JSON BOM.
func readSBOM(path string) (map[string]string, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	type comp struct {
		Group      string `json:"group"`
		Name       string `json:"name"`
		Version    string `json:"version"`
		Components []comp `json:"components"`
	}
	var bom struct {
		Metadata struct {
			Component comp `json:"component"`
		} `json:"metadata"`
		Components []comp `json:"components"`
	}
	if err := json.Unmarshal(b, &bom); err != nil {
		return nil, err
	}
	out := map[string]string{}
	var walk func([]comp)
	walk = func(cs []comp) {
		for _, c := range cs {
			if c.Group != "" {
				out[c.Group+":"+c.Name] = c.Version
			}
			walk(c.Components)
		}
	}
	walk(bom.Components)
	return out, nil
}

// readCatalog reads the subset of a Gradle version catalog the fixtures use:
// [versions] key = "v"; [libraries] and [plugins] with module/id and version or version.ref.
func readCatalog(path string) (map[string]string, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, fmt.Errorf("version catalog: %w", err)
	}
	defer f.Close()
	versions := map[string]string{}
	out := map[string]string{}
	type entry struct{ section, coord, version, ref string }
	var entries []entry
	section := ""
	reKV := regexp.MustCompile(`^([A-Za-z0-9_.-]+)\s*=\s*(.+)$`)
	reField := regexp.MustCompile(`([a-z.]+)\s*=\s*"([^"]*)"`)
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		l := strings.TrimSpace(sc.Text())
		if l == "" || strings.HasPrefix(l, "#") {
			continue
		}
		if strings.HasPrefix(l, "[") {
			section = strings.Trim(l, "[]")
			continue
		}
		m := reKV.FindStringSubmatch(l)
		if m == nil {
			continue
		}
		if section == "versions" {
			versions[m[1]] = strings.Trim(m[2], `"`)
			out["version:"+m[1]] = versions[m[1]]
			continue
		}
		e := entry{section: section}
		for _, fm := range reField.FindAllStringSubmatch(m[2], -1) {
			switch fm[1] {
			case "module", "id":
				e.coord = fm[2]
			case "version":
				e.version = fm[2]
			case "version.ref":
				e.ref = fm[2]
			}
		}
		entries = append(entries, e)
	}
	for _, e := range entries {
		v := e.version
		if e.ref != "" {
			v = versions[e.ref]
		}
		prefix := "lib:"
		if e.section == "plugins" {
			prefix = "plugin:"
		}
		out[prefix+e.coord] = v
	}
	return out, sc.Err()
}

func containsStr(list []string, s string) bool {
	for _, x := range list {
		if x == s {
			return true
		}
	}
	return false
}

// Summary renders a report for logs.
func Summary(rep *Report) string {
	var b strings.Builder
	fmt.Fprintf(&b, "fixture %s, baseline %s, ok %v\n", rep.Fixture, rep.Baseline, rep.OK)
	for _, t := range rep.Tests {
		fmt.Fprintf(&b, "  %-7s %-55s runs=%d %s\n", t.Status, t.Test, t.Runs, t.Output)
	}
	cs := append([]ComponentResult{}, rep.Components...)
	sort.Slice(cs, func(i, j int) bool { return cs[i].Key < cs[j].Key })
	for _, c := range cs {
		fmt.Fprintf(&b, "  %-8s %-32s expected %s resolved %s\n", c.Status, c.Key, c.Expected, c.Resolved)
	}
	return b.String()
}
