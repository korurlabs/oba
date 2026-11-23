package delivery

import (
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonedit"
)

// keepVersions is how many of the newest versions stay under .kaya/versions/ besides the active
// and the staged one, so that a restore or a rollback needs no download.
const keepVersions = 3

// hasVersion reports whether release number is built under .kaya/versions/.
func (c *Checkout) hasVersion(number int) bool {
	info, err := os.Lstat(c.versionDir(number))
	if err != nil || !info.IsDir() {
		return false
	}
	_, err = os.Lstat(c.releasePath(number))
	return err == nil
}

// buildVersion writes the complete tree of a verified release into .kaya/versions/<n>/ from the
// blob cache: a temporary directory first, renamed into place only when every file is written,
// so a failure never leaves a partial version that a switch could pick. An existing version is
// replaced, which is how a damaged one is restored.
func (c *Checkout) buildVersion(release client.DeliveryRelease, manifest Manifest, cache Cache) error {
	if err := c.ensureKayaDir(); err != nil {
		return err
	}
	tmp, err := os.MkdirTemp(c.versionsDir(), fmt.Sprintf(".build-%d-", manifest.Number))
	if err != nil {
		return fmt.Errorf("create a build directory: %w", err)
	}
	built := false
	defer func() {
		if !built {
			_ = os.RemoveAll(tmp)
		}
	}()
	for _, file := range manifest.Files {
		dest := filepath.Join(tmp, filepath.FromSlash(file.Path))
		if err := os.MkdirAll(filepath.Dir(dest), 0o750); err != nil {
			return fmt.Errorf("create the directory of %s: %w", file.Path, err)
		}
		if file.Mode == ModeSymlink {
			if err := os.Symlink(file.Target, dest); err != nil {
				return fmt.Errorf("link %s: %w", file.Path, err)
			}
			continue
		}
		data, err := cache.Get(file.SHA256)
		if err != nil {
			return fmt.Errorf("%s: %w", file.Path, err)
		}
		if err := os.WriteFile(dest, data, 0o600); err != nil {
			return fmt.Errorf("write %s: %w", file.Path, err)
		}
		// The executable bit is set only for an EXECUTABLE entry of a signed manifest.
		if err := os.Chmod(dest, fileMode(file)); err != nil {
			return fmt.Errorf("set the mode of %s: %w", file.Path, err)
		}
	}
	data, err := json.MarshalIndent(release, "", "  ")
	if err != nil {
		return fmt.Errorf("encode release %d: %w", manifest.Number, err)
	}
	if err := writeFileAtomic(c.releasePath(manifest.Number), append(data, '\n'), 0o600); err != nil {
		return err
	}
	final := c.versionDir(manifest.Number)
	old := ""
	if _, err := os.Lstat(final); err == nil {
		old = fmt.Sprintf("%s.old-%d", final, time.Now().UnixNano())
		if err := os.Rename(final, old); err != nil {
			return fmt.Errorf("replace version %d: %w", manifest.Number, err)
		}
	}
	if err := os.Rename(tmp, final); err != nil {
		return fmt.Errorf("install version %d: %w", manifest.Number, err)
	}
	built = true
	if old != "" {
		if err := os.RemoveAll(old); err != nil {
			return fmt.Errorf("remove the replaced version %d: %w", manifest.Number, err)
		}
	}
	return nil
}

func fileMode(file ManifestFile) fs.FileMode {
	if file.Mode == ModeExecutable {
		return 0o755
	}
	return 0o644
}

// loadRelease reads the stored signed release n and verifies it again, so that a release file
// edited on disk is never trusted.
func (c *Checkout) loadRelease(number int, keys Keys) (client.DeliveryRelease, Manifest, error) {
	data, err := os.ReadFile(c.releasePath(number))
	if err != nil {
		return client.DeliveryRelease{}, Manifest{}, fmt.Errorf("release %d: %w", number, err)
	}
	var release client.DeliveryRelease
	if err := json.Unmarshal(data, &release); err != nil {
		return client.DeliveryRelease{}, Manifest{}, fmt.Errorf("release %d: the stored copy is damaged: %w", number, err)
	}
	manifest, err := VerifyRelease(release, keys)
	if err != nil {
		return client.DeliveryRelease{}, Manifest{}, err
	}
	if err := ValidateManifest(manifest); err != nil {
		return client.DeliveryRelease{}, Manifest{}, err
	}
	return release, manifest, nil
}

