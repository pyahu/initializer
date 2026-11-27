package guidedata

import "strings"

// Versions is data/versions.yaml.
type Versions struct {
	Schema    string     `json:"schema"`
	Active    string     `json:"active"`
	Revisions []Revision `json:"revisions"`
}

// Revision is one baseline revision.
type Revision struct {
	ID         string               `json:"id"`
	Release    string               `json:"release"`
	Created    string               `json:"created"`
	Components map[string]Component `json:"components"`
}

// Component is one pin of a revision.
type Component struct {
	Version               string  `json:"version"`
	Kind                  string  `json:"kind"`
	Coordinates           string  `json:"coordinates,omitempty"`
	PluginID              string  `json:"pluginId,omitempty"`
	Vendor                string  `json:"vendor,omitempty"`
	Feature               string  `json:"feature,omitempty"`
	Build                 string  `json:"build,omitempty"`
	Image                 string  `json:"image,omitempty"`
	Digest                string  `json:"digest,omitempty"`
	DistributionSha256Sum string  `json:"distributionSha256Sum,omitempty"`
	ManagedBy             *string `json:"managedBy"`
	VerifiedAt            string  `json:"verifiedAt"`
}

// Revision returns the revision with the id, or nil.
func (v *Versions) Revision(id string) *Revision {
	for i := range v.Revisions {
		if v.Revisions[i].ID == id {
			return &v.Revisions[i]
		}
	}
	return nil
}

// ActiveRevision returns the baseline the guides, the fixture and the generator use.
func (v *Versions) ActiveRevision() *Revision { return v.Revision(v.Active) }

// References is data/references.yaml.
type References struct {
	Schema  string           `json:"schema"`
	Entries []ReferenceEntry `json:"entries"`
}

// ReferenceEntry is one official document.
type ReferenceEntry struct {
	ID          string   `json:"id"`
	Title       string   `json:"title"`
	Component   *string  `json:"component"`
	DocVersion  string   `json:"docVersion"`
	Match       string   `json:"match"`
	Kind        string   `json:"kind"`
	Area        string   `json:"area"`
	URL         string   `json:"url"`
	Pinned      bool     `json:"pinned"`
	PinNote     string   `json:"pinNote,omitempty"`
	Expect      string   `json:"expect,omitempty"`
	Purpose     string   `json:"purpose"`
	Guides      []string `json:"guides"`
	AppliesWhen Filter   `json:"appliesWhen"`
	JEP         *JEP     `json:"jep"`
	LLMs        *LLMs    `json:"llms"`
	Baselines   []string `json:"baselines"`
}

// JEP describes a JDK Enhancement Proposal entry.
type JEP struct {
	Number  int    `json:"number"`
	Title   string `json:"title"`
	Status  string `json:"status"`
	Release string `json:"release"`
}

// LLMs points to an upstream llms.txt.
type LLMs struct {
	Txt  string `json:"txt"`
	Full string `json:"full,omitempty"`
}

// Filter is an appliesWhen map from option path to allowed values.
type Filter map[string][]any

// ChecklistItem is one record of data/checklist.yaml.
type ChecklistItem struct {
	ID          string `json:"id"`
	Guide       string `json:"guide"`
	Card        string `json:"card"`
	Type        string `json:"type"`
	Text        string `json:"text"`
	AppliesWhen Filter `json:"appliesWhen"`
}

// EvidenceRecord is one tested claim of data/evidence/<guide>.yaml. Its test is a required test of a
// fixture, so CI on main fails as soon as the claim stops holding. Only a measurement names its run,
// because its numbers belong to that run.
type EvidenceRecord struct {
	ID          string       `json:"id"`
	Card        string       `json:"card"`
	Claim       string       `json:"claim"`
	Badge       string       `json:"badge"`
	Test        string       `json:"test"`
	Result      string       `json:"result,omitempty"`
	Limitations string       `json:"limitations"`
	Run         string       `json:"run,omitempty"`
	Date        string       `json:"date,omitempty"`
	Measurement *Measurement `json:"measurement,omitempty"`
	Guide       string       `json:"guide"` // set by the loader
}

