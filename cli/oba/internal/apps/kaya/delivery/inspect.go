package delivery

import (
	"bytes"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"strconv"
)

// Problem is a delivered path that does not match the active release.
type Problem struct {
	Path   string
	Reason string
	// inVersion marks damage below .kaya/versions/, which needs the version rebuilt from the
	// cache; any other problem is fixed by placing the path again.
	inVersion bool
}

func (p Problem) String() string {
	return p.Path + ": " + p.Reason
}

// Inspect compares the checkout with the active release: .kaya/current, every file of the
// version (content hash and executable bit), every delivered link and the settings file with
// exactly the deny rules of the release's guardrails. It changes nothing.
func (c *Checkout) Inspect(manifest Manifest, cache Cache) []Problem {
	var problems []Problem
	wantCurrent := filepath.Join("versions", strconv.Itoa(manifest.Number))
	if target, err := os.Readlink(c.currentLink()); err != nil || target != wantCurrent {
		problems = append(problems, Problem{Path: ".kaya/current", Reason: "does not point at version " + strconv.Itoa(manifest.Number)})
	}
	version := c.versionDir(manifest.Number)
	for _, file := range manifest.Files {
		problems = append(problems, c.inspectVersionFile(version, file)...)
		problems = append(problems, c.inspectPlaced(manifest, file, cache)...)
	}
	return problems
}

func (c *Checkout) inspectVersionFile(version string, file ManifestFile) []Problem {
	abs := filepath.Join(version, filepath.FromSlash(file.Path))
	info, err := os.Lstat(abs)
	if err != nil {
		return []Problem{{Path: file.Path, Reason: "missing from the cached version", inVersion: true}}
	}
	if file.Mode == ModeSymlink {
		if target, err := os.Readlink(abs); err != nil || target != file.Target {
			return []Problem{{Path: file.Path, Reason: "the cached link was changed", inVersion: true}}
		}
		return nil
	}
	if !info.Mode().IsRegular() {
		return []Problem{{Path: file.Path, Reason: "the cached copy is not a regular file", inVersion: true}}
	}
	data, err := os.ReadFile(abs) // #nosec G304 -- a file of the checkout's own .kaya/versions
	if err != nil || hashOf(data) != file.SHA256 {
		return []Problem{{Path: file.Path, Reason: "the cached copy was changed (checksum mismatch)", inVersion: true}}
	}
	if info.Mode().Perm() != fileMode(file) {
		return []Problem{{Path: file.Path, Reason: "the cached copy has the wrong mode", inVersion: true}}
	}
	return nil
}

func (c *Checkout) inspectPlaced(manifest Manifest, file ManifestFile, cache Cache) []Problem {
	abs := filepath.Join(c.Root, filepath.FromSlash(file.Path))
	info, err := os.Lstat(abs)
	if errors.Is(err, fs.ErrNotExist) {
		return []Problem{{Path: file.Path, Reason: "missing from the checkout"}}
	}
	if err != nil {
		return []Problem{{Path: file.Path, Reason: err.Error()}}
	}
	switch {
	case file.Path == SettingsPath:
		want, err := c.expectedSettings(manifest, file, cache)
		if err != nil {
			return []Problem{{Path: file.Path, Reason: err.Error()}}
		}
		got, readErr := os.ReadFile(abs) // #nosec G304 -- a delivered path of the checkout
		if !info.Mode().IsRegular() || readErr != nil || !bytes.Equal(got, want) {
			return []Problem{{Path: file.Path, Reason: "was changed (checksum mismatch)"}}
		}
	case file.Mode == ModeSymlink:
		if target, err := os.Readlink(abs); err != nil || target != file.Target {
			return []Problem{{Path: file.Path, Reason: "the link was changed"}}
		}
	default:
		want, err := c.linkTarget(file.Path)
		if err != nil {
			return []Problem{{Path: file.Path, Reason: err.Error()}}
		}
		if target, err := os.Readlink(abs); err != nil || target != want {
			return []Problem{{Path: file.Path, Reason: "was replaced (not the link to the release)"}}
		}
	}
	return nil
}

// restore rebuilds a damaged version from the blob cache and places every path again. It needs
// no network: every blob of an active release is cached.
func (c *Checkout) restore(problems []Problem, release Release, cache Cache) error {
	for _, problem := range problems {
		if problem.inVersion {
			if err := c.buildVersion(release.Signed, release.Manifest, cache); err != nil {
				return fmt.Errorf("restore version %d from the cache: %w", release.Manifest.Number, err)
			}
			break
		}
	}
	if err := c.activate(release.Manifest, nil, release.Signed, cache); err != nil {
		return fmt.Errorf("restore release %d: %w", release.Manifest.Number, err)
	}
	return nil
}
