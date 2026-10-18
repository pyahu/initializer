package references

import "testing"

func TestSiteLinks(t *testing.T) {
	cases := map[string]string{
		"see the [database guide](/guides/database/).":      "see the [database guide](database.md).",
		"[step 2](/guides/database/#db-timeouts)":           "[step 2](https://initializer.pyahu.io/guides/database/#db-timeouts)",
		"[the fixture](/fixtures/single-hexagonal/x.java/)": "[the fixture](https://initializer.pyahu.io/fixtures/single-hexagonal/x.java/)",
		"[Spring](https://spring.io/)":                      "[Spring](https://spring.io/)",
	}
	for in, want := range cases {
		if got := siteLinks(in); got != want {
			t.Errorf("siteLinks(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestDedent(t *testing.T) {
	got := dedent([]string{"    a()", "", "        .b();"})
	if want := "a()\n\n    .b();"; got != want {
		t.Errorf("dedent = %q, want %q", got, want)
	}
}
