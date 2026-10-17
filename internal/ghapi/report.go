// Package ghapi fetches the latest fixture evidence report from GitHub Actions.
package ghapi

import (
	"archive/zip"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"time"
)

// API is the GitHub REST API base.
const API = "https://api.github.com"

// FetchReport downloads the evidence-report artifact of the newest completed run of a
// workflow on a branch, whatever its conclusion, and returns the JSON and the run id.
func FetchReport(repo, workflow, branch, token string) ([]byte, int64, error) {
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
	var runs struct {
		WorkflowRuns []struct {
			ID int64 `json:"id"`
		} `json:"workflow_runs"`
	}
	if err := get(fmt.Sprintf("%s/repos/%s/actions/workflows/%s/runs?branch=%s&status=completed&per_page=1", API, repo, workflow, branch), &runs); err != nil {
		return nil, 0, err
	}
	if len(runs.WorkflowRuns) == 0 {
		return nil, 0, fmt.Errorf("no completed run of %s on %s", workflow, branch)
	}
	id := runs.WorkflowRuns[0].ID
	var arts struct {
		Artifacts []struct {
			Name               string `json:"name"`
			ArchiveDownloadURL string `json:"archive_download_url"`
			Expired            bool   `json:"expired"`
		} `json:"artifacts"`
	}
	if err := get(fmt.Sprintf("%s/repos/%s/actions/runs/%d/artifacts?name=evidence-report", API, repo, id), &arts); err != nil {
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
				return b, id, err
			}
		}
		return nil, id, fmt.Errorf("run %d: evidence-report artifact has no evidence-report.json", id)
	}
	return nil, id, fmt.Errorf("run %d of %s has no evidence-report artifact", id, workflow)
}

// Token returns GITHUB_TOKEN.
func Token() string { return os.Getenv("GITHUB_TOKEN") }
