package delivery

import (
	"errors"
	"fmt"
	"regexp"
	"strings"

	"golang.org/x/text/unicode/norm"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/seed"
)

// maxLinkHops is the server's limit of links followed in one chain; a longer chain is a loop.
const maxLinkHops = 8

var (
	sha256Hex = regexp.MustCompile(`^[0-9a-f]{64}$`)
	skillFile = regexp.MustCompile(`^\.(agents|claude)/skills/[^/]+/SKILL\.md$`)
)

// maxDenyRules is the server's limit of deny rules in one guardrail.
const maxDenyRules = 20

// guardrailPrefix is the folder of guardrail files, the only files that may carry deny rules.
const guardrailPrefix = ".agents/rules/guardrails/"

// ValidateManifest applies the server's file and composition rules to a verified manifest
// before anything is written, so that even a validly signed release cannot make the CLI write
// outside the delivered paths: every path on the allow-list and spelled once (no two paths that
// differ only in letter case or Unicode normalization, as macOS file systems would merge them),
// no path below a delivered link, no link at a SKILL.md, under .agents/hooks/ or at
// .claude/settings.json, and every link, followed through the other links as the file system
// follows them, ending inside the repository at a delivered file or at a delivered directory
// this release puts files in, as .claude/rules -> ../.agents/rules does.
func ValidateManifest(m Manifest) error {
	if problems := ManifestProblems(m); len(problems) > 0 {
		return errors.New("the release breaks the delivery rules:\n  " + strings.Join(problems, "\n  "))
	}
	return nil
}

// ManifestProblems lists every rule of ValidateManifest that m breaks, one "path: problem" line
// each. A proposal checks the tree it would produce with it, so that a change the server would
// refuse is refused before it is sent.
func ManifestProblems(m Manifest) []string {
	var problems []string
	links := map[string]string{}
	seen := map[string]bool{}
	var paths []string
	for _, file := range m.Files {
		if seen[file.Path] {
			problems = append(problems, file.Path+": listed twice")
			continue
		}
		seen[file.Path] = true
		paths = append(paths, file.Path)
		if problem := fileProblem(file); problem != "" {
			problems = append(problems, file.Path+": "+problem)
			continue
		}
		if file.Mode == ModeSymlink {
			links[file.Path] = file.Target
		}
	}
	for path, earlier := range caseConflicts(paths) {
		problems = append(problems, fmt.Sprintf("%s: differs from %s only in letter case", path, earlier))
	}
	delivered := m.ByPath()
	for _, file := range m.Files {
		if link := linkAbove(file.Path, links); link != "" {
			problems = append(problems, fmt.Sprintf("%s: lies below the delivered link %s", file.Path, link))
		}
		target, isLink := links[file.Path]
		if !isLink {
			continue
		}
		resolved, problem := resolveLink(file.Path, target, links)
		switch {
		case problem != "":
			problems = append(problems, file.Path+": "+problem)
		case !seed.IsAllowedPath(resolved) && !seed.IsAllowedDir(resolved):
			problems = append(problems, fmt.Sprintf("%s: points at %s, which Kaya does not deliver", file.Path, resolved))
		case file.AlwaysOn && !isDeliveredFile(resolved, delivered):
			problems = append(problems, fmt.Sprintf("%s: an always-on link must point at a delivered file, not %s", file.Path, resolved))
		case !isDeliveredFile(resolved, delivered) && !hasDeliveredBelow(resolved, m.Files):
			problems = append(problems, fmt.Sprintf("%s: points at %s, which this release does not deliver", file.Path, resolved))
		}
	}
	return problems
}

