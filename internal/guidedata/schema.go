package guidedata

import (
	"bytes"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"github.com/santhosh-tekuri/jsonschema/v6"
)

const schemaBase = "https://initializer.pyahu.io/schemas/"

// Schemas validates documents against the JSON Schemas in data/schemas.
type Schemas struct {
	byName map[string]*jsonschema.Schema
}

// LoadSchemas compiles every *.schema.json file under dir, with format assertion on.
func LoadSchemas(dir string) (*Schemas, error) {
	files, err := filepath.Glob(filepath.Join(dir, "*.schema.json"))
	if err != nil {
		return nil, err
	}
	sort.Strings(files)
	c := jsonschema.NewCompiler()
	c.AssertFormat()
	names := []string{}
	for _, f := range files {
		b, err := os.ReadFile(f)
		if err != nil {
			return nil, err
		}
		doc, err := jsonschema.UnmarshalJSON(bytes.NewReader(b))
		if err != nil {
			return nil, fmt.Errorf("%s: %w", f, err)
		}
		name := filepath.Base(f)
		if err := c.AddResource(schemaBase+name, doc); err != nil {
			return nil, fmt.Errorf("%s: %w", f, err)
		}
		names = append(names, name)
	}
	s := &Schemas{byName: map[string]*jsonschema.Schema{}}
	for _, n := range names {
		sch, err := c.Compile(schemaBase + n)
		if err != nil {
			return nil, fmt.Errorf("compile %s: %w", n, err)
		}
		s.byName[strings.TrimSuffix(n, ".schema.json")] = sch
	}
	return s, nil
}

// Validate checks v against the named schema (file name without .schema.json).
func (s *Schemas) Validate(schema, source string, v any) error {
	sch, ok := s.byName[schema]
	if !ok {
		return fmt.Errorf("no schema %q", schema)
	}
	if err := sch.Validate(v); err != nil {
		return fmt.Errorf("%s does not match %s.schema.json:\n%v", source, schema, err)
	}
	return nil
}
