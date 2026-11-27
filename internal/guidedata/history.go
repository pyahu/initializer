package guidedata

import (
	"bytes"
	"fmt"
	"os/exec"
	"regexp"
	"sort"
	"strings"
)

// git runs git in the repository root.
func (r *Repo) git(args ...string) ([]byte, error) {
	cmd := exec.Command("git", args...)
	cmd.Dir = r.Root
	var stderr bytes.Buffer
	cmd.Stderr = &stderr
	out, err := cmd.Output()
	if err != nil {
		return nil, fmt.Errorf("git %s: %v: %s", strings.Join(args, " "), err, strings.TrimSpace(stderr.String()))
	}
	return out, nil
}

// showAt returns a file's content at a commit, or nil when it did not exist there.
func (r *Repo) showAt(base, path string) ([]byte, error) {
	if _, err := r.git("cat-file", "-e", base+":"+path); err != nil {
		return nil, nil
	}
	return r.git("show", base+":"+path)
}

// CheckHistory compares the working tree with base: guides, steps and checklist items that disappeared
// without a record in data/retired.yaml, and exception dates.
func (r *Repo) CheckHistory(base string) []error {
	var errs []error
	add := func(format string, a ...any) { errs = append(errs, fmt.Errorf(format, a...)) }
	if _, err := r.git("cat-file", "-e", base+"^{commit}"); err != nil {
		return []error{fmt.Errorf("base commit %s is not available (check out with fetch-depth: 0): %v", base, err)}
	}

	// Removed IDs.
	oldIDs, err := r.idsAt(base)
	if err != nil {
		add("%v", err)
	}
	live := r.liveIDs()
	retired := map[string]map[string]bool{}
	for _, x := range r.Retired {
		if retired[x.Kind] == nil {
			retired[x.Kind] = map[string]bool{}
		}
		retired[x.Kind][x.ID] = true
	}
	kinds := make([]string, 0, len(oldIDs))
	for k := range oldIDs {
		kinds = append(kinds, k)
	}
	sort.Strings(kinds)
	for _, kind := range kinds {
		ids := make([]string, 0, len(oldIDs[kind]))
		for id := range oldIDs[kind] {
			ids = append(ids, id)
		}
		sort.Strings(ids)
		for _, id := range ids {
			if !live[kind][id] && !retired[kind][id] {
				add("%s %s existed at base and is gone; add it to data/retired.yaml", kind, id)
			}
		}
	}

	// Link exceptions: added never changes; a new record carries the commit date.
	today := ""
	if out, err := r.git("log", "-1", "--format=%cd", "--date=format-local:%Y-%m-%d"); err == nil {
		today = strings.TrimSpace(string(out))
	}
	old := map[string]string{}
	if b, _ := r.showAt(base, "data/link-exceptions.yaml"); b != nil {
		if v, err := ParseYAML("base:data/link-exceptions.yaml", b); err == nil {
			var xs []LinkException
			_ = Decode(v, &xs)
			for _, x := range xs {
				old[x.URL+"\x00"+x.Added] = x.Expires
			}
		}
	}
	for _, x := range r.Exceptions {
		exp, existed := old[x.URL+"\x00"+x.Added]
		switch {
		case existed && exp != x.Expires:
			add("link exception %s: expires changed; a renewal is a new record", x.URL)
		case !existed && today != "" && x.Added != today:
			add("link exception %s: a new record must have added: %q (the commit date, UTC)", x.URL, today)
		}
	}
	oldAudit := map[string]string{}
	if b, _ := r.showAt(base, "data/audit-exceptions.yaml"); b != nil {
		if v, err := ParseYAML("base:data/audit-exceptions.yaml", b); err == nil {
			var xs []AuditException
			_ = Decode(v, &xs)
			for _, x := range xs {
				oldAudit[x.Advisory+"\x00"+x.Added] = x.Expires
			}
		}
	}
	for _, x := range r.Audit {
		exp, existed := oldAudit[x.Advisory+"\x00"+x.Added]
		switch {
		case existed && exp != x.Expires:
			add("audit exception %s: expires changed; a renewal is a new record", x.Advisory)
		case !existed && today != "" && x.Added != today:
			add("audit exception %s: a new record must have added: %q (the commit date, UTC)", x.Advisory, today)
		}
	}
	errs = append(errs, r.checkReleaseHistory(base)...)
	return errs
}

var (
	reIDLine = regexp.MustCompile(`(?m)^\s*- id: ([A-Za-z0-9-]+)\s*$`)
)

// idsAt lists the stable IDs present at a commit, by kind.
func (r *Repo) idsAt(base string) (map[string]map[string]bool, error) {
	m := map[string]map[string]bool{"guide": {}, "card": {}, "checklist": {}}
	list, err := r.git("ls-tree", "-r", "--name-only", base)
	if err != nil {
		return nil, err
	}
	for _, p := range strings.Split(strings.TrimSpace(string(list)), "\n") {
		switch {
		case strings.HasPrefix(p, "guides/src/content/docs/guides/") && strings.HasSuffix(p, ".mdx") && strings.Count(p, "/") == 5:
			b, _ := r.showAt(base, p)
			m["guide"][strings.TrimSuffix(p[strings.LastIndex(p, "/")+1:], ".mdx")] = true
			for _, l := range strings.Split(string(b), "\n") {
				if mm := reAnchor.FindStringSubmatch(l); mm != nil {
					m["card"][mm[1]] = true
				}
			}
		case p == "data/checklist.yaml":
			b, _ := r.showAt(base, p)
			for _, mm := range reIDLine.FindAllStringSubmatch(string(b), -1) {
				m["checklist"][mm[1]] = true
			}
		}
	}
	// Card anchors are kept as stubs when retired, so a retired card counts as live.
	return m, nil
}
