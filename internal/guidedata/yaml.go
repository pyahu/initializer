// Package guidedata loads and validates the repository's data files and guide sources.
package guidedata

import (
	"encoding/json"
	"fmt"
	"os"
	"strconv"

	"go.yaml.in/yaml/v3"
)

// LoadYAMLFile parses a YAML file into JSON-compatible values (map[string]any, []any,
// string, json.Number, bool, nil). It rejects:
// duplicate keys, non-string keys, anchors and aliases, unquoted dates, and floats.
func LoadYAMLFile(path string) (any, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	return ParseYAML(path, b)
}

// ParseYAML is LoadYAMLFile on bytes; name is used in error messages.
func ParseYAML(name string, b []byte) (any, error) {
	var doc yaml.Node
	if err := yaml.Unmarshal(b, &doc); err != nil {
		return nil, fmt.Errorf("%s: %w", name, err)
	}
	if doc.Kind == 0 {
		return nil, fmt.Errorf("%s: empty document", name)
	}
	return convert(name, &doc)
}

func convert(name string, n *yaml.Node) (any, error) {
	if n.Anchor != "" {
		return nil, fmt.Errorf("%s:%d: anchors are not allowed", name, n.Line)
	}
	switch n.Kind {
	case yaml.DocumentNode:
		if len(n.Content) != 1 {
			return nil, fmt.Errorf("%s: expected one document", name)
		}
		return convert(name, n.Content[0])
	case yaml.AliasNode:
		return nil, fmt.Errorf("%s:%d: aliases are not allowed", name, n.Line)
	case yaml.MappingNode:
		m := make(map[string]any, len(n.Content)/2)
		for i := 0; i < len(n.Content); i += 2 {
			k, v := n.Content[i], n.Content[i+1]
			if k.Kind != yaml.ScalarNode || k.Tag != "!!str" {
				return nil, fmt.Errorf("%s:%d: keys must be strings", name, k.Line)
			}
			if _, dup := m[k.Value]; dup {
				return nil, fmt.Errorf("%s:%d: duplicate key %q", name, k.Line, k.Value)
			}
			cv, err := convert(name, v)
			if err != nil {
				return nil, err
			}
			m[k.Value] = cv
		}
		return m, nil
	case yaml.SequenceNode:
		s := make([]any, 0, len(n.Content))
		for _, c := range n.Content {
			cv, err := convert(name, c)
			if err != nil {
				return nil, err
			}
			s = append(s, cv)
		}
		return s, nil
	case yaml.ScalarNode:
		switch n.Tag {
		case "!!str":
			return n.Value, nil
		case "!!null":
			return nil, nil
		case "!!bool":
			var b bool
			if err := n.Decode(&b); err != nil {
				return nil, fmt.Errorf("%s:%d: %w", name, n.Line, err)
			}
			return b, nil
		case "!!int":
			i, err := strconv.ParseInt(n.Value, 10, 64)
			if err != nil {
				return nil, fmt.Errorf("%s:%d: integers must be plain decimal: %q", name, n.Line, n.Value)
			}
			return json.Number(strconv.FormatInt(i, 10)), nil
		case "!!timestamp":
			return nil, fmt.Errorf("%s:%d: quote dates as strings: %q", name, n.Line, n.Value)
		case "!!float":
			return nil, fmt.Errorf("%s:%d: floats are not allowed, quote versions: %q", name, n.Line, n.Value)
		default:
			return nil, fmt.Errorf("%s:%d: unsupported tag %s", name, n.Line, n.Tag)
		}
	}
	return nil, fmt.Errorf("%s:%d: unsupported node", name, n.Line)
}

// Decode converts a validated JSON-compatible value into a typed struct.
func Decode(v any, out any) error {
	b, err := json.Marshal(v)
	if err != nil {
		return err
	}
	return json.Unmarshal(b, out)
}