// activate points .kaya/current at version n with one rename, then makes the checkout match it:
// links of the previous release that n no longer delivers are removed, every delivered path is
// placed, and the release record is written. Before any of that it refuses a release that would
// replace a path git tracks, and adds the release's paths to the exclude block, so a refusal or a
// failure to write the block changes no file, and git never sees a placed path.
func (c *Checkout) activate(manifest Manifest, prev *Manifest, release client.DeliveryRelease, cache Cache) error {
	if err := c.refuseTracked(manifest); err != nil {
		return err
	}
	if err := c.WriteExcludes(manifest); err != nil {
		return err
	}
	tmpLink := c.currentLink() + ".tmp"
	_ = os.Remove(tmpLink)
	if err := os.Symlink(filepath.Join("versions", strconv.Itoa(manifest.Number)), tmpLink); err != nil {
		return fmt.Errorf("prepare the switch to release %d: %w", manifest.Number, err)
	}
	if err := os.Rename(tmpLink, c.currentLink()); err != nil {
		_ = os.Remove(tmpLink)
		return fmt.Errorf("switch to release %d: %w", manifest.Number, err)
	}
	if prev != nil {
		if err := c.removeStale(*prev, manifest); err != nil {
			return err
		}
	}
	if err := c.place(manifest, cache); err != nil {
		return err
	}
	return c.writeReleaseRecord(manifest, release)
}

// removeStale removes the paths prev delivered and next does not: only links and the settings
// file the CLI wrote, never a regular file or directory someone else put there.
func (c *Checkout) removeStale(prev, next Manifest) error {
	keep := next.ByPath()
	for _, file := range prev.Files {
		if _, ok := keep[file.Path]; ok {
			continue
		}
		abs := filepath.Join(c.Root, filepath.FromSlash(file.Path))
		info, err := os.Lstat(abs)
		if errors.Is(err, fs.ErrNotExist) {
			continue
		}
		if err != nil {
			return fmt.Errorf("inspect %s: %w", file.Path, err)
		}
		if info.Mode()&fs.ModeSymlink == 0 && file.Path != SettingsPath {
			continue
		}
		if err := os.Remove(abs); err != nil {
			return fmt.Errorf("remove %s, which release %d no longer delivers: %w", file.Path, next.Number, err)
		}
		removeEmptyParents(c.Root, filepath.Dir(abs))
	}
	return nil
}

// removeEmptyParents removes directories left empty below the checkout root, up to the first
// one that still holds something.
func removeEmptyParents(root, dir string) {
	for dir != root && strings.HasPrefix(dir, root+string(filepath.Separator)) {
		if os.Remove(dir) != nil {
			return
		}
		dir = filepath.Dir(dir)
	}
}

// place writes every delivered path into the checkout: a file as a link into .kaya/current, a
// link as the link the manifest names, and .claude/settings.json as a regular file with the
// guardrail deny rules merged in. It refuses a path whose directory in the checkout is a link
// (for example a link committed in git), because writing there would follow the link. Its only
// caller, activate, has refused a path git tracks and written the exclude block first.
func (c *Checkout) place(manifest Manifest, cache Cache) error {
	for _, file := range manifest.Files {
		if err := c.checkParents(file.Path); err != nil {
			return err
		}
		abs := filepath.Join(c.Root, filepath.FromSlash(file.Path))
		if err := os.MkdirAll(filepath.Dir(abs), 0o750); err != nil {
			return fmt.Errorf("create the directory of %s: %w", file.Path, err)
		}
		if info, err := os.Lstat(abs); err == nil && info.IsDir() {
			return fmt.Errorf("%s is a directory in the checkout; Kaya delivers a file there", file.Path)
		}
		switch {
		case file.Path == SettingsPath:
			content, err := c.expectedSettings(manifest, file, cache)
			if err != nil {
				return err
			}
			if err := replaceWithFile(abs, content); err != nil {
				return err
			}
		case file.Mode == ModeSymlink:
			if err := replaceWithLink(abs, file.Target); err != nil {
				return err
			}
		default:
			target, err := c.linkTarget(file.Path)
			if err != nil {
				return err
			}
			if err := replaceWithLink(abs, target); err != nil {
				return err
			}
		}
	}
	return nil
}

