package seed

import (
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"github.com/korurlabs/kaya-cli/internal/client"
)

// Entry is one file as it is on disk: a regular file with its hash and executable bit, or a
// symlink with its target as the link stores it.
type Entry struct {
	// Path is relative to the repository root, with '/' separators.
	Path string
	// Mode is FILE, EXECUTABLE or SYMLINK.
	Mode string
	// SHA256 is the lower-case hex hash of the content; empty for a SYMLINK.
	SHA256 string
	// Target is the link target of a SYMLINK.
	Target string
	// Source is the absolute path the content is read from for the upload.
	Source string
	// Size is the content size in bytes.
	Size int64
}

// SeedFile is the entry as a file of the seed request.
func (e Entry) SeedFile() client.SeedFile {
	return client.SeedFile{Path: e.Path, SHA256: e.SHA256, Mode: e.Mode, SymlinkTarget: e.Target}
}

// readEntry reads the file at abs without following a final symlink. ok is false for a
// directory, a device, a socket or a pipe.
func readEntry(abs, rel string) (Entry, bool, error) {
	info, err := os.Lstat(abs)
	if err != nil {
		return Entry{}, false, fmt.Errorf("read %s: %w", abs, err)
	}
	switch {
	case info.Mode()&fs.ModeSymlink != 0:
		target, err := os.Readlink(abs)
		if err != nil {
			return Entry{}, false, fmt.Errorf("read the link %s: %w", abs, err)
		}
		return Entry{Path: rel, Mode: client.ModeSymlink, Target: filepath.ToSlash(target), Source: abs}, true, nil
	case info.Mode().IsRegular():
		entry := Entry{Path: rel, Mode: client.ModeFile, Source: abs, Size: info.Size()}
		if info.Mode().Perm()&0o111 != 0 {
			entry.Mode = client.ModeExecutable
		}
		if info.Size() > MaxBlobBytes {
			return entry, true, nil
		}
		sum, err := hashFile(abs)
		if err != nil {
			return Entry{}, false, err
		}
		entry.SHA256 = sum
		return entry, true, nil
	default:
		return Entry{}, false, nil
	}
}

func hashFile(abs string) (string, error) {
	content, err := os.ReadFile(abs) // #nosec G304 -- the seed reads the files of the checkout it was pointed at
	if err != nil {
		return "", fmt.Errorf("read %s: %w", abs, err)
	}
	sum := sha256.Sum256(content)
	return hex.EncodeToString(sum[:]), nil
}

// RepoScan is what a consumer repository holds under .agents/, .claude/, the convention docs and
// its root agent files.
type RepoScan struct {
	// Delivered are the files on the allow-list, by path.
	Delivered map[string]Entry
	// NotImported are the other paths, with the reason.
	NotImported []client.NotImported
}

// rootFiles are the agent files at a repository root the seed looks at; only AGENTS.md is delivered.
var rootFiles = []string{"AGENTS.md", "CLAUDE.md"}

// walkRoots are the directories the seed walks: the agent config and the convention docs.
var walkRoots = []string{".agents", ".claude", ConventionsDir}

// ScanRepo reads the agent config of the checkout at root.
func ScanRepo(root string) (RepoScan, error) {
	scan := RepoScan{Delivered: map[string]Entry{}}
	for _, name := range rootFiles {
		abs := filepath.Join(root, name)
		if _, err := os.Lstat(abs); errors.Is(err, os.ErrNotExist) {
			continue
		}
		if err := scan.add(abs, name); err != nil {
			return RepoScan{}, err
		}
	}
	for _, dir := range walkRoots {
		base := filepath.Join(root, filepath.FromSlash(dir))
		info, err := os.Lstat(base)
		if errors.Is(err, os.ErrNotExist) {
			continue
		}
		if err != nil {
			return RepoScan{}, fmt.Errorf("read %s: %w", base, err)
		}
		if !info.IsDir() {
			if err := scan.add(base, dir); err != nil {
				return RepoScan{}, err
			}
			continue
		}
		err = filepath.WalkDir(base, func(abs string, d fs.DirEntry, walkErr error) error {
			if walkErr != nil {
				return walkErr
			}
			rel, err := filepath.Rel(root, abs)
			if err != nil {
				return err
			}
			rel = filepath.ToSlash(rel)
			if d.IsDir() {
				if reason, ok := collapsedDirs[rel]; ok {
					scan.NotImported = append(scan.NotImported, client.NotImported{Path: rel + "/", Reason: reason})
					return filepath.SkipDir
				}
				return nil
			}
			return scan.add(abs, rel)
		})
		if err != nil {
			return RepoScan{}, fmt.Errorf("walk %s: %w", base, err)
		}
	}
	sort.Slice(scan.NotImported, func(i, j int) bool { return scan.NotImported[i].Path < scan.NotImported[j].Path })
	return scan, nil
}

