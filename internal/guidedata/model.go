package guidedata

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
	Version               string        `json:"version"`
	Kind                  string        `json:"kind"`
	Coordinates           string        `json:"coordinates,omitempty"`
	PluginID              string        `json:"pluginId,omitempty"`
	Vendor                string        `json:"vendor,omitempty"`
	Feature               string        `json:"feature,omitempty"`
	Build                 string        `json:"build,omitempty"`
	Image                 string        `json:"image,omitempty"`
	Digest                string        `json:"digest,omitempty"`
	DistributionSha256Sum string        `json:"distributionSha256Sum,omitempty"`
	ManagedBy             *string       `json:"managedBy"`
	VerifiedAt            string        `json:"verifiedAt"`
	Guides                []string      `json:"guides"`
	Claims                []string      `json:"claims"`
	Qualification         Qualification `json:"qualification"`
}

// Qualification is the mutable block written by guidekit qualify.
type Qualification struct {
	State        string              `json:"state"`
	Capabilities map[string][]string `json:"capabilities"`
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

// RevisionIndex returns the position of the revision, or -1.
func (v *Versions) RevisionIndex(id string) int {
	for i := range v.Revisions {
		if v.Revisions[i].ID == id {
			return i
		}
	}
	return -1
}

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
	ID          string   `json:"id"`
	Guide       string   `json:"guide"`
	Card        string   `json:"card"`
	Type        string   `json:"type"`
	Text        string   `json:"text"`
	Claim       string   `json:"claim"`
	Tests       []string `json:"tests"`
	AppliesWhen Filter   `json:"appliesWhen"`
}

// EvidenceRecord is one record of data/evidence/<guide>.yaml.
type EvidenceRecord struct {
	ID           string       `json:"id"`
	Card         string       `json:"card"`
	Claim        string       `json:"claim"`
	Badge        string       `json:"badge"`
	Capabilities []string     `json:"capabilities,omitempty"`
	Components   []string     `json:"components,omitempty"`
	Baseline     string       `json:"baseline"`
	Fixture      string       `json:"fixture,omitempty"`
	Test         string       `json:"test,omitempty"`
	Commands     []string     `json:"commands,omitempty"`
	Environment  string       `json:"environment,omitempty"`
	Run          string       `json:"run,omitempty"`
	Commit       string       `json:"commit,omitempty"`
	Date         string       `json:"date,omitempty"`
	Result       string       `json:"result,omitempty"`
	Limitations  string       `json:"limitations"`
	Cluster      *Cluster     `json:"cluster,omitempty"`
	Measurement  *Measurement `json:"measurement,omitempty"`
	Reason       string       `json:"reason,omitempty"`
	Guide        string       `json:"guide"` // set by the loader
}

// Exercised reports whether the record claims a run.
func (e EvidenceRecord) Exercised() bool { return e.Badge != "not-exercised" }

// Cluster describes a cluster-run environment.
type Cluster struct {
	Kind              string `json:"kind"`
	KubernetesVersion string `json:"kubernetesVersion"`
	CNI               string `json:"cni"`
}

// Measurement describes a measured record.
type Measurement struct {
	Samples   int    `json:"samples"`
	Statistic string `json:"statistic"`
	Unit      string `json:"unit"`
	Protocol  string `json:"protocol"`
}

// Invalidation is one record of data/invalidations.yaml.
type Invalidation struct {
	ID         string   `json:"id"`
	Guides     []string `json:"guides"`
	Components []string `json:"components"`
	Reason     string   `json:"reason"`
	Opened     string   `json:"opened"`
	Closed     *string  `json:"closed"`
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
	Baseline      string         `json:"baseline"`
	Stage         int            `json:"stage"`
	Options       map[string]any `json:"options"`
	Exercises     []string       `json:"exercises"`
	RequiredTests []string       `json:"requiredTests"`
	Dir           string         `json:"-"`
}

// GuideMeta is the guide block of a guide page's frontmatter.
type GuideMeta struct {
	ID           string        `json:"id"`
	Prefix       string        `json:"prefix"`
	State        string        `json:"state"`
	Baseline     string        `json:"baseline"`
	Components   []string      `json:"components"`
	Verification *Verification `json:"verification"`
	Questions    []Question    `json:"questions"`
	Sources      []Source      `json:"sources,omitempty"`
}

// Verification records one complete fixture run for a guide.
type Verification struct {
	Run      string `json:"run"`
	Commit   string `json:"commit"`
	Date     string `json:"date"`
	Baseline string `json:"baseline"`
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
