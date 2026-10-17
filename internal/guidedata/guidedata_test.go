package guidedata

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestParseYAMLRejects(t *testing.T) {
	cases := map[string]string{
		"duplicate key":  "a: 1\na: 2\n",
		"unquoted date":  "d: 2026-10-17\n",
		"float version":  "v: 1.10\n",
		"anchor":         "a: &x 1\nb: *x\n",
		"non-string key": "1: a\n",
	}
	for name, src := range cases {
		if _, err := ParseYAML(name, []byte(src)); err == nil {
			t.Errorf("%s: accepted %q", name, src)
		}
	}
	v, err := ParseYAML("ok", []byte("d: \"2026-10-17\"\nn: 3\nb: true\n"))
	if err != nil {
		t.Fatal(err)
	}
	m := v.(map[string]any)
	if m["d"] != "2026-10-17" || m["b"] != true {
		t.Fatalf("got %v", m)
	}
}

func TestFilterSemantics(t *testing.T) {
	opts := map[string]any{"database": "none", "api": []any{"rest"}, "auth": true, "kafka": map[string]any{"enabled": false, "outbox": false}}
	cases := []struct {
		f    Filter
		want bool
	}{
		{Filter{}, true},
		{Filter{"database": {"postgres"}}, false},
		{Filter{"database": {"none", "postgres"}}, true},
		{Filter{"persistence": {"jdbc"}}, false}, // absent option matches nothing
		{Filter{"api": {"rest"}}, true},
		{Filter{"api": {"grpc"}}, false},
		{Filter{"auth": {true}, "database": {"none"}}, true},
		{Filter{"auth": {false}, "database": {"none"}}, false},
		{Filter{"kafka.enabled": {false}}, true},
	}
	for _, c := range cases {
		if got := Matches(c.f, opts); got != c.want {
			t.Errorf("Matches(%v) = %v, want %v", c.f, got, c.want)
		}
	}
	for _, bad := range []Filter{{"nope": {"x"}}, {"database": {}}, {"auth": {"true"}}, {"database": {"mysql"}}} {
		if err := CheckFilter(bad); err == nil {
			t.Errorf("CheckFilter(%v) accepted", bad)
		}
	}
}

func TestVersionMatches(t *testing.T) {
	cases := []struct {
		match, doc, pin string
		want            bool
	}{
		{"minor", "4.1", "4.1.1", true},
		{"minor", "4.1", "4.2.0", false},
		{"major", "25", "25.0.4.1+1", true},
		{"exact", "9.8.1", "9.8.1", true},
		{"exact", "9.8.0", "9.8.1", false},
		{"minor", "2026.0", "2026.0.1", true},
		{"none", "x", "y", true},
	}
	for _, c := range cases {
		if got := VersionMatches(c.match, c.doc, c.pin); got != c.want {
			t.Errorf("VersionMatches(%s, %s, %s) = %v", c.match, c.doc, c.pin, got)
		}
	}
}

const goodGuide = `---
title: T
description: D
tableOfContents: { maxHeadingLevel: 2 }
guide:
  id: g
  prefix: GG
  state: draft
  baseline: v0.1-b1
  components: [java]
  verification: null
  questions:
    - id: GG-Q1
      text: Q?
      answeredIn: gg-one
---
import GuideStatus from '../../../components/GuideStatus.astro';
import Evidence from '../../../components/Evidence.astro';
import Recipe from '../../../components/Recipe.astro';
import Checklist from '../../../components/Checklist.astro';
import GuideReferences from '../../../components/GuideReferences.astro';

<GuideStatus guide="g" />

## Decision summary

Text with ` + "`{braces}`" + ` in code.

<a id="gg-one"></a>

## One

### Problem

p

### Solution

s

### Use cases

u

### Trade-offs

t

### Evidence

<Evidence card="gg-one" />

## Recipes

### R

<Recipe id="r" path="fixtures/x/f" start="a" lang="text" />

**Expected:** e

**If it fails:**

- f

## Checklist

<Checklist guide="g" />

## References

<GuideReferences guide="g" />
`

func parse(t *testing.T, src string) (*Guide, []error) {
	t.Helper()
	root, err := FindRoot(".")
	if err != nil {
		t.Fatal(err)
	}
	s, err := LoadSchemas(filepath.Join(root, "data", "schemas"))
	if err != nil {
		t.Fatal(err)
	}
	p := filepath.Join(t.TempDir(), "g.mdx")
	if err := os.WriteFile(p, []byte(src), 0o644); err != nil {
		t.Fatal(err)
	}
	return ParseGuide(p, s, map[string]string{})
}

func TestParseGuideAcceptsTheFormat(t *testing.T) {
	g, errs := parse(t, goodGuide)
	if len(errs) > 0 {
		t.Fatalf("errors: %v", errs)
	}
	if len(g.Cards) != 1 || g.Cards[0].ID != "gg-one" || len(g.Recipes) != 1 {
		t.Fatalf("parsed %+v", g)
	}
}

func TestParseGuideRejects(t *testing.T) {
	cases := map[string][2]string{
		"missing part":        {"### Use cases\n\nu\n\n", ""},
		"parts out of order":  {"### Solution\n\ns\n\n### Use cases", "### Use cases\n\nu\n\n### Solution"},
		"brace in prose":      {"Text with `{braces}` in code.", "Text with {braces} outside."},
		"raw html":            {"Text with `{braces}` in code.", "Text <b>bold</b>."},
		"em dash":             {"Text with `{braces}` in code.", "Text — dash."},
		"mermaid":             {"Text with `{braces}` in code.", "```mermaid\ngraph TD\n```"},
		"starlight draft":     {"title: T\n", "title: T\ndraft: true\n"},
		"no expected":         {"**Expected:** e", "e"},
		"evidence extra text": {"<Evidence card=\"gg-one\" />\n", "<Evidence card=\"gg-one\" />\n\nMore text.\n"},
		"wrong prefix":        {"<a id=\"gg-one\"></a>", "<a id=\"xx-one\"></a>"},
	}
	for name, c := range cases {
		src := strings.Replace(goodGuide, c[0], c[1], 1)
		if src == goodGuide {
			t.Fatalf("%s: replacement did not apply", name)
		}
		if _, errs := parse(t, src); len(errs) == 0 {
			t.Errorf("%s: accepted", name)
		}
	}
}

func TestExcerpt(t *testing.T) {
	root := t.TempDir()
	if err := os.MkdirAll(filepath.Join(root, "fixtures", "x"), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "fixtures", "x", "f"), []byte("a\n  b {\n  }\n}\nc\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	r := &Repo{Root: root}
	got, err := r.Excerpt(Recipe{ID: "r", Path: "fixtures/x/f", Start: "b", End: "}"})
	if err != nil || got != "  b {\n  }" {
		t.Fatalf("got %q, %v", got, err)
	}
	if _, err := r.Excerpt(Recipe{ID: "r", Path: "fixtures/x/f", Start: "}"}); err == nil {
		t.Fatal("a start matching two lines was accepted")
	}
	if _, err := r.Excerpt(Recipe{ID: "r", Path: "fixtures/x/f", Start: "c", End: "z"}); err == nil {
		t.Fatal("an end with no later match was accepted")
	}
}
