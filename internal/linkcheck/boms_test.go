package linkcheck

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

// Central has answered 404 for POMs that exist. The resolver must try the next host before it gives up.
func TestBOMResolverFallsBackWhenAHostAnswersNotFound(t *testing.T) {
	missing := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.NotFound(w, r)
	}))
	defer missing.Close()
	served := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/org/example/bom/1.0/bom-1.0.pom" {
			http.NotFound(w, r)
			return
		}
		_, _ = w.Write([]byte(`<project><dependencyManagement><dependencies><dependency>
<groupId>org.example</groupId><artifactId>lib</artifactId><version>2.3</version>
</dependency></dependencies></dependencyManagement></project>`))
	}))
	defer served.Close()

	res := &resolver{client: http.DefaultClient, cache: map[string]*pom{}, hosts: []string{missing.URL + "/", served.URL + "/"}}
	v, err := res.managed("org.example", "bom", "1.0", "org.example:lib", 0)
	if err != nil || v != "2.3" {
		t.Fatalf("managed = %q, %v; want 2.3", v, err)
	}

	res = &resolver{client: http.DefaultClient, cache: map[string]*pom{}, hosts: []string{missing.URL + "/", missing.URL + "/"}}
	if _, err := res.managed("org.example", "bom", "1.0", "org.example:lib", 0); err == nil {
		t.Fatal("a POM missing on every host must fail")
	}
}
