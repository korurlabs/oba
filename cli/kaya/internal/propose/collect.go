// Package propose builds a config proposal from a checkout: the delivered paths a developer or a
// session agent changed against the release the checkout runs, and the new paths beside them. It
// refuses locally what Kaya would refuse, so that a proposal fails before it is sent, and turns
// Kaya's refusals into texts that say what to do next.
package propose

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
	"github.com/korurlabs/kaya-cli/internal/delivery"
	"github.com/korurlabs/kaya-cli/internal/seed"
)

// Kind is what a proposal does to a path, relative to the release the checkout runs.
type Kind string

// Kinds of a change, as Kaya names them in a conflict.
const (
	Added    Kind = "ADDED"
	Modified Kind = "MODIFIED"
	Deleted  Kind = "DELETED"
)

// tmpSuffix marks the temporary link the delivery writes before its rename.
const tmpSuffix = ".kaya-tmp"

// Change is one path of a proposal. Content is nil for a link, a removal and a file over the size
// limit (Size still says how large it is).
type Change struct {
	Path    string
	Kind    Kind
	Mode    string
	Content []byte
	Size    int64
	Target  string
	// Base is the path as the release delivered it; nil for a new path.
	Base *delivery.ManifestFile
}

// TooLarge reports whether the file was too large to read.
func (c Change) TooLarge() bool {
	return c.Kind != Deleted && c.Mode != client.ModeSymlink && c.Content == nil && c.Size > maxFileBytes
}

// Source is where a proposal is read from: the checkout under its active release, or a
// directory that mirrors the repository's layout (From), for a session agent that may not edit
// delivered paths in place.
type Source struct {
	Checkout *delivery.Checkout
	Release  delivery.Manifest
	Cache    delivery.Cache
	From     string
}

func (s Source) root() string {
	if s.From != "" {
		return s.From
	}
	return s.Checkout.Root
}

// ResolveArgs turns command arguments into repository paths. Without From an argument is a
// path of the checkout, relative to cwd; with From it is the repository path itself, or an
// absolute path inside From.
func ResolveArgs(src Source, cwd string, args []string) ([]string, error) {
	var paths []string
	for _, arg := range args {
		base, abs := src.Checkout.Root, arg
		switch {
		case src.From != "":
			base = src.From
			if !filepath.IsAbs(arg) {
				abs = filepath.Join(src.From, arg)
			}
		case !filepath.IsAbs(arg):
			abs = filepath.Join(cwd, arg)
		}
		rel, err := filepath.Rel(base, filepath.Clean(abs))
		if err != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) {
			return nil, fmt.Errorf("%s is outside %s", arg, base)
		}
		if rel == "." {
			return nil, fmt.Errorf("%s is the root of %s; name the config files or directories to propose", arg, base)
		}
		paths = append(paths, filepath.ToSlash(rel))
	}
	return paths, nil
}

// Collect lists the changes of the named paths against the release, in path order. A named
// directory stands for every config file below it and, in the checkout, every delivered path
// below it. Without paths and removals it scans: every delivered path and every new file on the
// allow-list (with From, every config file of From). remove names delivered paths to remove.
func Collect(src Source, paths, remove []string) ([]Change, error) {
	delivered := src.Release.ByPath()
	candidates := map[string]bool{}
	if len(paths) == 0 && len(remove) == 0 {
		if src.From == "" {
			for path := range delivered {
				candidates[path] = true
			}
		}
		starts := []string{"AGENTS.md", ".agents", ".claude", seed.ConventionsDir}
		if src.From != "" {
			// Every config file of From, the AGENTS.md of a project folder included.
			starts = []string{"."}
		}
		for _, start := range starts {
			found, err := discover(src.root(), start, src.From != "")
			if err != nil {
				return nil, err
			}
			for _, path := range found {
				candidates[path] = true
			}
		}
	}
	for _, path := range paths {
		expanded, err := expand(src, delivered, path)
		if err != nil {
			return nil, err
		}
		for _, p := range expanded {
			candidates[p] = true
		}
	}

	var changes []Change
	for _, path := range remove {
		base, ok := delivered[path]
		if !ok {
			return nil, fmt.Errorf("release %d does not deliver %s; there is nothing to remove", src.Release.Number, path)
		}
		if candidates[path] {
			return nil, fmt.Errorf("%s is named to change and to remove", path)
		}
		changes = append(changes, Change{Path: path, Kind: Deleted, Base: &base})
	}
	for path := range candidates {
		var base *delivery.ManifestFile
		if file, ok := delivered[path]; ok {
			base = &file
		}
		change, err := compare(src, path, base)
		if err != nil {
			return nil, err
		}
		if change != nil {
			changes = append(changes, *change)
		}
	}
	sort.Slice(changes, func(i, j int) bool { return changes[i].Path < changes[j].Path })
	return changes, nil
}

