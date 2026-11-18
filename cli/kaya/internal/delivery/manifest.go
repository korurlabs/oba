// Package delivery writes Kaya's signed config releases into a checkout: it verifies a release
// against the compiled-in signing keys and the server's path and link rules before anything is
// written, keeps the content-addressed blob cache, builds each version under .kaya/versions/,
// switches .kaya/current atomically, pins a version while sessions of the checkout are live, and
// restores delivered files that were changed by hand.
package delivery

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"slices"
	"strings"

	"github.com/korurlabs/kaya-cli/internal/client"
)

// File modes of a manifest entry.
const (
	ModeFile       = "FILE"
	ModeExecutable = "EXECUTABLE"
	ModeSymlink    = "SYMLINK"
)

// SettingsPath is the one delivered file written as a regular file into the checkout, because
// the deny rules of the release's guardrails are merged into its permissions.deny.
const SettingsPath = ".claude/settings.json"

// Manifest is the signed document of a release.
type Manifest struct {
	Organization string         `json:"organization"`
	RemoteURL    string         `json:"remoteUrl"`
	Number       int            `json:"number"`
	CreatedAt    string         `json:"createdAt"`
	Files        []ManifestFile `json:"files"`
}

// ManifestFile is one delivered path. SHA256 is set for FILE and EXECUTABLE, Target for SYMLINK.
// AlwaysOn marks the files Claude Code loads at launch (AGENTS.md and rules without paths).
// Deny holds the permission deny rules of a guardrail, in the order its frontmatter lists them;
// the signature covers them, so a tampered manifest can neither drop nor add one.
type ManifestFile struct {
	Path     string   `json:"path"`
	SHA256   string   `json:"sha256,omitempty"`
	Mode     string   `json:"mode"`
	Target   string   `json:"target,omitempty"`
	AlwaysOn bool     `json:"alwaysOn"`
	Deny     []string `json:"deny,omitempty"`
}

// GuardrailDeny is every deny rule of the release's guardrails, in manifest order. A rule two
// guardrails share is listed twice here; MergeDeny writes it once.
func (m Manifest) GuardrailDeny() []string {
	var rules []string
	for _, file := range m.Files {
		rules = append(rules, file.Deny...)
	}
	return rules
}

// VerifyRelease checks the signature over the manifest's exact bytes and its hash, and only then
// parses it. The caller still checks organization, remote and number against the checkout.
func VerifyRelease(release client.DeliveryRelease, keys Keys) (Manifest, error) {
	if err := keys.Verify(release.KeyID, release.Signature, []byte(release.Manifest)); err != nil {
		return Manifest{}, fmt.Errorf("release %d: %w", release.Number, err)
	}
	sum := sha256.Sum256([]byte(release.Manifest))
	if hex.EncodeToString(sum[:]) != strings.ToLower(strings.TrimSpace(release.ManifestSHA256)) {
		return Manifest{}, fmt.Errorf("release %d: the manifest does not hash to %s", release.Number, release.ManifestSHA256)
	}
	var manifest Manifest
	if err := json.Unmarshal([]byte(release.Manifest), &manifest); err != nil {
		return Manifest{}, fmt.Errorf("release %d: the signed manifest is not valid JSON: %w", release.Number, err)
	}
	if manifest.Number != release.Number {
		return Manifest{}, fmt.Errorf("release %d: the signed manifest names number %d", release.Number, manifest.Number)
	}
	return manifest, nil
}

// CheckOrigin refuses a manifest signed for another organization or repository, or older than
// the newest release this checkout has verified, so a validly signed release of another
// repository or an old one cannot be replayed.
func CheckOrigin(manifest Manifest, organization, remoteURL string, highest int) error {
	if organization != "" && !strings.EqualFold(manifest.Organization, organization) {
		return fmt.Errorf("release %d is signed for organization %s, not %s", manifest.Number, manifest.Organization, organization)
	}
	if manifest.RemoteURL != remoteURL {
		return fmt.Errorf("release %d is signed for %s, not for this checkout's %s", manifest.Number, manifest.RemoteURL, remoteURL)
	}
	if manifest.Number < highest {
		return fmt.Errorf("release %d is older than release %d, which this checkout already verified", manifest.Number, highest)
	}
	return nil
}

// Equal reports whether two entries deliver the same thing, deny rules included.
func (f ManifestFile) Equal(other ManifestFile) bool {
	return f.Path == other.Path && f.SHA256 == other.SHA256 && f.Mode == other.Mode && f.Target == other.Target &&
		f.AlwaysOn == other.AlwaysOn && slices.Equal(f.Deny, other.Deny)
}

// ByPath indexes the files of a manifest.
func (m Manifest) ByPath() map[string]ManifestFile {
	files := make(map[string]ManifestFile, len(m.Files))
	for _, file := range m.Files {
		files[file.Path] = file
	}
	return files
}

// AlwaysOnChanged reports whether next adds, removes or changes a file Claude Code loads at
// launch, compared with prev. Such a change cannot apply inside a running session.
func AlwaysOnChanged(prev, next Manifest) bool {
	before := alwaysOnFiles(prev)
	after := alwaysOnFiles(next)
	if len(before) != len(after) {
		return true
	}
	for path, file := range after {
		if !before[path].Equal(file) {
			return true
		}
	}
	return false
}

func alwaysOnFiles(m Manifest) map[string]ManifestFile {
	files := map[string]ManifestFile{}
	for _, file := range m.Files {
		if file.AlwaysOn {
			files[file.Path] = file
		}
	}
	return files
}

// Changes lists the paths added or changed from prev to next in path order, then the removed ones.
func Changes(prev, next Manifest) []string {
	before := prev.ByPath()
	after := next.ByPath()
	var changed []string
	for _, file := range next.Files {
		if old, ok := before[file.Path]; !ok || !old.Equal(file) {
			changed = append(changed, file.Path)
		}
	}
	for _, file := range prev.Files {
		if _, ok := after[file.Path]; !ok {
			changed = append(changed, file.Path+" (removed)")
		}
	}
	return changed
}

// Delivers reports whether path is a delivered path of the manifest, ignoring case and Unicode
// normalization as a case-insensitive file system does.
func (m Manifest) Delivers(path string) bool {
	want := fold(path)
	for _, file := range m.Files {
		if fold(file.Path) == want {
			return true
		}
	}
	return false
}
