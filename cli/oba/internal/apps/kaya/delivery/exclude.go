package delivery

import (
	"bytes"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"slices"
	"strings"
)

// The marker lines of the block Kaya keeps in info/exclude. Everything between them is Kaya's;
// every line outside them is the user's and is never changed.
const (
	ExcludeBegin = "# >>> oba kaya >>>"
	ExcludeEnd   = "# <<< oba kaya <<<"
)

// excludeNote is the comment line at the top of the block, for a reader of the file.
const excludeNote = "# Paths oba kaya delivers into this repository's checkouts; remove the block to undo."

// excludeRoots are the top-level directories Kaya owns in a delivered checkout. Each is excluded
// whole; every other delivered path is excluded on its own, so nothing else of the repository is
// hidden from git.
var excludeRoots = []string{".agents", ".claude", ".kaya"}

// ExcludeEntries are the info/exclude lines that keep a release's paths out of git: the three
// directories Kaya owns, then every other delivered path, each anchored at the repository root
// and without a trailing slash, so that a pattern matches the link or file Kaya places and
// nothing else of the same name deeper in the tree.
func ExcludeEntries(manifest Manifest) ([]string, error) {
	entries := make([]string, 0, len(excludeRoots)+len(manifest.Files))
	for _, root := range excludeRoots {
		entries = append(entries, "/"+root)
	}
	for _, file := range manifest.Files {
		top, _, _ := strings.Cut(file.Path, "/")
		if slices.Contains(excludeRoots, top) {
			continue
		}
		entry, err := excludePattern(file.Path)
		if err != nil {
			return nil, err
		}
		entries = append(entries, entry)
	}
	return entries, nil
}

// excludeSpecial matches the characters a gitignore pattern reads as a wildcard.
var excludeSpecial = regexp.MustCompile(`[\\*?\[]`)

// excludePattern is the anchored gitignore pattern that matches exactly path.
func excludePattern(path string) (string, error) {
	if strings.ContainsFunc(path, func(r rune) bool { return r < 0x20 || r == 0x7f }) {
		return "", fmt.Errorf("%q holds a control character and cannot be excluded from git", path)
	}
	pattern := "/" + excludeSpecial.ReplaceAllString(path, `\$0`)
	// git drops trailing spaces of a pattern unless each is escaped.
	trimmed := strings.TrimRight(pattern, " ")
	return trimmed + strings.Repeat(`\ `, len(pattern)-len(trimmed)), nil
}

// WriteExcludes adds the paths of manifest to Kaya's block in info/exclude of the repository's
// git common directory, which the main checkout and every linked worktree read, so that neither
// git status nor git add sees what Kaya delivers. Entries are only added: a path a later release
// no longer delivers may still be placed in another worktree that has not switched yet. No
// .gitignore and no tracked file is written.
func (c *Checkout) WriteExcludes(manifest Manifest) error {
	entries, err := ExcludeEntries(manifest)
	if err != nil {
		return err
	}
	commonDir, err := c.gitCommonDir()
	if err != nil {
		return err
	}
	path := filepath.Join(commonDir, "info", "exclude")
	content, mode, err := readExclude(path)
	if err != nil {
		return err
	}
	merged, err := MergeExcludeBlock(content, entries)
	if err != nil {
		return fmt.Errorf("%s: %w", path, err)
	}
	if bytes.Equal(merged, content) {
		return nil
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o750); err != nil {
		return fmt.Errorf("create %s: %w", filepath.Dir(path), err)
	}
	return writeFileAtomic(path, merged, mode)
}

// readExclude reads info/exclude and its permissions; a missing file is empty. A link is refused,
// since the rename that writes the file would replace the link instead of writing through it.
func readExclude(path string) ([]byte, fs.FileMode, error) {
	info, err := os.Lstat(path)
	if errors.Is(err, fs.ErrNotExist) {
		return nil, 0o644, nil
	}
	if err != nil {
		return nil, 0, fmt.Errorf("inspect %s: %w", path, err)
	}
	if !info.Mode().IsRegular() {
		return nil, 0, fmt.Errorf("%s is not a regular file; Kaya keeps its exclude block only in a regular file", path)
	}
	data, err := os.ReadFile(path) // #nosec G304 -- info/exclude of the checkout's own git directory
	if err != nil {
		return nil, 0, fmt.Errorf("read %s: %w", path, err)
	}
	return data, info.Mode().Perm(), nil
}

