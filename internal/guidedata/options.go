package guidedata

import (
	"fmt"
	"sort"
)

// OptionPaths are the option paths a filter may name, with their value type.
var OptionPaths = map[string]string{
	"architecture": "string", "api": "list", "database": "string", "persistence": "string",
	"kafka.enabled": "bool", "kafka.outbox": "bool", "rabbitmq": "bool", "cache": "bool",
	"auth": "bool", "pyahuCli": "bool", "sample": "bool",
}

// OptionValues are the values the options contract allows per path (all releases), so
// a filter cannot name a value that can never exist.
var OptionValues = map[string][]any{
	"architecture": {"hexagonal", "ddd-layered", "layered"},
	"api":          {"rest", "grpc"},
	"database":     {"none", "postgres"},
	"persistence":  {"jdbc", "jpa", "jooq"},
}

// CheckFilter validates a filter's keys, value types and values.
func CheckFilter(f Filter) error {
	keys := make([]string, 0, len(f))
	for k := range f {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	for _, k := range keys {
		typ, ok := OptionPaths[k]
		if !ok {
			return fmt.Errorf("unknown option %q", k)
		}
		vals := f[k]
		if len(vals) == 0 {
			return fmt.Errorf("option %q: empty list", k)
		}
		for _, v := range vals {
			switch typ {
			case "bool":
				if _, ok := v.(bool); !ok {
					return fmt.Errorf("option %q: %v is not a boolean", k, v)
				}
			default:
				s, ok := v.(string)
				if !ok {
					return fmt.Errorf("option %q: %v is not a string", k, v)
				}
				if !contains(OptionValues[k], s) {
					return fmt.Errorf("option %q: unknown value %q", k, s)
				}
			}
		}
	}
	return nil
}

// Matches evaluates a filter against validated options: AND across keys, OR within a key,
// list options match when they contain a value, and an absent option matches nothing.
func Matches(f Filter, options map[string]any) bool {
	for k, allowed := range f {
		v, present := lookup(options, k)
		if !present {
			return false
		}
		ok := false
		if list, isList := v.([]any); isList {
			for _, item := range list {
				if contains(allowed, item) {
					ok = true
				}
			}
		} else {
			ok = contains(allowed, v)
		}
		if !ok {
			return false
		}
	}
	return true
}

func lookup(m map[string]any, path string) (any, bool) {
	switch path {
	case "kafka.enabled", "kafka.outbox":
		k, ok := m["kafka"].(map[string]any)
		if !ok {
			return nil, false
		}
		v, ok := k[path[len("kafka."):]]
		return v, ok
	}
	v, ok := m[path]
	return v, ok
}

func contains(list []any, v any) bool {
	for _, x := range list {
		if x == v {
			return true
		}
	}
	return false
}
