package capture

import (
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"regexp"
	"slices"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"
)

// Kaya stores a plan of at most PlanContentLimit characters and a spec path of at most
// planPathLimit; a longer one would make it refuse the session's whole batch, so it is never
// captured.
const (
	PlanContentLimit = 200_000
	planPathLimit    = 500
)

// Sources Kaya records for the plan of a session.
const (
	planSourcePlanMode = "PLAN_MODE"
	planSourceMessage  = "MESSAGE"
)

var (
	// planPathPattern is the repository path Kaya accepts for a spec plan.
	planPathPattern = regexp.MustCompile(`^docs/(?:.+/)?plans/.+\.md$`)
	worktreePrefix  = regexp.MustCompile(`^\.claude/worktrees/[^/]+/`)
	// planPathInCommand finds a word of a shell command that may name a spec file. The command is
	// not parsed; each candidate is checked once it is resolved to a file of the repository. The
	// first group is the word; the character after it is matched only to make sure the word ends
	// there.
	planPathInCommand = regexp.MustCompile("([^" + pySpace + "'\"`<>|;&()=]*plans/[^" + pySpace + "'\"`<>|;&()]*\\.md)(?:[^" + pyWord + ".-]|$)")
	planGoal          = regexp.MustCompile(planSection("Goal"))
	planSteps         = regexp.MustCompile(planSection("Steps"))
)

// planSection matches the Goal or Steps line of the core plan format, bold (`**Goal**:`,
// `1. **Steps:**`) or a heading (`## Goal`).
func planSection(word string) string {
	return `(?m)^[ \t]*(?:#{1,6}[ \t]+(?:\p{Nd}+[.)][ \t]+)?(?:\*\*)?` + word + `(?:[^` + pyWord + `]|$)|(?:[-*+][ \t]+|\p{Nd}+[.)][ \t]+)?\*\*` +
		word + `(?::\*\*|\*\*[ \t]*(?::|$)))`
}

func isoOf(t time.Time) string { return t.UTC().Format(isoSeconds) }

// snapshotHandoff copies a written file under handoffs/ to handoffs/<path below handoffs/> of the
// session's queue directory. It returns that path, or "" when the file is no handoff.
func snapshotHandoff(dir, filePath, cwd string) (string, error) {
	if filePath == "" {
		return "", nil
	}
	full := filePath
	if !filepath.IsAbs(full) {
		full = filepath.Join(cwd, full)
	}
	full = filepath.Clean(full)
	below := handoffsSegment.FindStringSubmatch(filepath.ToSlash(full))
	if below == nil {
		return "", nil
	}
	rel := below[1]
	if slices.Contains(strings.Split(rel, "/"), "..") {
		return "", fmt.Errorf("refused to snapshot %s: the path climbs out of handoffs/", filePath)
	}
	if _, err := copySnapshot(full, filepath.Join(dir, "handoffs", filepath.FromSlash(rel)), 0); err != nil {
		return "", err
	}
	return rel, nil
}

// copySnapshot copies source to target and gives the copy the source's modification time, so an
// unchanged file is recognized by its time alone and never copied again. With a limit, a file of
// more characters is not copied and false is returned.
func copySnapshot(source, target string, limit int) (bool, error) {
	data, err := os.ReadFile(source) // #nosec G304 -- a handoff or spec file of the session's repository
	if err != nil {
		return false, err
	}
	info, err := os.Stat(source)
	if err != nil {
		return false, err
	}
	// A character is at least one byte, so only a file of more bytes than the limit is counted.
	if limit > 0 && len(data) > limit && utf8.RuneCount(data) > limit {
		return false, nil
	}
	if err := writeAtomic(target, data); err != nil {
		return false, err
	}
	if err := os.Chtimes(target, info.ModTime(), info.ModTime()); err != nil {
		return false, fmt.Errorf("set the time of %s: %w", target, err)
	}
	return true, nil
}

// ValidPlanPath reports whether path is a repository path Kaya accepts for a spec plan.
func ValidPlanPath(path string) bool {
	if len(path) > planPathLimit || !planPathPattern.MatchString(path) {
		return false
	}
	for _, segment := range strings.Split(path, "/") {
		if segment == "" || segment == "." || segment == ".." {
			return false
		}
	}
	return true
}

// resolveIn is filePath made absolute against base, cleaned.
func resolveIn(filePath, base string) string {
	if filepath.IsAbs(filePath) {
		return filepath.Clean(filePath)
	}
	return filepath.Clean(filepath.Join(base, filePath))
}