// MergeExcludeBlock returns content with Kaya's block holding its earlier entries and entries
// together: the three directories Kaya owns first, then every other entry in sorted order. The
// block stays where it was, or is appended when content has none; blocks repeated by hand are
// merged into the first. Every line outside the block is kept byte for byte.
func MergeExcludeBlock(content []byte, entries []string) ([]byte, error) {
	lines := strings.SplitAfter(string(content), "\n")
	if lines[len(lines)-1] == "" {
		lines = lines[:len(lines)-1]
	}
	var kept []string
	insertAt := -1
	present := map[string]bool{}
	inBlock := false
	for _, line := range lines {
		text := strings.TrimRight(line, "\r\n")
		switch {
		case !inBlock && text == ExcludeBegin:
			inBlock = true
			if insertAt < 0 {
				insertAt = len(kept)
			}
		case inBlock && text == ExcludeEnd:
			inBlock = false
		case inBlock && text == ExcludeBegin:
			return nil, fmt.Errorf("the line %q repeats before %q; remove Kaya's block and sync again", ExcludeBegin, ExcludeEnd)
		case inBlock:
			if text != "" && !strings.HasPrefix(text, "#") {
				present[text] = true
			}
		default:
			kept = append(kept, line)
		}
	}
	if inBlock {
		return nil, fmt.Errorf("the line %q has no %q after it; remove Kaya's block and sync again", ExcludeBegin, ExcludeEnd)
	}
	for _, entry := range entries {
		present[entry] = true
	}
	block := []string{ExcludeBegin + "\n", excludeNote + "\n"}
	for _, root := range excludeRoots {
		if present["/"+root] {
			block = append(block, "/"+root+"\n")
			delete(present, "/"+root)
		}
	}
	rest := make([]string, 0, len(present))
	for entry := range present {
		rest = append(rest, entry)
	}
	slices.Sort(rest)
	for _, entry := range rest {
		block = append(block, entry+"\n")
	}
	block = append(block, ExcludeEnd+"\n")
	if insertAt < 0 {
		insertAt = len(kept)
		if insertAt > 0 && !strings.HasSuffix(kept[insertAt-1], "\n") {
			kept[insertAt-1] += "\n"
		}
	}
	merged := slices.Concat(kept[:insertAt], block, kept[insertAt:])
	return []byte(strings.Join(merged, "")), nil
}

// gitCommonDir is the git directory the checkout shares with its linked worktrees, where
// info/exclude lives: .git of the main checkout for every one of them.
func (c *Checkout) gitCommonDir() (string, error) {
	out, err := runGit(c.Root, "rev-parse", "--git-common-dir")
	if err != nil {
		return "", fmt.Errorf("find the git directory of %s: %w", c.Root, err)
	}
	dir := strings.TrimRight(string(out), "\r\n")
	if dir == "" {
		return "", fmt.Errorf("find the git directory of %s: git named none", c.Root)
	}
	if !filepath.IsAbs(dir) {
		dir = filepath.Join(c.Root, dir)
	}
	return dir, nil
}

// TrackedError refuses a release that would replace paths git tracks in the checkout. Replacing
// a tracked file would show as a change to commit; the user untracks it first, keeping the file.
type TrackedError struct {
	Root  string
	Paths []string
}

func (e *TrackedError) Error() string {
	var b strings.Builder
	fmt.Fprintf(&b, "git tracks %d path(s) in %s that Kaya delivers, so no delivered file was changed; "+
		"untrack each with the command below, which keeps the file, then sync again:", len(e.Paths), e.Root)
	for _, path := range e.Paths {
		b.WriteString("\n  git rm --cached -- " + shellQuote(path))
	}
	return b.String()
}

// refuseTracked fails with a *TrackedError when git lists any path the release writes into the
// checkout: a delivered path, a tracked file below one, or the release record. Pathspecs are
// literal, so a delivered name that looks like a glob matches only itself.
func (c *Checkout) refuseTracked(manifest Manifest) error {
	args := make([]string, 0, len(manifest.Files)+5)
	args = append(args, "--literal-pathspecs", "ls-files", "-z", "--", ReleaseRecordPath)
	for _, file := range manifest.Files {
		args = append(args, file.Path)
	}
	out, err := runGit(c.Root, args...)
	if err != nil {
		return fmt.Errorf("list the files git tracks in %s: %w", c.Root, err)
	}
	var tracked []string
	for _, path := range strings.Split(string(out), "\x00") {
		if path != "" {
			tracked = append(tracked, path)
		}
	}
	if len(tracked) == 0 {
		return nil
	}
	slices.Sort(tracked)
	return &TrackedError{Root: c.Root, Paths: slices.Compact(tracked)}
}

// runGit runs git in root with the variables that point git at another repository dropped, such
// as the GIT_DIR a git hook runs under, and names git's own message on a failure.
func runGit(root string, args ...string) ([]byte, error) {
	cmd := exec.Command("git", append([]string{"-C", root}, args...)...) // #nosec G204 -- fixed git subcommands on the checkout
	cmd.Env = withoutGitVars(os.Environ())
	out, err := cmd.Output()
	if err != nil {
		name := args[0]
		if i := slices.IndexFunc(args, func(arg string) bool { return !strings.HasPrefix(arg, "-") }); i >= 0 {
			name = args[i]
		}
		var exitErr *exec.ExitError
		if errors.As(err, &exitErr) && len(exitErr.Stderr) > 0 {
			return nil, fmt.Errorf("git %s: %s", name, strings.TrimSpace(string(exitErr.Stderr)))
		}
		return nil, fmt.Errorf("git %s: %w", name, err)
	}
	return out, nil
}

// shellQuote quotes a path for a POSIX shell when it needs it, so a printed command can be pasted.
func shellQuote(s string) string {
	if shellSafe.MatchString(s) {
		return s
	}
	return "'" + strings.ReplaceAll(s, "'", `'\''`) + "'"
}

var shellSafe = regexp.MustCompile(`^[A-Za-z0-9_@%+=:,./-]+$`)
