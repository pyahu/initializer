// Command guidekit checks and generates the Initializer's guides, data, and evidence.
// See CONTRIBUTING.md.
package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/pyahu/initializer/internal/evidence"
	"github.com/pyahu/initializer/internal/ghapi"
	"github.com/pyahu/initializer/internal/guidedata"
	"github.com/pyahu/initializer/internal/linkcheck"
	"github.com/pyahu/initializer/internal/references"
	"github.com/pyahu/initializer/internal/sitecheck"
)

const usage = `guidekit <command> [flags]

  check     [--base <sha>]           schemas, grammar, cross-refs, history
  generate  [--check]                reference page, guides.json, fixture files, fixture references
  qualify   [--check] --report <f>   recompute qualification from evidence (repeat --report per fixture)
  report    fetch --workflow <file> [--branch main] [--repo pyahu/initializer] --out <f>
  evidence  verify --fixture <name> [--go-test-json f] [--junit dir] [--sbom f] [--image ref] --out <f>
  site      [--dist guides/dist] [--site URL]   built site and llms exports
  links     [--dist guides/dist] [--site URL]   external links
  boms                               managedBy pins against Maven Central
  audit     [--dir guides]           npm audit, high and critical, with expiring exceptions
`

const defaultSite = "https://initializer.pyahu.io"

