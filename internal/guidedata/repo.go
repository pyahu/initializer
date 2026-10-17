package guidedata

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
)

// Repo is everything guidekit reads, loaded and schema-validated.
type Repo struct {
	Root          string
	Schemas       *Schemas
	Versions      Versions
	References    References
	Checklist     []ChecklistItem
	Evidence      []EvidenceRecord
	Invalidations []Invalidation
	Retired       []Retired
	Exceptions    []LinkException
	Audit         []AuditException
	Fixtures      []Fixture
	Guides        []*Guide
	Diagrams      map[string]DiagramDef // by component name
}

// FindRoot walks up from dir to the directory holding go.mod and data/.
func FindRoot(dir string) (string, error) {
	d, err := filepath.Abs(dir)
	if err != nil {
		return "", err
	}
	for {
		if fileExists(filepath.Join(d, "go.mod")) && fileExists(filepath.Join(d, "data", "versions.yaml")) {
			return d, nil
		}
		parent := filepath.Dir(d)
		if parent == d {
			return "", errors.New("repository root not found (go.mod and data/versions.yaml)")
		}
		d = parent
	}
}

func fileExists(p string) bool { _, err := os.Stat(p); return err == nil }

// Load reads and validates every data file and guide. Rule violations in guides are
// returned in errs alongside a usable Repo; schema failures stop loading.
func Load(root string) (*Repo, []error, error) {
	r := &Repo{Root: root}
	s, err := LoadSchemas(filepath.Join(root, "data", "schemas"))
	if err != nil {
		return nil, nil, err
	}
	r.Schemas = s
	load := func(rel, schema string, out any) error {
		v, err := LoadYAMLFile(filepath.Join(root, rel))
		if err != nil {
			return err
		}
		if err := s.Validate(schema, rel, v); err != nil {
			return err
		}
		return Decode(v, out)
	}
	files := []struct {
		rel, schema string
		out         any
	}{
		{"data/versions.yaml", "versions", &r.Versions},
		{"data/references.yaml", "references", &r.References},
		{"data/checklist.yaml", "checklist", &r.Checklist},
		{"data/invalidations.yaml", "invalidations", &r.Invalidations},
		{"data/retired.yaml", "retired", &r.Retired},
		{"data/link-exceptions.yaml", "link-exceptions", &r.Exceptions},
		{"data/audit-exceptions.yaml", "audit-exceptions", &r.Audit},
	}
	for _, f := range files {
		if err := load(f.rel, f.schema, f.out); err != nil {
			return nil, nil, err
		}
	}
	evFiles, _ := filepath.Glob(filepath.Join(root, "data", "evidence", "*.yaml"))
	sort.Strings(evFiles)
	for _, f := range evFiles {
		var recs []EvidenceRecord
		rel, _ := filepath.Rel(root, f)
		if err := load(rel, "evidence", &recs); err != nil {
			return nil, nil, err
		}
		guide := strings.TrimSuffix(filepath.Base(f), ".yaml")
		for i := range recs {
			recs[i].Guide = guide
		}
		r.Evidence = append(r.Evidence, recs...)
	}
	fxFiles, _ := filepath.Glob(filepath.Join(root, "fixtures", "*", "fixture.yaml"))
	sort.Strings(fxFiles)
	for _, f := range fxFiles {
		var fx Fixture
		rel, _ := filepath.Rel(root, f)
		if err := load(rel, "fixture", &fx); err != nil {
			return nil, nil, err
		}
		fx.Dir = filepath.Dir(f)
		if fx.Name != filepath.Base(fx.Dir) {
			return nil, nil, fmt.Errorf("%s: name %q must equal its directory", rel, fx.Name)
		}
		r.Fixtures = append(r.Fixtures, fx)
	}
	r.Diagrams, err = LoadDiagrams(root)
	if err != nil {
		return nil, nil, err
	}
	retiredCards := map[string]string{}
	for _, x := range r.Retired {
		if x.Kind == "card" {
			retiredCards[x.ID] = x.ReplacedBy
		}
	}
	gfiles, err := GuideFiles(root)
	if err != nil {
		return nil, nil, err
	}
	var errs []error
	for _, f := range gfiles {
		g, gerrs := ParseGuide(f, s, retiredCards)
		errs = append(errs, gerrs...)
		if g != nil {
			r.Guides = append(r.Guides, g)
		}
	}
	return r, errs, nil
}

// Guide returns the guide with the id, or nil.
func (r *Repo) Guide(id string) *Guide {
	for _, g := range r.Guides {
		if g.Meta.ID == id {
			return g
		}
	}
	return nil
}

// Fixture returns the fixture with the name, or nil.
func (r *Repo) Fixture(name string) *Fixture {
	for i := range r.Fixtures {
		if r.Fixtures[i].Name == name {
			return &r.Fixtures[i]
		}
	}
	return nil
}

// Rel returns a path relative to the root, with forward slashes.
func (r *Repo) Rel(p string) string {
	rel, err := filepath.Rel(r.Root, p)
	if err != nil {
		return p
	}
	return filepath.ToSlash(rel)
}