// planPath is the path of a spec file in its repository, the worktree prefix
// `.claude/worktrees/<name>/` removed, or "" when filePath is no spec file of the main checkout
// root or its worktrees.
func planPath(filePath, root, cwd string) string {
	if filePath == "" || root == "" {
		return ""
	}
	base := cwd
	if base == "" {
		base = root
	}
	full := resolveIn(filePath, base)
	if !strings.HasPrefix(full, root+string(filepath.Separator)) {
		return ""
	}
	rel, err := filepath.Rel(root, full)
	if err != nil {
		return ""
	}
	rel = worktreePrefix.ReplaceAllString(filepath.ToSlash(rel), "")
	if !ValidPlanPath(rel) {
		return ""
	}
	return rel
}

// isPlanMessage reports whether an assistant message has the core plan format: a Goal line and
// a Steps line.
func isPlanMessage(text string) bool {
	return planGoal.MatchString(text) && planSteps.MatchString(text)
}

// commandPlanPaths are the words of a shell command that may name a spec file, in order.
func commandPlanPaths(command string) []string {
	var words []string
	for pos := 0; pos <= len(command); {
		loc := planPathInCommand.FindStringSubmatchIndex(command[pos:])
		if loc == nil {
			break
		}
		words = append(words, command[pos+loc[2]:pos+loc[3]])
		pos += loc[3]
	}
	return words
}

// snapshotSpec copies the spec file source to plans/files/<rel> unless the snapshot has its time
// already. With since (the session's start), a file without a snapshot that was last changed
// before it is left out: a shell command that only reads an older spec did not write it.
func snapshotSpec(dir, source, rel string, since *string, warn func(string, ...any)) (bool, error) {
	target := filepath.Join(dir, "plans", "files", filepath.FromSlash(rel))
	sourceInfo, err := os.Stat(source)
	if err != nil {
		return false, err
	}
	targetInfo, err := os.Stat(target)
	switch {
	case err == nil:
		if targetInfo.ModTime().Equal(sourceInfo.ModTime()) {
			return false, nil
		}
	case errors.Is(err, fs.ErrNotExist):
		if since != nil && *since != "" && isoOf(sourceInfo.ModTime()) < *since {
			return false, nil
		}
	default:
		return false, err
	}
	copied, err := copySnapshot(source, target, PlanContentLimit)
	if err != nil {
		return false, err
	}
	if !copied {
		warn("plan %s not captured: it has more than %d characters", source, PlanContentLimit)
	}
	return copied, nil
}

// snapshotSessionPlan writes the plan of the session to plans/session.md and its source to
// plans/session.json. The source is written first, so the sender, which goes by the time of
// session.md, never pairs new content with the source of the one before. Both files take the
// event's time, which the sender reports as the plan's capture time.
func snapshotSessionPlan(dir string, value any, source string, now time.Time, warn func(string, ...any)) (bool, error) {
	text, ok := value.(string)
	if !ok || pyStrip(text) == "" {
		return false, nil
	}
	if utf8.RuneCountInString(text) > PlanContentLimit {
		warn("plan of the session not captured: it has more than %d characters", PlanContentLimit)
		return false, nil
	}
	folder := filepath.Join(dir, "plans")
	path := filepath.Join(folder, "session.md")
	current, err := os.ReadFile(path) // #nosec G304 -- fixed name in the session's queue directory
	switch {
	case err == nil:
		if string(current) == text {
			return false, nil
		}
	case !errors.Is(err, fs.ErrNotExist):
		return false, fmt.Errorf("read %s: %w", path, err)
	}
	sourcePath := filepath.Join(folder, "session.json")
	if err := writeJSON(sourcePath, map[string]string{"source": source}); err != nil {
		return false, err
	}
	if err := writeAtomic(path, []byte(text)); err != nil {
		return false, err
	}
	for _, written := range []string{sourcePath, path} {
		if err := os.Chtimes(written, now, now); err != nil {
			return false, fmt.Errorf("set the time of %s: %w", written, err)
		}
	}
	return true, nil
}

