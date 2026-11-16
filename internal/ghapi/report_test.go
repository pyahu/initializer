package ghapi

import (
	"archive/zip"
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
	"time"
)

func reportRepository(t *testing.T) (root, original, documentation, changed string) {
	t.Helper()
	root = t.TempDir()
	run := func(args ...string) string {
		t.Helper()
		out, err := git(root, args...)
		if err != nil {
			t.Fatal(args, err)
		}
		return out
	}
	run("init", "-q")
	run("config", "user.email", "test@example.invalid")
	run("config", "user.name", "Test")
	if err := os.MkdirAll(filepath.Join(root, ".github", "workflows"), 0755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, ".github", "workflows", "fixture.yml"), []byte("on:\n  push:\n    paths: [source]\n"), 0644); err != nil {
		t.Fatal(err)
	}
	commit := func(file, content string) string {
		t.Helper()
		if err := os.WriteFile(filepath.Join(root, file), []byte(content), 0644); err != nil {
			t.Fatal(err)
		}
		run("add", ".")
		run("commit", "-qm", content)
		return run("rev-parse", "HEAD")
	}
	original = commit("source", "original")
	documentation = commit("README", "documentation")
	changed = commit("source", "changed")
	return
}

func TestSelectRunBindsToInputsAndNeverFallsBack(t *testing.T) {
	root, original, documentation, changed := reportRepository(t)
	paths := []string{"source"}
	green := workflowRun{ID: 1, HeadSHA: original, Status: "completed", Conclusion: "success", Attempt: 1}
	if _, err := selectRun(root, documentation, paths, []workflowRun{green}); err != nil {
		t.Fatal(err)
	}
	if _, err := selectRun(root, changed, paths, []workflowRun{green}); !errors.Is(err, errPending) {
		t.Fatalf("old inputs accepted: %v", err)
	}
	pending := workflowRun{ID: 2, HeadSHA: documentation, Status: "in_progress", Attempt: 1}
	if _, err := selectRun(root, documentation, paths, []workflowRun{pending, green}); !errors.Is(err, errPending) {
		t.Fatalf("pending run bypassed: %v", err)
	}
	pending.Status, pending.Conclusion = "completed", "failure"
	if _, err := selectRun(root, documentation, paths, []workflowRun{pending, green}); err == nil || errors.Is(err, errPending) {
		t.Fatalf("failed run bypassed: %v", err)
	}
	if sameInputs(root, changed, documentation, paths) {
		t.Fatal("future commit accepted")
	}
}

func TestFetchReportWaitsAndChecksArtifactIdentity(t *testing.T) {
	root, _, _, target := reportRepository(t)
	for _, validIdentity := range []bool{true, false} {
		t.Run(fmt.Sprint(validIdentity), func(t *testing.T) {
			requests := 0
			var server *httptest.Server
			server = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				switch r.URL.Path {
				case "/repos/owner/repo/actions/workflows/fixture.yml/runs":
					requests++
					status := "in_progress"
					if requests > 1 {
						status = "completed"
					}
					_ = json.NewEncoder(w).Encode(map[string]any{"workflow_runs": []workflowRun{{ID: 42, HeadSHA: target, Status: status, Conclusion: "success", Attempt: 1}}})
				case "/repos/owner/repo/actions/runs/42/artifacts":
					_, _ = fmt.Fprintf(w, `{"artifacts":[{"name":"evidence-report","archive_download_url":%q}]}`, server.URL+"/artifact")
				case "/artifact":
					id := 42
					if !validIdentity {
						id = 41
					}
					var b bytes.Buffer
					z := zip.NewWriter(&b)
					f, _ := z.Create("evidence-report.json")
					_, _ = fmt.Fprintf(f, `{"ok":true,"commit":%q,"runId":%d,"runAttempt":1}`, target, id)
					_ = z.Close()
					_, _ = w.Write(b.Bytes())
				default:
					t.Errorf("unexpected URL %s", r.URL)
					w.WriteHeader(404)
				}
			}))
			defer server.Close()
			_, id, err := FetchReport("owner/repo", "fixture.yml", "main", "test-token", FetchOptions{Root: root, Commit: target, Wait: time.Second, api: server.URL, poll: time.Millisecond})
			if validIdentity && (err != nil || id != 42) {
				t.Fatalf("valid artifact rejected: %d %v", id, err)
			}
			if !validIdentity && err == nil {
				t.Fatal("wrong artifact identity accepted")
			}
			if requests != 2 {
				t.Fatalf("did not wait for current run: %d requests", requests)
			}
		})
	}
}

func TestPullRequestRunNamesTheHeadWhileEvidenceNamesTheMerge(t *testing.T) {
	root, original, _, _ := reportRepository(t)
	run := func(args ...string) string {
		t.Helper()
		out, err := git(root, args...)
		if err != nil {
			t.Fatal(args, err)
		}
		return out
	}
	run("branch", "-M", "main")
	run("checkout", "-qb", "feature", original)
	if err := os.WriteFile(filepath.Join(root, "feature"), []byte("feature"), 0644); err != nil {
		t.Fatal(err)
	}
	run("add", ".")
	run("commit", "-qm", "feature")
	head := run("rev-parse", "HEAD")
	run("checkout", "-q", "main")
	run("merge", "--no-ff", "-m", "test merge", "feature")
	merge := run("rev-parse", "HEAD")
	pr := workflowRun{ID: 3, HeadSHA: head, Status: "completed", Conclusion: "success", Event: "pull_request"}
	if sameInputs(root, head, merge, []string{"source"}) {
		t.Fatal("test needs main changes absent from the PR head")
	}
	if _, err := selectRun(root, merge, []string{"source"}, []workflowRun{pr}); err != nil {
		t.Fatal(err)
	}
	pr.HeadSHA = original
	if _, err := selectRun(root, merge, []string{"source"}, []workflowRun{pr}); !errors.Is(err, errPending) {
		t.Fatal("different PR head accepted", err)
	}
}
