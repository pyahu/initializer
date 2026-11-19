package linkcheck

import (
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"sync/atomic"
	"testing"
	"time"

	"github.com/pyahu/initializer/internal/guidedata"
)

func TestHTTPRetryPolicyHonorsServerDelaysAndKeepsFailures(t *testing.T) {
	now := time.Date(2026, 11, 19, 12, 0, 0, 0, time.UTC)
	for _, tc := range []struct {
		name       string
		status     int
		header     string
		persistent bool
		attempts   int32
		delays     []time.Duration
		pass       bool
	}{
		{"429 seconds", 429, "2", false, 2, []time.Duration{2 * time.Second}, true},
		{"429 longer cooldown", 429, "90", false, 2, []time.Duration{90 * time.Second}, true},
		{"503 HTTP date", 503, now.Add(3 * time.Second).Format(http.TimeFormat), false, 2, []time.Duration{3 * time.Second}, true},
		{"404 never retried", 404, "", true, 1, nil, false},
		{"429 budget", 429, "", true, 4, []time.Duration{5 * time.Second, 15 * time.Second, 30 * time.Second}, false},
		{"excessive delay fails", 429, "3600", true, 1, nil, false},
		{"overflow delay fails", 429, "999999999999999999999999", true, 1, nil, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			var requests atomic.Int32
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				n := requests.Add(1)
				if n == 1 || tc.persistent {
					w.Header().Set("Retry-After", tc.header)
					w.WriteHeader(tc.status)
					return
				}
				_, _ = w.Write([]byte("available"))
			}))
			defer server.Close()
			var delays []time.Duration
			body, err := readURLWithRetry(context.Background(), server.Client(), server.URL, func() time.Time { return now }, func(_ context.Context, d time.Duration) error {
				delays = append(delays, d)
				return nil
			})
			if (err == nil) != tc.pass || (tc.pass && string(body) != "available") || requests.Load() != tc.attempts {
				t.Fatalf("body=%q, error=%v, requests=%d", body, err, requests.Load())
			}
			if len(delays) != len(tc.delays) {
				t.Fatalf("delays=%v, want %v", delays, tc.delays)
			}
			for i := range delays {
				if delays[i] != tc.delays[i] {
					t.Fatalf("delays=%v, want %v", delays, tc.delays)
				}
			}
		})
	}
}

func TestHTTPContentFailuresAreNotRetried(t *testing.T) {
	var requests atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests.Add(1)
		_, _ = w.Write([]byte(`<html><p id="present">existing page</p></html>`))
	}))
	defer server.Close()
	for _, target := range []target{{url: server.URL + "#missing"}, {url: server.URL, expect: "absent text"}} {
		if err := fetch(server.Client(), target); err == nil {
			t.Fatal("broken content accepted")
		}
	}
	if requests.Load() != 2 {
		t.Fatal("content failures retried", requests.Load())
	}
}

func TestLinkCheckerSerializesRequestsToOneOrigin(t *testing.T) {
	var active, maximum atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		n := active.Add(1)
		for old := maximum.Load(); n > old && !maximum.CompareAndSwap(old, n); old = maximum.Load() {
		}
		time.Sleep(10 * time.Millisecond)
		active.Add(-1)
		_, _ = w.Write([]byte("valid page"))
	}))
	defer server.Close()
	r := &guidedata.Repo{References: guidedata.References{Entries: []guidedata.ReferenceEntry{
		{URL: server.URL + "/a"}, {URL: server.URL + "/b"}, {URL: server.URL + "/c"},
	}}}
	dir := t.TempDir()
	if err := os.WriteFile(dir+"/index.html", []byte("<html></html>"), 0o644); err != nil {
		t.Fatal(err)
	}
	if errs := Check(r, dir, "https://example.invalid", time.Now()); len(errs) != 0 {
		t.Fatal(errs)
	}
	if maximum.Load() != 1 {
		t.Fatal("parallel requests to one origin", maximum.Load())
	}
}