// snapshotPlan snapshots the plan one hook call shows: a spec file a successful Write, Edit or
// MultiEdit wrote or a successful Bash command named, the plan of an ExitPlanMode call before the
// developer answers it, or the last message of a turn in the plan format. The plan text is read
// from the hook input and the file, never from the event, so it does not enter the event stream.
// It returns the paths below plans/ that changed.
func snapshotPlan(dir string, in map[string]any, root, cwd string, since *string, now time.Time, warn func(string, ...any)) ([]string, error) {
	name, tool := stringOf(in["hook_event_name"]), stringOf(in["tool_name"])
	input := toolInput(in)
	if name == preToolUse && tool == "ExitPlanMode" {
		changed, err := snapshotSessionPlan(dir, input["plan"], planSourcePlanMode, now, warn)
		return sessionPlanChanged(changed), err
	}
	if name == stop {
		message, ok := in["last_assistant_message"].(string)
		if !ok || !isPlanMessage(message) {
			return nil, nil
		}
		changed, err := snapshotSessionPlan(dir, message, planSourceMessage, now, warn)
		return sessionPlanChanged(changed), err
	}
	if name != postToolUse || !toolOK(in) {
		return nil, nil
	}
	type candidate struct {
		path  string
		since *string
	}
	var candidates []candidate
	switch {
	case writeTools[tool]:
		candidates = append(candidates, candidate{path: stringOf(writtenPath(in))})
	case tool == "Bash":
		for _, word := range commandPlanPaths(stringOf(input["command"])) {
			candidates = append(candidates, candidate{path: word, since: since})
		}
	}
	base := cwd
	if base == "" {
		base = root
	}
	var copied []string
	for _, c := range candidates {
		rel := planPath(c.path, root, cwd)
		if rel == "" || slices.Contains(copied, "files/"+rel) {
			continue
		}
		full := resolveIn(c.path, base)
		if c.since != nil {
			if info, err := os.Stat(full); err != nil || !info.Mode().IsRegular() {
				continue
			}
		}
		done, err := snapshotSpec(dir, full, rel, c.since, warn)
		if err != nil {
			warn("plan %s not captured: %v", full, err)
			continue
		}
		if done {
			copied = append(copied, "files/"+rel)
		}
	}
	return copied, nil
}

func sessionPlanChanged(changed bool) []string {
	if changed {
		return []string{"session.md"}
	}
	return nil
}

// refreshHandoffs snapshots again every file under <root>/handoffs/<feature>/ of the briefed
// features that changed since its snapshot, and copies one without a snapshot only when it was
// changed after since (the session's start), so the handoffs of other sessions in the same
// directory are left out. Only the named directories are walked, so a tool event stays cheap.
// It returns the paths below handoffs/ that were copied.
func refreshHandoffs(dir, root string, features []string, since string, warn func(string, ...any)) []string {
	handoffs := filepath.Join(root, "handoffs")
	var copied []string
	for _, feature := range features {
		if feature == "" || feature == "." || feature == ".." || strings.ContainsRune(feature, '/') {
			warn("briefed handoff directory %s refused: not a single directory name", pyRepr(feature))
			continue
		}
		base := filepath.Join(handoffs, feature)
		if info, err := os.Stat(base); err != nil || !info.IsDir() {
			continue
		}
		walkErr := filepath.WalkDir(base, func(source string, entry fs.DirEntry, err error) error {
			if err != nil {
				warn("cannot list %s: %v", source, err)
				return nil
			}
			if entry.IsDir() || strings.Contains(entry.Name(), ".tmp-") {
				return nil
			}
			rel, err := filepath.Rel(handoffs, source)
			if err != nil {
				warn("handoff %s not snapshotted: %v", source, err)
				return nil
			}
			rel = filepath.ToSlash(rel)
			done, err := refreshHandoff(source, filepath.Join(dir, "handoffs", filepath.FromSlash(rel)), since)
			if err != nil {
				warn("handoff %s not snapshotted: %v", source, err)
				return nil
			}
			if done {
				copied = append(copied, rel)
			}
			return nil
		})
		if walkErr != nil {
			warn("cannot list %s: %v", base, walkErr)
		}
	}
	return copied
}

func refreshHandoff(source, target, since string) (bool, error) {
	sourceInfo, err := os.Stat(source)
	if err != nil {
		return false, err
	}
	if sourceInfo.IsDir() {
		return false, nil
	}
	targetInfo, err := os.Stat(target)
	switch {
	case errors.Is(err, fs.ErrNotExist):
		if since != "" && isoOf(sourceInfo.ModTime()) < since {
			return false, nil
		}
	case err != nil:
		return false, err
	case targetInfo.ModTime().Equal(sourceInfo.ModTime()):
		return false, nil
	}
	return copySnapshot(source, target, 0)
}

// pyRepr quotes a name for a message the way the recorder's messages always quoted it.
func pyRepr(s string) string {
	quote := "'"
	if strings.Contains(s, "'") && !strings.Contains(s, `"`) {
		quote = `"`
	}
	quoted := strconv.Quote(s)
	inner := quoted[1 : len(quoted)-1]
	if quote == "'" {
		inner = strings.ReplaceAll(strings.ReplaceAll(inner, `\"`, `"`), "'", `\'`)
	} else {
		inner = strings.ReplaceAll(inner, `\"`, `"`)
	}
	return quote + inner + quote
}