func (s *RepoScan) add(abs, rel string) error {
	entry, ok, err := readEntry(abs, rel)
	if err != nil {
		return err
	}
	switch {
	case !ok:
		s.NotImported = append(s.NotImported, client.NotImported{Path: rel, Reason: ReasonNotRegular})
	case !IsAllowedPath(rel):
		s.NotImported = append(s.NotImported, client.NotImported{Path: rel, Reason: reasonFor(rel, entry.Target, entry.Mode == client.ModeSymlink)})
	default:
		s.Delivered[rel] = entry
	}
	return nil
}

// sortedPaths returns the keys of entries in byte order.
func sortedPaths(entries map[string]Entry) []string {
	paths := make([]string, 0, len(entries))
	for path := range entries {
		paths = append(paths, path)
	}
	sort.Strings(paths)
	return paths
}

// prefixed names a path of one source in the report, where several repositories and the agents
// clone share one list: "<source>:<path>".
func prefixed(source, path string) string {
	return source + ":" + strings.TrimPrefix(path, "/")
}

// agentDirs are the directories whose files are agent config wherever they sit.
var agentDirs = map[string]bool{".agents": true, ".claude": true}

// scanMountFolder reads the folder root of the checkout at repoRoot: <root>/AGENTS.md is delivered;
// every other AGENTS.md or CLAUDE.md below it and every file of a .claude/ or .agents/ directory
// there stays in git. Other hidden directories are tool state and are not walked.
func scanMountFolder(repoRoot, root string) (RepoScan, error) {
	scan := RepoScan{Delivered: map[string]Entry{}}
	agents := root + "/AGENTS.md"
	base := filepath.Join(repoRoot, filepath.FromSlash(root))
	err := filepath.WalkDir(base, func(abs string, d fs.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		rel, err := filepath.Rel(repoRoot, abs)
		if err != nil {
			return err
		}
		rel = filepath.ToSlash(rel)
		inAgentDir := false
		for _, segment := range strings.Split(strings.TrimPrefix(rel, root), "/") {
			inAgentDir = inAgentDir || agentDirs[segment]
		}
		if d.IsDir() {
			name := d.Name()
			switch {
			case abs == base:
				return nil
			case strings.HasSuffix(rel, "/.claude/worktrees"):
				scan.NotImported = append(scan.NotImported, client.NotImported{Path: rel + "/", Reason: ReasonWorktrees})
				return filepath.SkipDir
			case name == "node_modules", strings.HasPrefix(name, ".") && !inAgentDir:
				// Dependencies and hidden tool state (.git, .next) hold no agent files of the pack.
				return filepath.SkipDir
			}
			return nil
		}
		switch {
		case rel == agents:
			entry, ok, err := readEntry(abs, rel)
			if err != nil {
				return err
			}
			if !ok {
				scan.NotImported = append(scan.NotImported, client.NotImported{Path: rel, Reason: ReasonNotRegular})
				return nil
			}
			scan.Delivered[rel] = entry
		case inAgentDir || d.Name() == "AGENTS.md" || d.Name() == "CLAUDE.md":
			scan.NotImported = append(scan.NotImported, client.NotImported{Path: rel, Reason: ReasonNestedAgentFiles})
		}
		return nil
	})
	if err != nil {
		return RepoScan{}, fmt.Errorf("walk %s: %w", base, err)
	}
	sort.Slice(scan.NotImported, func(i, j int) bool { return scan.NotImported[i].Path < scan.NotImported[j].Path })
	return scan, nil
}
