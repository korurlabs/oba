package seed

import (
	"fmt"
	"sort"

	"github.com/korurlabs/kaya-cli/internal/client"
)

// Mismatch is one delivered path where a release and the repository on disk differ.
type Mismatch struct {
	Path   string
	Reason string
}

// Compare checks a release against what the repository holds at delivered paths: the seed passes
// only when every path has the same sha256, mode and symlink target, and nothing on disk is left
// out of the release.
//
// Before the cut-over the disk still holds the inherited rules as git has them, while a release
// delivers a rule that a mounted pack's override narrows as its narrowed copy, and leaves out a rule the
// override narrows to no path at all. Such a rule differs by design until the cut-over, so a
// narrowed entry is compared without its sha256 and a rule overridden away may stay on disk. After
// the cut-over the disk is Kaya's output and must equal the release exactly.
func Compare(release client.Release, disk map[string]Entry, cutOver bool) []Mismatch {
	var mismatches []Mismatch
	delivered := map[string]bool{}
	overridden := map[string]bool{}
	for _, file := range release.Files {
		if file.OverridesPath != "" {
			overridden[file.OverridesPath] = true
		}
	}
	for _, file := range release.Files {
		delivered[file.Path] = true
		entry, ok := disk[file.Path]
		narrowed := file.NarrowedBy != "" && !cutOver
		switch {
		case !ok:
			mismatches = append(mismatches, Mismatch{file.Path, "in the release, not on disk"})
		case entry.Mode != file.Mode:
			mismatches = append(mismatches, Mismatch{file.Path, fmt.Sprintf("mode %s in the release, %s on disk", file.Mode, entry.Mode)})
		case file.Mode == client.ModeSymlink && entry.Target != file.Target:
			mismatches = append(mismatches, Mismatch{file.Path, fmt.Sprintf("link to %s in the release, to %s on disk", file.Target, entry.Target)})
		case file.Mode != client.ModeSymlink && entry.SHA256 != file.SHA256 && !narrowed:
			mismatches = append(mismatches, Mismatch{file.Path, fmt.Sprintf("sha256 %s in the release, %s on disk", short(file.SHA256), short(entry.SHA256))})
		}
	}
	for path := range disk {
		if !delivered[path] && (cutOver || !overridden[path]) {
			mismatches = append(mismatches, Mismatch{path, "on disk, not in the release"})
		}
	}
	sort.Slice(mismatches, func(i, j int) bool { return mismatches[i].Path < mismatches[j].Path })
	return mismatches
}

func short(sha string) string {
	if sha == "" {
		return "(none)"
	}
	if len(sha) > 12 {
		return sha[:12]
	}
	return sha
}