// expand is the repository paths a named path stands for.
func expand(src Source, delivered map[string]delivery.ManifestFile, path string) ([]string, error) {
	var paths []string
	if src.From == "" {
		for p := range delivered {
			if p == path || strings.HasPrefix(p, path+"/") {
				paths = append(paths, p)
			}
		}
	}
	if err := delivery.CheckParents(src.root(), path); err != nil {
		return nil, err
	}
	info, err := os.Lstat(filepath.Join(src.root(), filepath.FromSlash(path)))
	switch {
	case err == nil && info.IsDir():
		found, err := discover(src.root(), path, src.From != "")
		if err != nil {
			return nil, err
		}
		paths = append(paths, found...)
		// A project folder of the checkout stands for its own AGENTS.md, not for the agent files
		// nested deeper, which stay in git.
		agents := path + "/AGENTS.md"
		if _, err := os.Lstat(filepath.Join(src.root(), filepath.FromSlash(agents))); src.From == "" && err == nil && seed.IsAllowedPath(agents) {
			paths = append(paths, agents)
		}
	case err == nil:
		paths = append(paths, path)
	case !errors.Is(err, fs.ErrNotExist):
		return nil, fmt.Errorf("inspect %s: %w", path, err)
	}
	if len(paths) > 0 {
		return paths, nil
	}
	if src.From != "" {
		return nil, fmt.Errorf("%s is not in %s; name a path to remove with --remove", path, src.From)
	}
	return nil, fmt.Errorf("%s is neither delivered by release %d nor in the checkout", path, src.Release.Number)
}

// discover lists the files and links on the allow-list at or below start, without following a
// link, and skips directories that can hold no config (.agents/memory, .claude/worktrees).
// agentsFiles also walks the folders that can hold a project's AGENTS.md (one to four levels, not
// hidden), as in a From directory, which holds only what the proposer put there; a checkout's
// folders also hold the nested agent files that stay in git.
func discover(root, start string, agentsFiles bool) ([]string, error) {
	abs := filepath.Join(root, filepath.FromSlash(start))
	if _, err := os.Lstat(abs); errors.Is(err, fs.ErrNotExist) {
		return nil, nil
	}
	var found []string
	err := filepath.WalkDir(abs, func(path string, entry fs.DirEntry, err error) error {
		if err != nil {
			return fmt.Errorf("read %s: %w", path, err)
		}
		rel, relErr := filepath.Rel(root, path)
		if relErr != nil {
			return fmt.Errorf("read %s: %w", path, relErr)
		}
		slash := filepath.ToSlash(rel)
		if entry.IsDir() {
			if slash == "." || slash == ".agents" || slash == ".claude" || seed.IsAllowedDir(slash) ||
				(agentsFiles && seed.IsAllowedPath(slash+"/AGENTS.md")) {
				return nil
			}
			return filepath.SkipDir
		}
		if seed.IsAllowedPath(slash) && !strings.HasSuffix(slash, tmpSuffix) {
			found = append(found, slash)
		}
		return nil
	})
	return found, err
}

// errDirectory answers a read of a path that is a directory, where a file was expected.
var errDirectory = errors.New("a directory")

// state is a path as the source holds it.
type state struct {
	exists  bool
	mode    string
	content []byte
	size    int64
	target  string
}