func main() {
	if len(os.Args) < 2 {
		fmt.Fprint(os.Stderr, usage)
		os.Exit(2)
	}
	root, err := guidedata.FindRoot(".")
	if err != nil {
		die(err)
	}
	cmd, args := os.Args[1], os.Args[2:]
	switch cmd {
	case "check":
		fs := flag.NewFlagSet(cmd, flag.ExitOnError)
		base := fs.String("base", "", "base commit for the history checks")
		_ = fs.Parse(args)
		r := mustLoad(root)
		errs := r.Check()
		if *base != "" {
			errs = append(errs, r.CheckHistory(*base)...)
		}
		finish("check", errs)
	case "generate":
		fs := flag.NewFlagSet(cmd, flag.ExitOnError)
		check := fs.Bool("check", false, "fail when outputs are stale instead of writing")
		_ = fs.Parse(args)
		r := mustLoad(root)
		out, err := references.Generate(r)
		if err != nil {
			die(err)
		}
		if *check {
			if stale := out.Check(root); len(stale) > 0 {
				for _, s := range stale {
					fmt.Fprintf(os.Stderr, "stale: %s\n", s)
				}
				die(fmt.Errorf("generated outputs are stale; run: go run ./cmd/guidekit generate"))
			}
			fmt.Println("generate: up to date")
			return
		}
		if err := out.Write(root); err != nil {
			die(err)
		}
		fmt.Printf("generate: wrote %d files\n", len(out))
	case "qualify":
		fs := flag.NewFlagSet(cmd, flag.ExitOnError)
		check := fs.Bool("check", false, "fail when versions.yaml disagrees instead of writing")
		var reports multi
		fs.Var(&reports, "report", "evidence report (repeatable)")
		_ = fs.Parse(args)
		r := mustLoad(root)
		reps := map[string]*evidence.Report{}
		for _, p := range reports {
			rep, err := evidence.ReadReport(r, p)
			if err != nil {
				die(err)
			}
			reps[rep.Fixture] = rep
		}
		for _, fx := range r.Fixtures {
			if reps[fx.Name] == nil {
				die(fmt.Errorf("no evidence report for fixture %s", fx.Name))
			}
		}
		q := evidence.Qualify(r, reps)
		if *check {
			if d := evidence.Differences(r, q); len(d) > 0 {
				for _, s := range d {
					fmt.Fprintln(os.Stderr, s)
				}
				die(fmt.Errorf("qualification is stale; run: go run ./cmd/guidekit qualify --report <report>"))
			}
			fmt.Println("qualify: versions.yaml agrees with the evidence")
			return
		}
		if err := evidence.WriteQualification(r, q); err != nil {
			die(err)
		}
		fmt.Println("qualify: wrote data/versions.yaml")
	case "report":
		if len(args) == 0 || args[0] != "fetch" {
			die(fmt.Errorf("usage: guidekit report fetch ..."))
		}
		fs := flag.NewFlagSet("report fetch", flag.ExitOnError)
		wf := fs.String("workflow", "", "workflow file name")
		branch := fs.String("branch", "main", "branch")
		repo := fs.String("repo", envOr("GITHUB_REPOSITORY", "pyahu/initializer"), "owner/name")
		out := fs.String("out", "out/evidence-report.json", "output file")
		_ = fs.Parse(args[1:])
		b, id, err := ghapi.FetchReport(*repo, *wf, *branch, ghapi.Token())
		if err != nil {
			die(err)
		}
		writeFile(*out, b)
		fmt.Printf("report: run %d -> %s\n", id, *out)
	case "evidence":
		if len(args) == 0 || args[0] != "verify" {
			die(fmt.Errorf("usage: guidekit evidence verify ..."))
		}
		fs := flag.NewFlagSet("evidence verify", flag.ExitOnError)
		in := evidence.VerifyInput{Module: "github.com/pyahu/initializer"}
		fs.StringVar(&in.Fixture, "fixture", "", "fixture name")
		fs.StringVar(&in.GoTestJSON, "go-test-json", "", "go test -json output")
		fs.StringVar(&in.JUnitDir, "junit", "", "Gradle test-results directory")
		fs.StringVar(&in.SBOM, "sbom", "", "CycloneDX JSON")
		fs.StringVar(&in.Image, "image", "", "built image")
		fs.StringVar(&in.GradleOutcome, "gradle-outcome", "", "outcome of the Gradle build step (success, failure, ...)")
		out := fs.String("out", "out/evidence-report.json", "report file")
		_ = fs.Parse(args[1:])
		r := mustLoad(root)
		rep, errs := evidence.Verify(r, in)
		if rep != nil {
			b, _ := json.MarshalIndent(rep, "", "  ")
			writeFile(*out, append(b, '\n'))
			fmt.Print(evidence.Summary(rep))
			if !rep.OK && len(errs) == 0 {
				errs = append(errs, fmt.Errorf("the run does not prove its required tests and components"))
			}
		}
		finish("evidence verify", errs)
	case "site":
		fs := flag.NewFlagSet(cmd, flag.ExitOnError)
		dist := fs.String("dist", "guides/dist", "built site")
		site := fs.String("site", defaultSite, "site URL")
		_ = fs.Parse(args)
		r := mustLoad(root)
		finish("site", sitecheck.Check(r, abs(root, *dist), *site))
	case "links":
		fs := flag.NewFlagSet(cmd, flag.ExitOnError)
		dist := fs.String("dist", "guides/dist", "built site")
		site := fs.String("site", defaultSite, "site URL")
		_ = fs.Parse(args)
		r := mustLoad(root)
		finish("links", linkcheck.Check(r, abs(root, *dist), *site, time.Now()))
	case "audit":
		fs := flag.NewFlagSet(cmd, flag.ExitOnError)
		dir := fs.String("dir", "guides", "npm package directory")
		_ = fs.Parse(args)
		r := mustLoad(root)
		finish("audit", linkcheck.Audit(r, abs(root, *dir), time.Now()))
	case "boms":
		r := mustLoad(root)
		finish("boms", linkcheck.CheckBOMs(r))
	default:
		fmt.Fprint(os.Stderr, usage)
		os.Exit(2)
	}
}

type multi []string

func (m *multi) String() string     { return fmt.Sprint(*m) }
func (m *multi) Set(s string) error { *m = append(*m, s); return nil }

func mustLoad(root string) *guidedata.Repo {
	r, errs, err := guidedata.Load(root)
	if err != nil {
		die(err)
	}
	if len(errs) > 0 {
		finish("load", errs)
	}
	return r
}

func finish(what string, errs []error) {
	if len(errs) == 0 {
		fmt.Printf("%s: ok\n", what)
		return
	}
	for _, e := range errs {
		fmt.Fprintln(os.Stderr, e)
	}
	fmt.Fprintf(os.Stderr, "%s: %d problem(s)\n", what, len(errs))
	os.Exit(1)
}

func die(err error) {
	fmt.Fprintln(os.Stderr, "guidekit:", err)
	os.Exit(1)
}

func abs(root, p string) string {
	if filepath.IsAbs(p) {
		return p
	}
	return filepath.Join(root, p)
}

func envOr(k, def string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return def
}

func writeFile(p string, b []byte) {
	if err := os.MkdirAll(filepath.Dir(p), 0o755); err != nil {
		die(err)
	}
	if err := os.WriteFile(p, b, 0o644); err != nil {
		die(err)
	}
}