// Measurement describes a measured record.
type Measurement struct {
	Samples   int    `json:"samples"`
	Statistic string `json:"statistic"`
	Unit      string `json:"unit"`
	Protocol  string `json:"protocol"`
}

// Retired is one record of data/retired.yaml.
type Retired struct {
	ID         string `json:"id"`
	Kind       string `json:"kind"`
	Retired    string `json:"retired"`
	Reason     string `json:"reason"`
	ReplacedBy string `json:"replacedBy,omitempty"`
}

// LinkException is one record of data/link-exceptions.yaml.
type LinkException struct {
	URL     string `json:"url"`
	Reason  string `json:"reason"`
	Added   string `json:"added"`
	Expires string `json:"expires"`
}

// Fixture is fixtures/<name>/fixture.yaml.
type Fixture struct {
	Schema        string         `json:"schema"`
	Name          string         `json:"name"`
	Options       map[string]any `json:"options"`
	Exercises     []string       `json:"exercises"`
	RequiredTests []string       `json:"requiredTests"`
	Dir           string         `json:"-"`
}

// GuideMeta is the guide block of a guide page's frontmatter.
type GuideMeta struct {
	ID        string     `json:"id"`
	Prefix    string     `json:"prefix"`
	State     string     `json:"state"`
	Questions []Question `json:"questions"`
	Sources   []Source   `json:"sources,omitempty"`
}

// Question is a question the guide must answer.
type Question struct {
	ID         string `json:"id"`
	Text       string `json:"text"`
	AnsweredIn string `json:"answeredIn"`
}

// Source is a ported source document.
type Source struct {
	Title    string `json:"title"`
	Origin   string `json:"origin"`
	Revision string `json:"revision"`
	Date     string `json:"date"`
	Author   string `json:"author"`
	License  string `json:"license"`
}

// AuditException is one record of data/audit-exceptions.yaml.
type AuditException struct {
	Advisory string `json:"advisory"`
	Package  string `json:"package"`
	Reason   string `json:"reason"`
	Added    string `json:"added"`
	Expires  string `json:"expires"`
}

// Releases is data/releases.yaml.
type Releases struct {
	Schema   string    `json:"schema"`
	Releases []Release `json:"releases"`
}

// Release is the notes of one baseline revision. Initial marks the first one kept, which has a snapshot and no
// notes; Unreleased collects what start.pyahu.io already generates for the next revision.
type Release struct {
	ID       string        `json:"id"`
	Status   string        `json:"status"`
	Date     string        `json:"date,omitempty"`
	Previous *string       `json:"previous"`
	Summary  string        `json:"summary"`
	Actions  []UpgradeStep `json:"actions,omitempty"`
	Guides   []GuideChange `json:"guides,omitempty"`
	Changes  []string      `json:"changes,omitempty"`
	Baseline *Revision     `json:"-"` // from data/baselines/<id>.yaml; nil for an unreleased entry
}

// UpgradeStep is what a team changes in a project generated on the previous release.
type UpgradeStep struct {
	ID          string   `json:"id"`
	Kind        string   `json:"kind"`
	Title       string   `json:"title"`
	AppliesWhen Filter   `json:"appliesWhen"`
	When        string   `json:"when,omitempty"`
	Files       []string `json:"files"`
	Detect      string   `json:"detect"`
	Steps       string   `json:"steps"`
	Components  []string `json:"components,omitempty"`
	Verify      string   `json:"verify"`
	Guide       string   `json:"guide,omitempty"`
}

// GuideChange is one guide step whose advice changed in a release.
type GuideChange struct {
	Step string `json:"step"`
	Text string `json:"text"`
}

// Release returns the entry with the id, or nil.
func (r *Releases) Release(id string) *Release {
	for i := range r.Releases {
		if r.Releases[i].ID == id {
			return &r.Releases[i]
		}
	}
	return nil
}

// Anchor is the page anchor of a release id: v0.1-b3 becomes v0-1-b3.
func ReleaseAnchor(id string) string { return strings.ReplaceAll(id, ".", "-") }
