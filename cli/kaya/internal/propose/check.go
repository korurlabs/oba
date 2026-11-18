package propose

import (
	"encoding/json"
	"fmt"
	"regexp"
	"sort"
	"strings"
	"unicode/utf16"
	"unicode/utf8"

	"gopkg.in/yaml.v3"

	"github.com/korurlabs/kaya-cli/internal/client"
	"github.com/korurlabs/kaya-cli/internal/delivery"
)

// Limits of POST /kaya/delivery/proposals and of one config file, as Kaya enforces them.
const (
	maxFiles          = 200
	maxFileBytes      = 5 << 20
	MaxBodyBytes      = 8 << 20
	minReason         = 10
	maxReason         = 1000
	maxSkillLines     = 1500
	maxSkillListing   = 1536
	frontmatterMarker = "---"
)

var skillFile = regexp.MustCompile(`^\.(agents|claude)/skills/[^/]+/SKILL\.md$`)

// Refusal is a proposal refused, locally or by Kaya: a summary, one line per problem, and what
// to do next.
type Refusal struct {
	Summary string
	Lines   []string
	Hint    string
	// Err is Kaya's answer when Kaya refused.
	Err error
}

func (r *Refusal) Error() string {
	text := r.Summary
	for _, line := range r.Lines {
		text += "\n  " + line
	}
	if r.Hint != "" {
		text += "\n" + r.Hint
	}
	return text
}

func (r *Refusal) Unwrap() error {
	return r.Err
}

// CheckReason refuses a reason Kaya would refuse: 10 to 1000 characters, counted as Java counts
// them.
func CheckReason(reason string) error {
	length := len(utf16.Encode([]rune(reason)))
	if length < minReason || length > maxReason {
		return fmt.Errorf("the reason has %d characters; Kaya needs 10 to 1000, so that the reviewer knows why the change is needed", length)
	}
	return nil
}

// Check refuses locally what Kaya would refuse: more than 200 files; the tree the release would
// become breaking the path, link and case rules every delivery checks (the allow-list, a link at
// a SKILL.md, a hook or the settings, a link leaving the repository or ending at nothing, a path
// below a link, two paths that differ only in letter case); settings that are not a JSON object;
// and a size limit (5 MiB, a SKILL.md under 1500 lines, description plus when_to_use at most 1536
// characters) broken by a new file or by a file that grows on the measure. A file that already
// broke a limit in the release may be edited as long as it does not grow, as Kaya waives it.
func Check(src Source, changes []Change) error {
	var problems []string
	if len(changes) > maxFiles {
		problems = append(problems, fmt.Sprintf("%d files: Kaya takes at most %d in one proposal", len(changes), maxFiles))
	}
	problems = append(problems, delivery.ManifestProblems(composed(src.Release, changes))...)
	for _, change := range changes {
		problems = append(problems, fileProblems(src, change)...)
	}
	if len(problems) == 0 {
		return nil
	}
	sort.Strings(problems)
	return &Refusal{
		Summary: fmt.Sprintf("Kaya would refuse this proposal on release %d:", src.Release.Number),
		Lines:   problems,
		Hint:    "Fix these paths and run kaya propose again.",
	}
}

// composed is the manifest the release becomes with the changes applied.
func composed(release delivery.Manifest, changes []Change) delivery.Manifest {
	byPath := map[string]Change{}
	for _, change := range changes {
		byPath[change.Path] = change
	}
	next := delivery.Manifest{Organization: release.Organization, RemoteURL: release.RemoteURL, Number: release.Number}
	for _, file := range release.Files {
		change, ok := byPath[file.Path]
		switch {
		case !ok:
			next.Files = append(next.Files, file)
		case change.Kind != Deleted:
			next.Files = append(next.Files, asManifestFile(change, file.AlwaysOn))
		}
	}
	for _, change := range changes {
		if change.Kind == Added {
			next.Files = append(next.Files, asManifestFile(change, false))
		}
	}
	return next
}

func asManifestFile(change Change, alwaysOn bool) delivery.ManifestFile {
	file := delivery.ManifestFile{Path: change.Path, Mode: change.Mode, AlwaysOn: alwaysOn}
	if change.Mode == client.ModeSymlink {
		file.Target = change.Target
		return file
	}
	// A file too large to read still needs a hash for the path rules; its size is refused apart.
	file.SHA256 = strings.Repeat("0", 64)
	if change.Content != nil {
		file.SHA256 = hashOf(change.Content)
	}
	return file
}

// fileProblems are the rules one changed file breaks on its own.
func fileProblems(src Source, change Change) []string {
	if change.Kind == Deleted || change.Mode == client.ModeSymlink {
		return nil
	}
	var base []byte
	if change.Base != nil && change.Base.Mode != client.ModeSymlink {
		// rule-10-no-op -- reason: without the release's copy the file is checked as a new one, which only refuses more; Kaya applies the waiver itself
		if content, err := src.Cache.Get(change.Base.SHA256); err == nil {
			base = content
		}
	}
	var problems []string
	if change.TooLarge() || change.Size > maxFileBytes {
		if base == nil || change.Size > int64(len(base)) {
			problems = append(problems, fmt.Sprintf("%s: has %d bytes; a config file holds at most %d", change.Path, change.Size, maxFileBytes))
		}
		return problems
	}
	if change.Path == delivery.SettingsPath {
		var settings map[string]any
		if err := json.Unmarshal(change.Content, &settings); err != nil || settings == nil {
			problems = append(problems, change.Path+": is not a JSON object")
		}
	}
	if !skillFile.MatchString(change.Path) {
		return problems
	}
	text := string(change.Content)
	if lines := lineCount(text); lines >= maxSkillLines && (base == nil || lines > lineCount(string(base))) {
		problems = append(problems, fmt.Sprintf("%s: has %d lines; a SKILL.md must stay under %d", change.Path, lines, maxSkillLines))
	}
	if listing, ok := listingLength(text); ok && listing > maxSkillListing {
		baseListing, baseOK := listingLength(string(base))
		if base == nil || !baseOK || listing > baseListing {
			problems = append(problems, fmt.Sprintf("%s: description plus when_to_use have %d characters; at most %d", change.Path, listing, maxSkillListing))
		}
	}
	return problems
}

// lineCount counts lines as an editor does: a final newline starts no other line.
func lineCount(text string) int {
	if text == "" {
		return 0
	}
	lines := strings.Count(text, "\n")
	if !strings.HasSuffix(text, "\n") {
		lines++
	}
	return lines
}

// listingLength is the characters of description plus when_to_use in a Markdown file's
// frontmatter. ok is false when the file has no frontmatter Kaya would read the same way; Kaya
// then decides alone.
func listingLength(text string) (int, bool) {
	text = strings.TrimPrefix(text, "\uFEFF")
	lines := strings.Split(strings.ReplaceAll(text, "\r\n", "\n"), "\n")
	if len(lines) == 0 || strings.TrimSpace(lines[0]) != frontmatterMarker {
		return 0, true
	}
	for i := 1; i < len(lines); i++ {
		if strings.TrimSpace(lines[i]) != frontmatterMarker {
			continue
		}
		var values map[string]any
		if err := yaml.Unmarshal([]byte(strings.Join(lines[1:i], "\n")), &values); err != nil {
			return 0, false
		}
		return scalarLength(values["description"]) + scalarLength(values["when_to_use"]), true
	}
	return 0, false
}

func scalarLength(value any) int {
	switch value.(type) {
	case nil, []any, map[string]any:
		return 0
	}
	return utf8.RuneCountInString(fmt.Sprint(value))
}
