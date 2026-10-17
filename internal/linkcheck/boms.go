package linkcheck

import (
	"encoding/xml"
	"fmt"
	"io"
	"net/http"
	"regexp"
	"strings"
	"time"

	"github.com/pyahu/initializer/internal/guidedata"
)

// Central is the Maven Central base URL.
const Central = "https://repo1.maven.org/maven2/"

type pom struct {
	Parent struct {
		GroupID    string `xml:"groupId"`
		ArtifactID string `xml:"artifactId"`
		Version    string `xml:"version"`
	} `xml:"parent"`
	Properties struct {
		Any []struct {
			XMLName xml.Name
			Value   string `xml:",chardata"`
		} `xml:",any"`
	} `xml:"properties"`
	DependencyManagement struct {
		Dependencies []struct {
			GroupID    string `xml:"groupId"`
			ArtifactID string `xml:"artifactId"`
			Version    string `xml:"version"`
			Type       string `xml:"type"`
			Scope      string `xml:"scope"`
		} `xml:"dependencies>dependency"`
	} `xml:"dependencyManagement"`
}

type resolver struct {
	client *http.Client
	cache  map[string]*pom
}

func (r *resolver) get(g, a, v string) (*pom, error) {
	key := g + ":" + a + ":" + v
	if p, ok := r.cache[key]; ok {
		return p, nil
	}
	u := fmt.Sprintf("%s%s/%s/%s/%s-%s.pom", Central, strings.ReplaceAll(g, ".", "/"), a, v, a, v)
	var b []byte
	var last error
	// Same retry policy as the link check: Central rate-limits CI runners with 429.
	for attempt, wait := range []time.Duration{0, 2 * time.Second, 8 * time.Second} {
		if attempt > 0 {
			time.Sleep(wait)
		}
		req, _ := http.NewRequest(http.MethodGet, u, nil)
		req.Header.Set("User-Agent", UserAgent)
		resp, err := r.client.Do(req)
		if err != nil {
			last = err
			continue
		}
		body, _ := io.ReadAll(resp.Body)
		resp.Body.Close()
		if resp.StatusCode == 429 || resp.StatusCode >= 500 {
			last = fmt.Errorf("%s: status %d", u, resp.StatusCode)
			continue
		}
		if resp.StatusCode != 200 {
			return nil, fmt.Errorf("%s: status %d", u, resp.StatusCode)
		}
		b, last = body, nil
		break
	}
	if last != nil {
		return nil, last
	}
	var p pom
	if err := xml.Unmarshal(b, &p); err != nil {
		return nil, fmt.Errorf("%s: %w", u, err)
	}
	r.cache[key] = &p
	return &p, nil
}

var reProp = regexp.MustCompile(`\$\{([^}]+)\}`)

// managed resolves the version a BOM gives group:artifact, following parents for
// properties and import-scoped BOMs for absent artifacts.
func (r *resolver) managed(bomG, bomA, bomV, coord string, depth int) (string, error) {
	if depth > 6 {
		return "", fmt.Errorf("BOM imports nest too deep")
	}
	p, err := r.get(bomG, bomA, bomV)
	if err != nil {
		return "", err
	}
	props := map[string]string{"project.version": bomV}
	for cur, d := p, 0; cur != nil && d < 4; d++ {
		for _, pr := range cur.Properties.Any {
			if _, ok := props[pr.XMLName.Local]; !ok {
				props[pr.XMLName.Local] = strings.TrimSpace(pr.Value)
			}
		}
		if cur.Parent.ArtifactID == "" {
			break
		}
		cur, _ = r.get(cur.Parent.GroupID, cur.Parent.ArtifactID, cur.Parent.Version)
	}
	expand := func(s string) string {
		for i := 0; i < 5 && strings.Contains(s, "${"); i++ {
			s = reProp.ReplaceAllStringFunc(s, func(m string) string { return props[m[2:len(m)-1]] })
		}
		return s
	}
	for _, d := range p.DependencyManagement.Dependencies {
		if d.GroupID+":"+d.ArtifactID == coord && d.Scope != "import" {
			return expand(d.Version), nil
		}
	}
	for _, d := range p.DependencyManagement.Dependencies {
		if d.Scope == "import" {
			if d.GroupID+":"+d.ArtifactID == coord {
				return expand(d.Version), nil
			}
			v, err := r.managed(expand(d.GroupID), d.ArtifactID, expand(d.Version), coord, depth+1)
			if err != nil {
				return "", err
			}
			if v != "" {
				return v, nil
			}
		}
	}
	return "", nil
}

// CheckBOMs verifies every managedBy pin against its BOM on Maven Central.
func CheckBOMs(repo *guidedata.Repo) []error {
	res := &resolver{client: &http.Client{Timeout: 20 * time.Second}, cache: map[string]*pom{}}
	var errs []error
	for _, rev := range repo.Versions.Revisions {
		for key, c := range rev.Components {
			if c.ManagedBy == nil {
				continue
			}
			bom := rev.Components[*c.ManagedBy]
			ga := strings.SplitN(bom.Coordinates, ":", 2)
			v, err := res.managed(ga[0], ga[1], bom.Version, c.Coordinates, 0)
			switch {
			case err != nil:
				errs = append(errs, fmt.Errorf("%s %s: %v", rev.ID, key, err))
			case v == "":
				errs = append(errs, fmt.Errorf("%s %s: %s does not manage %s", rev.ID, key, bom.Coordinates, c.Coordinates))
			case v != c.Version:
				errs = append(errs, fmt.Errorf("%s %s: pinned %s, %s %s manages %s", rev.ID, key, c.Version, bom.Coordinates, bom.Version, v))
			}
		}
	}
	return errs
}