func fileProblem(file ManifestFile) string {
	if !seed.IsAllowedPath(file.Path) {
		return "not a path Kaya delivers"
	}
	switch file.Mode {
	case ModeFile, ModeExecutable:
		if !sha256Hex.MatchString(file.SHA256) || file.Target != "" {
			return "a file needs a lower-case sha256 and no link target"
		}
	case ModeSymlink:
		switch {
		case file.SHA256 != "":
			return "a link has no content"
		case skillFile.MatchString(file.Path):
			return "a SKILL.md is never a link"
		case strings.HasPrefix(file.Path, ".agents/hooks/"):
			return "a hook is never a link"
		case file.Path == SettingsPath:
			return "settings are never a link"
		case file.Target == "" || strings.TrimSpace(file.Target) == "" || strings.HasPrefix(file.Target, "/") ||
			strings.Contains(file.Target, `\`) || len(file.Target) > 500:
			return "the link target must be a relative path"
		}
	default:
		return fmt.Sprintf("unknown mode %q", file.Mode)
	}
	return denyProblem(file)
}

// denyProblem refuses deny rules anywhere but on a guardrail file, as the server sets them only
// there, and an empty rule or more than the server's limit, so that a release cannot use the
// settings merge to switch tools off beyond what a guardrail may.
func denyProblem(file ManifestFile) string {
	if len(file.Deny) == 0 {
		return ""
	}
	if file.Mode == ModeSymlink || !strings.HasPrefix(file.Path, guardrailPrefix) || !strings.HasSuffix(file.Path, ".md") {
		return "only a guardrail file under " + guardrailPrefix + " carries deny rules"
	}
	if len(file.Deny) > maxDenyRules {
		return fmt.Sprintf("holds %d deny rules, more than %d", len(file.Deny), maxDenyRules)
	}
	for _, rule := range file.Deny {
		if strings.TrimSpace(rule) == "" {
			return "holds an empty deny rule"
		}
	}
	return ""
}

// fold is how macOS compares names: Unicode NFC, then lower case.
func fold(path string) string {
	return strings.ToLower(norm.NFC.String(path))
}

// caseConflicts maps each path whose own spelling or one of whose directories folds to an
// earlier, different spelling, to that earlier spelling.
func caseConflicts(paths []string) map[string]string {
	spellings := map[string]string{}
	conflicts := map[string]string{}
	for _, path := range paths {
		segments := strings.Split(path, "/")
		for length := 1; length <= len(segments); length++ {
			prefix := strings.Join(segments[:length], "/")
			key := fold(prefix)
			earlier, ok := spellings[key]
			if !ok {
				spellings[key] = prefix
				continue
			}
			if earlier != prefix {
				conflicts[path] = earlier
				break
			}
		}
	}
	return conflicts
}

// linkAbove is the delivered link a directory of path is, in any spelling, or "".
func linkAbove(path string, links map[string]string) string {
	folded := map[string]string{}
	for link := range links {
		folded[fold(link)] = link
	}
	segments := strings.Split(path, "/")
	for length := 1; length < len(segments); length++ {
		if link, ok := folded[fold(strings.Join(segments[:length], "/"))]; ok {
			return link
		}
	}
	return ""
}

// resolveLink follows a link segment by segment, replacing a segment that names a delivered
// link by its target, as the file system does. It answers the repository path the chain ends at,
// or why it ends nowhere.
func resolveLink(linkPath, target string, links map[string]string) (string, string) {
	folded := map[string]string{}
	for link, linkTarget := range links {
		folded[fold(link)] = linkTarget
	}
	segments := strings.Split(linkPath, "/")
	resolved := append([]string{}, segments[:len(segments)-1]...)
	pending := strings.Split(target, "/")
	hops := 0
	for len(pending) > 0 {
		segment := pending[0]
		pending = pending[1:]
		switch segment {
		case "", ".":
			continue
		case "..":
			if len(resolved) == 0 {
				return "", "the link leaves the repository"
			}
			resolved = resolved[:len(resolved)-1]
			continue
		}
		resolved = append(resolved, segment)
		next, isLink := folded[fold(strings.Join(resolved, "/"))]
		if !isLink {
			continue
		}
		hops++
		if hops > maxLinkHops {
			return "", "the link chain loops"
		}
		if strings.HasPrefix(next, "/") || strings.Contains(next, `\`) {
			return "", "the link leaves the repository"
		}
		resolved = resolved[:len(resolved)-1]
		pending = append(strings.Split(next, "/"), pending...)
	}
	if len(resolved) == 0 {
		return "", "the link points at the repository root"
	}
	return strings.Join(resolved, "/"), ""
}

func isDeliveredFile(path string, delivered map[string]ManifestFile) bool {
	file, ok := delivered[path]
	return ok && file.Mode != ModeSymlink
}

func hasDeliveredBelow(dir string, files []ManifestFile) bool {
	prefix := dir + "/"
	for _, file := range files {
		if strings.HasPrefix(file.Path, prefix) {
			return true
		}
	}
	return false
}
