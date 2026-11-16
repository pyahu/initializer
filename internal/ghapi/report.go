// Package ghapi fetches the latest fixture evidence report from GitHub Actions.
package ghapi

import (
	"archive/zip"
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"go.yaml.in/yaml/v3"
	"io"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"
)

// API is the GitHub REST API base.
const API = "https://api.github.com"

// FetchOptions binds evidence to the workflow inputs of the checkout being qualified.
type FetchOptions struct {
	Root, Commit string
	Wait         time.Duration
	// api and poll are injectable for deterministic HTTP tests.
	api  string
	poll time.Duration
}

type workflowRun struct {
	ID         int64  `json:"id"`
	HeadSHA    string `json:"head_sha"`
	Status     string `json:"status"`
	Conclusion string `json:"conclusion"`
	Attempt    int64  `json:"run_attempt"`
	Event      string `json:"event"`
}

var errPending = errors.New("waiting for matching fixture evidence")

// FetchReport waits for the newest run whose inputs match the target checkout.
// A newer pending or failed run is never replaced with an older passing result.
func FetchReport(repo, workflow, branch, token string, options FetchOptions) ([]byte, int64, error) {
	if token == "" {
		return nil, 0, fmt.Errorf("GITHUB_TOKEN is not set")
	}
	c := &http.Client{Timeout: 60 * time.Second}
	get := func(url string, out any) error {
		req, _ := http.NewRequest(http.MethodGet, url, nil)
		req.Header.Set("Authorization", "Bearer "+token)
		req.Header.Set("Accept", "application/vnd.github+json")
		req.Header.Set("X-GitHub-Api-Version", "2022-11-28")
		resp, err := c.Do(req)
		if err != nil {
			return err
		}
		defer resp.Body.Close()
		if resp.StatusCode != 200 {
			b, _ := io.ReadAll(resp.Body)
			return fmt.Errorf("%s: status %d: %s", url, resp.StatusCode, b)
		}
		if raw, ok := out.(*[]byte); ok {
			*raw, err = io.ReadAll(resp.Body)
			return err
		}
		return json.NewDecoder(resp.Body).Decode(out)
	}
	paths, err := workflowPaths(options.Root, workflow)
	if err != nil {
		return nil, 0, err
	}
	target, err := git(options.Root, "rev-parse", options.Commit)
	if err != nil {
		return nil, 0, err
	}
	api := options.api
	if api == "" {
		api = API
	}
	poll := options.poll
	if poll == 0 {
		poll = 15 * time.Second
	}
	deadline := time.Now().Add(options.Wait)
	var selected *workflowRun
	for {
		var runs struct {
			WorkflowRuns []workflowRun `json:"workflow_runs"`
		}
		if err := get(fmt.Sprintf("%s/repos/%s/actions/workflows/%s/runs?branch=%s&per_page=100", api, repo, workflow, url.QueryEscape(branch)), &runs); err != nil {
			return nil, 0, err
		}
		selected, err = selectRun(options.Root, target, paths, runs.WorkflowRuns)
		if err == nil {
			break
		}
		if !errors.Is(err, errPending) || !time.Now().Before(deadline) {
			return nil, 0, err
		}
		fmt.Fprintln(os.Stderr, "report: waiting for a completed fixture run with matching inputs")
		time.Sleep(poll)
	}
	id := selected.ID
	var arts struct {
		Artifacts []struct {
			Name               string `json:"name"`
			ArchiveDownloadURL string `json:"archive_download_url"`
			Expired            bool   `json:"expired"`
		} `json:"artifacts"`
	}
	if err := get(fmt.Sprintf("%s/repos/%s/actions/runs/%d/artifacts?name=evidence-report", api, repo, id), &arts); err != nil {
		return nil, id, err
	}
	for _, a := range arts.Artifacts {
		if a.Name != "evidence-report" || a.Expired {
			continue
		}
		var zipped []byte
		if err := get(a.ArchiveDownloadURL, &zipped); err != nil {
			return nil, id, err
		}
		zr, err := zip.NewReader(bytes.NewReader(zipped), int64(len(zipped)))
		if err != nil {
			return nil, id, err
		}
		for _, f := range zr.File {
			if f.Name == "evidence-report.json" {
				rc, err := f.Open()
				if err != nil {
					return nil, id, err
				}
				defer rc.Close()
				b, err := io.ReadAll(rc)
				if err != nil {
					return nil, id, err
				}
				var identity struct {
					Commit  string `json:"commit"`
					RunID   int64  `json:"runId"`
					Attempt int64  `json:"runAttempt"`
					OK      bool   `json:"ok"`
				}
				if err := json.Unmarshal(b, &identity); err != nil {
					return nil, id, err
				}
				if !identity.OK || identity.RunID != id || identity.Attempt != selected.Attempt || !sameInputs(options.Root, identity.Commit, target, paths) {
					return nil, id, fmt.Errorf("run %d: report identity or workflow inputs do not match the target checkout", id)
				}
				return b, id, nil
			}
		}
		return nil, id, fmt.Errorf("run %d: evidence-report artifact has no evidence-report.json", id)
	}
	return nil, id, fmt.Errorf("run %d of %s has no evidence-report artifact", id, workflow)
}

// Token returns GITHUB_TOKEN.
func Token() string { return os.Getenv("GITHUB_TOKEN") }

func workflowPaths(root, workflow string) ([]string, error) {
	b, err := os.ReadFile(filepath.Join(root, ".github", "workflows", workflow))
	if err != nil {
		return nil, err
	}
	var config struct {
		On struct {
			Push struct {
				Paths []string `yaml:"paths"`
			} `yaml:"push"`
		} `yaml:"on"`
	}
	if err := yaml.Unmarshal(b, &config); err != nil {
		return nil, err
	}
	if len(config.On.Push.Paths) == 0 {
		return []string{"."}, nil
	}
	for _, p := range config.On.Push.Paths {
		if strings.HasPrefix(p, "!") {
			return nil, fmt.Errorf("negated workflow paths are not supported")
		}
	}
	return config.On.Push.Paths, nil
}

func git(root string, args ...string) (string, error) {
	c := exec.Command("git", append([]string{"-C", root}, args...)...)
	b, err := c.Output()
	return strings.TrimSpace(string(b)), err
}

func sameInputs(root, source, target string, paths []string) bool {
	if source == "" {
		return false
	}
	if _, err := git(root, "merge-base", "--is-ancestor", source, target); err != nil {
		return false
	}
	_, err := git(root, append([]string{"diff", "--quiet", source, target, "--"}, paths...)...)
	return err == nil
}

func selectRun(root, target string, paths []string, runs []workflowRun) (*workflowRun, error) {
	for _, run := range runs {
		matches := sameInputs(root, run.HeadSHA, target, paths)
		if run.Event == "pull_request" {
			// checkout tests the synthetic merge commit; head_sha names its second parent.
			head, err := git(root, "rev-parse", target+"^2")
			matches = err == nil && head == run.HeadSHA
		}
		if !matches {
			continue
		}
		if run.Status != "completed" {
			return nil, errPending
		}
		if run.Conclusion != "success" {
			return nil, fmt.Errorf("matching fixture run %d concluded %s", run.ID, run.Conclusion)
		}
		return &run, nil
	}
	return nil, errPending
}
