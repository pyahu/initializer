//go:build acceptance

package promotion

import (
	"path/filepath"
	"strings"
	"testing"

	h "github.com/pyahu/initializer/acceptance/harness"
)

func TestSaveLoadPreservesTheTestedImage(t *testing.T) {
	image := h.FixtureImage()
	original := strings.TrimSpace(h.MustDocker(t, "image", "inspect", "--format", "{{.Id}}", image))
	tag := h.Name("pyahu-promotion") + ":tested"
	promoted := strings.TrimSuffix(tag, ":tested") + ":release"
	t.Cleanup(func() { _, _ = h.Docker(t, "image", "rm", tag, promoted) })
	h.MustDocker(t, "tag", image, tag)
	archive := filepath.Join(t.TempDir(), "tested-image.tar")
	h.MustDocker(t, "save", "--output", archive, tag)
	h.MustDocker(t, "image", "rm", tag)
	h.MustDocker(t, "load", "--input", archive)
	h.MustDocker(t, "tag", tag, promoted)
	for _, ref := range []string{tag, promoted} {
		got := strings.TrimSpace(h.MustDocker(t, "image", "inspect", "--format", "{{.Id}}", ref))
		if got != original {
			t.Fatalf("%s changed from %s to %s", ref, original, got)
		}
	}
	t.Logf("RESULT: save/load and release retag preserve tested image ID %s", original)
}