// checkParents refuses a delivered path below a link in the checkout.
func (c *Checkout) checkParents(path string) error {
	if err := CheckParents(c.Root, path); err != nil {
		return fmt.Errorf("%w (remove it, or let Kaya deliver it)", err)
	}
	return nil
}

// CheckParents refuses a repository path whose parent directories below root hold a link or a
// file. Opening the path would follow such a link, so Kaya would write into, or read and send,
// a directory outside the repository. A parent that does not exist is no problem: the path
// does not exist either.
func CheckParents(root, path string) error {
	segments := strings.Split(path, "/")
	current := root
	for _, segment := range segments[:len(segments)-1] {
		current = filepath.Join(current, segment)
		info, err := os.Lstat(current)
		if errors.Is(err, fs.ErrNotExist) {
			return nil
		}
		if err != nil {
			return fmt.Errorf("inspect %s: %w", current, err)
		}
		if info.Mode()&fs.ModeSymlink != 0 {
			rel, _ := filepath.Rel(root, current)
			return fmt.Errorf("%s lies below the link %s in %s; Kaya does not read or write through a link", path, filepath.ToSlash(rel), root)
		}
		if !info.IsDir() {
			return fmt.Errorf("%s: %s is a file in %s", path, current, root)
		}
	}
	return nil
}

// LinkTarget is the link a delivered file is placed as: relative, into .kaya/current.
func (c *Checkout) LinkTarget(path string) (string, error) {
	return c.linkTarget(path)
}

// linkTarget is the relative link from a delivered path to its copy under .kaya/current.
func (c *Checkout) linkTarget(path string) (string, error) {
	abs := filepath.Join(c.Root, filepath.FromSlash(path))
	target, err := filepath.Rel(filepath.Dir(abs), filepath.Join(c.currentLink(), filepath.FromSlash(path)))
	if err != nil {
		return "", fmt.Errorf("link %s: %w", path, err)
	}
	return target, nil
}

// expectedSettings is the exact content .claude/settings.json must have: the release's file,
// with the deny rules of the release's guardrails added to permissions.deny.
func (c *Checkout) expectedSettings(manifest Manifest, file ManifestFile, cache Cache) ([]byte, error) {
	content, err := cache.Get(file.SHA256)
	if err != nil {
		return nil, fmt.Errorf("%s: %w", SettingsPath, err)
	}
	return MergeDeny(content, manifest.GuardrailDeny()...)
}

// MergeDeny adds deny rules to permissions.deny of a settings file: after the file's own rules
// come the guardrail rules in the order given, and a rule already in the list, whether the
// file's own or one two guardrails share, is written once. Without guardrail rules the content
// is returned unchanged, byte for byte, so a release without them is placed exactly as signed.
func MergeDeny(content []byte, guardrails ...string) ([]byte, error) {
	if len(guardrails) == 0 {
		return content, nil
	}
	settings, err := jsonedit.Parse(content)
	if err != nil {
		return nil, fmt.Errorf("%s: %w", SettingsPath, err)
	}
	permissions, err := settings.Object("permissions")
	if err != nil {
		return nil, fmt.Errorf("%s: %w", SettingsPath, err)
	}
	var deny []string
	if raw, ok := permissions.Get("deny"); ok {
		if err := json.Unmarshal(raw, &deny); err != nil {
			return nil, fmt.Errorf("%s: permissions.deny is not a list of rules: %w", SettingsPath, err)
		}
	}
	present := map[string]bool{}
	for _, rule := range deny {
		present[rule] = true
	}
	for _, rule := range guardrails {
		if !present[rule] {
			deny = append(deny, rule)
			present[rule] = true
		}
	}
	if err := permissions.Set("deny", deny); err != nil {
		return nil, err
	}
	if err := settings.Set("permissions", permissions); err != nil {
		return nil, err
	}
	return settings.Marshal()
}

