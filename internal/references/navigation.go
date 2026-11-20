package references

import (
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"

	"github.com/pyahu/initializer/internal/guidedata"
)

// The guides index (guides.mdx) is the order of the guides. These pages repeat it by hand and must agree with it.
var navigationCopies = []struct {
	path string
	link *regexp.Regexp
}{
	{"guides/astro.config.mjs", regexp.MustCompile(`slug: "guides/([a-z0-9-]+)"`)},
	{"guides/src/pages/index.astro", regexp.MustCompile(`href: "/guides/([a-z0-9-]+)/"`)},
}

var reGuideInTemplate = regexp.MustCompile(`\(([A-Za-z.]+(?: [A-Za-z.]+)*) guide, steps? ([0-9]+)(?:,? (?:and )?([0-9]+))?(?:,? (?:and )?([0-9]+))?`)

// CheckNavigation reports a sidebar or landing page whose guides differ from the guides index, and a template
// comment that cites a step its guide does not have.
func CheckNavigation(r *guidedata.Repo) []error {
	var errs []error
	parts, err := Parts(r)
	if err != nil {
		return []error{err}
	}
	var want []string
	for _, p := range parts {
		want = append(want, p.Guides...)
	}
	for _, c := range navigationCopies {
		b, err := os.ReadFile(filepath.Join(r.Root, c.path))
		if err != nil {
			errs = append(errs, err)
			continue
		}
		var got []string
		for _, m := range c.link.FindAllStringSubmatch(string(b), -1) {
			got = append(got, m[1])
		}
		if strings.Join(got, ",") != strings.Join(want, ",") {
			errs = append(errs, fmt.Errorf("%s: its guides must be the guides index in order (guides.mdx):\n  have %v\n  want %v", c.path, got, want))
		}
	}
	errs = append(errs, checkTemplateSteps(r)...)
	return errs
}

// checkTemplateSteps finds "(database guide, step 4)" in the templates and checks that the guide has that step.
func checkTemplateSteps(r *guidedata.Repo) []error {
	byName := map[string]*guidedata.Guide{}
	for _, g := range r.Guides {
		byName[strings.ReplaceAll(g.Meta.ID, "-", " ")] = g
		byName[strings.ToLower(strings.Trim(strings.SplitN(g.Title, ":", 2)[0], `"`))] = g
	}
	// Names the templates use that are neither the id nor the title.
	aliases := map[string]string{
		"jvm in containers": "jvm-in-containers", "jvm": "jvm-in-containers", "image": "container-image",
		"kubernetes deployment": "kubernetes-deployment", "deployment": "kubernetes-deployment",
		"kubernetes manifests": "kubernetes-manifests", "manifests": "kubernetes-manifests", "next.js": "nextjs",
		"ci": "ci-pipelines", "local development": "local-development", "build quality": "build-quality",
		"rest": "rest-apis", "http clients": "http-clients",
	}
	for alias, id := range aliases {
		if g := r.Guide(id); g != nil {
			byName[alias] = g
		}
	}
	var errs []error
	_ = filepath.Walk(filepath.Join(r.Root, "templates"), func(p string, info os.FileInfo, err error) error {
		if err != nil || info.IsDir() || strings.HasSuffix(p, ".json") {
			return nil
		}
		b, err := os.ReadFile(p)
		if err != nil {
			return nil
		}
		for i, line := range strings.Split(string(b), "\n") {
			for _, m := range reGuideInTemplate.FindAllStringSubmatch(line, -1) {
				name := strings.ToLower(m[1])
				g := byName[name]
				if g == nil {
					for n, cand := range byName {
						if strings.HasSuffix(name, " "+n) || name == n {
							g = cand
						}
					}
				}
				if g == nil {
					continue
				}
				for _, s := range m[2:] {
					var n int
					if s == "" {
						continue
					}
					fmt.Sscan(s, &n)
					if n < 1 || n > len(g.Cards) {
						errs = append(errs, fmt.Errorf("%s:%d: cites %s guide step %d, which has %d steps", r.Rel(p), i+1, g.Meta.ID, n, len(g.Cards)))
					}
				}
			}
		}
		return nil
	})
	return errs
}
