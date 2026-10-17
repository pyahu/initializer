// Package linkcheck checks external links and BOM-managed pins online.
package linkcheck

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"

	"golang.org/x/net/html"

	"github.com/pyahu/initializer/internal/guidedata"
)

// UserAgent identifies the checker to upstream sites.
// No URL in it: some hosts (oracle.com) answer 403 to a User-Agent that contains one.
const UserAgent = "pyahu-initializer-guidekit/1"

type target struct {
	url, expect, from string
}

// Check fetches every reference URL and every external link of the built site.
func Check(r *guidedata.Repo, dist, site string, now time.Time) []error {
	var errs []error
	exceptions := map[string]guidedata.LinkException{}
	for _, x := range r.Exceptions {
		exceptions[x.URL] = x
	}
	targets := map[string]target{}
	for _, e := range r.References.Entries {
		targets[e.URL] = target{url: e.URL, expect: e.Expect, from: "references " + e.ID}
	}
	siteURL, _ := url.Parse(site)
	_ = filepath.Walk(dist, func(p string, info os.FileInfo, err error) error {
		if err != nil || info.IsDir() || !strings.HasSuffix(p, ".html") {
			return nil
		}
		f, err := os.Open(p)
		if err != nil {
			return nil
		}
		defer f.Close()
		doc, err := html.Parse(f)
		if err != nil {
			return nil
		}
		var walk func(*html.Node)
		walk = func(n *html.Node) {
			if n.Type == html.ElementNode && n.Data == "a" {
				for _, a := range n.Attr {
					if a.Key == "href" && (strings.HasPrefix(a.Val, "http://") || strings.HasPrefix(a.Val, "https://")) {
						u, err := url.Parse(a.Val)
						if err == nil && u.Host != siteURL.Host {
							if _, ok := targets[a.Val]; !ok {
								rp, _ := filepath.Rel(dist, p)
								targets[a.Val] = target{url: a.Val, from: rp}
							}
						}
					}
				}
			}
			for c := n.FirstChild; c != nil; c = c.NextSibling {
				walk(c)
			}
		}
		walk(doc)
		return nil
	})
	if len(targets) == 0 {
		return []error{fmt.Errorf("no links to check; is %s built?", dist)}
	}
	list := make([]target, 0, len(targets))
	for _, t := range targets {
		list = append(list, t)
	}
	sort.Slice(list, func(i, j int) bool { return list[i].url < list[j].url })
	client := &http.Client{Timeout: 10 * time.Second}
	var mu sync.Mutex
	sem := make(chan struct{}, 8)
	var wg sync.WaitGroup
	for _, t := range list {
		if x, ok := exceptions[t.url]; ok {
			exp, _ := time.Parse("2006-01-02", x.Expires)
			if now.UTC().After(exp.Add(24 * time.Hour)) {
				mu.Lock()
				errs = append(errs, fmt.Errorf("%s: link exception expired on %s", t.url, x.Expires))
				mu.Unlock()
			}
			continue
		}
		wg.Add(1)
		sem <- struct{}{}
		go func(t target) {
			defer wg.Done()
			defer func() { <-sem }()
			if err := fetch(client, t); err != nil {
				mu.Lock()
				errs = append(errs, fmt.Errorf("%s (%s): %v", t.url, t.from, err))
				mu.Unlock()
			}
		}(t)
	}
	wg.Wait()
	sort.Slice(errs, func(i, j int) bool { return errs[i].Error() < errs[j].Error() })
	return errs
}

func fetch(client *http.Client, t target) error {
	backoff := []time.Duration{2 * time.Second, 8 * time.Second}
	var last error
	for attempt := 0; attempt <= len(backoff); attempt++ {
		if attempt > 0 {
			time.Sleep(backoff[attempt-1])
		}
		retry, err := once(client, t)
		if err == nil {
			return nil
		}
		last = err
		if !retry {
			return err
		}
	}
	return last
}

func once(client *http.Client, t target) (retry bool, err error) {
	req, _ := http.NewRequestWithContext(context.Background(), http.MethodGet, t.url, nil)
	req.Header.Set("User-Agent", UserAgent)
	req.Header.Set("Accept", "text/html,application/xhtml+xml,*/*")
	resp, err := client.Do(req)
	if err != nil {
		return true, err
	}
	defer resp.Body.Close()
	if resp.StatusCode == 429 || resp.StatusCode >= 500 {
		return true, fmt.Errorf("status %d", resp.StatusCode)
	}
	if resp.StatusCode != 200 {
		return false, fmt.Errorf("status %d", resp.StatusCode)
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, 32<<20))
	if err != nil {
		return true, err
	}
	if t.expect != "" && !strings.Contains(string(body), t.expect) {
		return false, fmt.Errorf("expected text %q not found", t.expect)
	}
	u, _ := url.Parse(t.url)
	if u.Fragment != "" {
		frag, _ := url.PathUnescape(u.Fragment)
		if !hasID(string(body), frag) {
			return false, fmt.Errorf("fragment %q not found as id or name", frag)
		}
	}
	return false, nil
}

func hasID(body, id string) bool {
	doc, err := html.Parse(strings.NewReader(body))
	if err != nil {
		return false
	}
	found := false
	var walk func(*html.Node)
	walk = func(n *html.Node) {
		if found {
			return
		}
		if n.Type == html.ElementNode {
			for _, a := range n.Attr {
				if (a.Key == "id" || a.Key == "name") && a.Val == id {
					found = true
				}
			}
		}
		for c := n.FirstChild; c != nil; c = c.NextSibling {
			walk(c)
		}
	}
	walk(doc)
	return found
}