func replaceWithLink(abs, target string) error {
	if current, err := os.Readlink(abs); err == nil && current == target {
		return nil
	}
	tmp := abs + ".kaya-tmp"
	_ = os.Remove(tmp)
	if err := os.Symlink(target, tmp); err != nil {
		return fmt.Errorf("link %s: %w", abs, err)
	}
	if err := os.Rename(tmp, abs); err != nil {
		_ = os.Remove(tmp)
		return fmt.Errorf("link %s: %w", abs, err)
	}
	return nil
}

func replaceWithFile(abs string, content []byte) error {
	if info, err := os.Lstat(abs); err == nil && info.Mode().IsRegular() {
		if current, err := os.ReadFile(abs); err == nil && string(current) == string(content) { // #nosec G304 -- a delivered path of the checkout
			return nil
		}
	}
	return writeFileAtomic(abs, content, 0o644)
}

// releaseRecord is .agents/.kaya-release.json.
type releaseRecord struct {
	Number         int    `json:"number"`
	ReleaseID      string `json:"releaseId"`
	ManifestSHA256 string `json:"manifestSha256"`
	CreatedAt      string `json:"createdAt"`
	ActivatedAt    string `json:"activatedAt"`
}

func (c *Checkout) writeReleaseRecord(manifest Manifest, release client.DeliveryRelease) error {
	record := releaseRecord{
		Number:         manifest.Number,
		ReleaseID:      release.ReleaseID,
		ManifestSHA256: strings.ToLower(release.ManifestSHA256),
		CreatedAt:      manifest.CreatedAt,
		ActivatedAt:    time.Now().UTC().Format(time.RFC3339),
	}
	data, err := json.MarshalIndent(record, "", "  ")
	if err != nil {
		return fmt.Errorf("encode the release record: %w", err)
	}
	path := filepath.Join(c.Root, filepath.FromSlash(ReleaseRecordPath))
	if err := c.checkParents(ReleaseRecordPath); err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o750); err != nil {
		return fmt.Errorf("create %s: %w", filepath.Dir(path), err)
	}
	return writeFileAtomic(path, append(data, '\n'), 0o644)
}

// prune removes versions and stored releases beyond the newest keepVersions, never the active
// or the staged one.
func (c *Checkout) prune(state State) error {
	entries, err := os.ReadDir(c.versionsDir())
	if err != nil {
		return fmt.Errorf("read %s: %w", c.versionsDir(), err)
	}
	var numbers []int
	for _, entry := range entries {
		if strings.HasPrefix(entry.Name(), ".build-") {
			// A build directory is complete only once renamed; one left behind is a crash.
			_ = os.RemoveAll(filepath.Join(c.versionsDir(), entry.Name()))
			continue
		}
		if n, err := strconv.Atoi(entry.Name()); err == nil {
			numbers = append(numbers, n)
		}
	}
	sort.Sort(sort.Reverse(sort.IntSlice(numbers)))
	for i, n := range numbers {
		if i < keepVersions || n == state.Active || n == state.Staged {
			continue
		}
		if err := os.RemoveAll(c.versionDir(n)); err != nil {
			return fmt.Errorf("remove version %d: %w", n, err)
		}
		if err := os.Remove(c.releasePath(n)); err != nil && !errors.Is(err, fs.ErrNotExist) {
			return fmt.Errorf("remove release %d: %w", n, err)
		}
	}
	return nil
}