// read reads a path without following a final link, except the link the delivery placed for a
// delivered file: the checkout holds that file's content behind it, edited in place or not. A
// link in a parent directory is never followed, so no file outside the source is sent.
func read(src Source, path string, base *delivery.ManifestFile) (state, error) {
	if err := delivery.CheckParents(src.root(), path); err != nil {
		return state{}, err
	}
	abs := filepath.Join(src.root(), filepath.FromSlash(path))
	info, err := os.Lstat(abs)
	if errors.Is(err, fs.ErrNotExist) {
		return state{}, nil
	}
	if err != nil {
		return state{}, fmt.Errorf("inspect %s: %w", path, err)
	}
	switch {
	case info.Mode()&fs.ModeSymlink != 0:
		target, err := os.Readlink(abs)
		if err != nil {
			return state{}, fmt.Errorf("read the link %s: %w", path, err)
		}
		if src.From == "" && base != nil && base.Mode != client.ModeSymlink {
			if placed, err := src.Checkout.LinkTarget(path); err == nil && placed == target {
				followed, err := os.Stat(abs)
				if err != nil {
					return state{}, fmt.Errorf("%s: the delivered copy is missing; run `kaya sync`: %w", path, err)
				}
				return readFile(abs, path, followed)
			}
		}
		return state{exists: true, mode: client.ModeSymlink, target: filepath.ToSlash(target)}, nil
	case info.Mode().IsRegular():
		return readFile(abs, path, info)
	case info.IsDir():
		return state{}, errDirectory
	default:
		return state{}, fmt.Errorf("%s is not a regular file or a link", path)
	}
}

func readFile(abs, path string, info fs.FileInfo) (state, error) {
	st := state{exists: true, mode: client.ModeFile, size: info.Size()}
	if info.Mode().Perm()&0o111 != 0 {
		st.mode = client.ModeExecutable
	}
	if info.Size() > maxFileBytes {
		return st, nil
	}
	data, err := os.ReadFile(abs) // #nosec G304 -- a config path of the checkout or of the directory the proposer named
	if err != nil {
		return state{}, fmt.Errorf("read %s: %w", path, err)
	}
	st.content = data
	st.size = int64(len(data))
	return st, nil
}

// compare is the change of a path against the release, or nil when the path is as delivered.
func compare(src Source, path string, base *delivery.ManifestFile) (*Change, error) {
	st, err := read(src, path, base)
	if err != nil {
		if errors.Is(err, errDirectory) {
			return nil, fmt.Errorf("%s is a directory where release %d delivers a file", path, src.Release.Number)
		}
		return nil, err
	}
	if path == delivery.SettingsPath && st.content != nil && base != nil && base.Mode != client.ModeSymlink {
		baseContent, err := src.Cache.Get(base.SHA256)
		if err != nil {
			return nil, fmt.Errorf("%s: the delivered copy is not in the cache; run `kaya sync`: %w", path, err)
		}
		st.content, err = withoutMergedDeny(st.content, baseContent, src.Release.GuardrailDeny())
		if err != nil {
			return nil, err
		}
		st.size = int64(len(st.content))
	}
	change := &Change{Path: path, Mode: st.mode, Content: st.content, Size: st.size, Target: st.target, Base: base}
	switch {
	case base == nil && !st.exists:
		return nil, nil
	case base == nil:
		change.Kind = Added
	case !st.exists:
		return &Change{Path: path, Kind: Deleted, Base: base}, nil
	case sameAsBase(st, *base):
		return nil, nil
	default:
		change.Kind = Modified
	}
	return change, nil
}

func sameAsBase(st state, base delivery.ManifestFile) bool {
	if base.Mode == client.ModeSymlink || st.mode == client.ModeSymlink {
		return st.mode == base.Mode && st.target == base.Target
	}
	return st.mode == base.Mode && st.content != nil && hashOf(st.content) == base.SHA256
}

func hashOf(data []byte) string {
	sum := sha256.Sum256(data)
	return hex.EncodeToString(sum[:])
}
