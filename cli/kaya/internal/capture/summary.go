package capture

import (
	"bufio"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"slices"
	"strings"
)

// Summary is the session's summary, kept up to date event by event in summary.json and sent in
// every batch. Its keys are the ones the ingest contract reads.
type Summary struct {
	SessionID       string   `json:"session_id"`
	Title           *string  `json:"title"`
	SessionTitle    *string  `json:"session_title"`
	FirstPrompt     *string  `json:"first_prompt"`
	StartedAt       *string  `json:"started_at"`
	LastEventAt     *string  `json:"last_event_at"`
	Ended           bool     `json:"ended"`
	Cwd             *string  `json:"cwd"`
	Branch          *string  `json:"branch"`
	Ticket          *string  `json:"ticket"`
	Features        []string `json:"features"`
	BriefedFeatures []string `json:"briefed_features"`
	EventCount      int      `json:"event_count"`
}

func ptr(s string) *string { return &s }

// fold is the summary of a session after one more event; nil starts a new session.
func fold(summary *Summary, event map[string]any, sessionID string) *Summary {
	at := stringOf(event["at"])
	if summary == nil {
		summary = &Summary{SessionID: sessionID, StartedAt: ptr(at), Features: []string{}, BriefedFeatures: []string{}}
		if cwd := stringOf(event["cwd"]); cwd != "" {
			summary.Cwd = ptr(cwd)
		}
	}
	if summary.Features == nil {
		summary.Features = []string{}
	}
	if summary.BriefedFeatures == nil {
		summary.BriefedFeatures = []string{}
	}
	name := stringOf(event["event"])
	summary.LastEventAt = ptr(at)
	summary.EventCount++
	summary.Ended = name == sessionEnd
	if title := stringOf(event["session_title"]); title != "" {
		summary.SessionTitle = ptr(title)
	}
	if branch := stringOf(event["branch"]); branch != "" {
		summary.Branch = ptr(branch)
	}
	if cwd := stringOf(event["cwd"]); (summary.Cwd == nil || *summary.Cwd == "") && cwd != "" {
		summary.Cwd = ptr(cwd)
	}
	if name == userPromptSubmit && summary.FirstPrompt == nil {
		if prompt := stringOf(stripHarness(event["prompt"])); prompt != "" {
			summary.FirstPrompt = ptr(prompt)
		}
	}

	var texts []string
	switch name {
	case userPromptSubmit:
		texts = append(texts, stringOf(event["prompt"]))
	case userPromptExpand:
		texts = append(texts, stringOf(event["command_args"]))
	}
	command := stringOf(event["command"])
	if words := pyFields(command); len(words) > 0 && words[0] == "kaizen" {
		texts = append(texts, command)
	}
	if summary.Ticket == nil {
		for _, text := range texts {
			if found := findTicket(text); found != "" {
				summary.Ticket = ptr(found)
				break
			}
		}
	}

	tool := stringOf(event["tool_name"])
	if path := stringOf(event["file_path"]); writeTools[tool] && path != "" {
		if below := handoffsSegment.FindStringSubmatch(filepath.ToSlash(path)); below != nil && strings.Contains(below[1], "/") {
			summary.Features = addFeature(summary.Features, below[1])
		}
	}
	if briefed := stringOf(event["handoff"]); isAgentTool(tool) && strings.Contains(briefed, "/") {
		summary.BriefedFeatures = addFeature(summary.BriefedFeatures, briefed)
	}
	summary.Title = title(summary)
	return summary
}

// addFeature adds the handoff directory of a path below handoffs/ once.
func addFeature(features []string, rel string) []string {
	feature, _, _ := strings.Cut(rel, "/")
	if slices.Contains(features, feature) {
		return features
	}
	return append(features, feature)
}

// addFeatures adds the handoff directory of each copied path below handoffs/.
func addFeatures(summary *Summary, paths []string) {
	for _, rel := range paths {
		if strings.Contains(rel, "/") {
			summary.Features = addFeature(summary.Features, rel)
		}
	}
}

// title is the session's own title, else its first prompt on one line, cut at a word to at most
// titleLimit characters.
func title(summary *Summary) *string {
	if summary.SessionTitle != nil && *summary.SessionTitle != "" {
		return summary.SessionTitle
	}
	prompt := ""
	if summary.FirstPrompt != nil {
		prompt = strings.Join(pyFields(*summary.FirstPrompt), " ")
	}
	runes := []rune(prompt)
	if len(runes) <= titleLimit {
		if prompt == "" {
			return nil
		}
		return ptr(prompt)
	}
	cut := string(runes[:titleLimit+1])
	if space := strings.LastIndex(cut, " "); space > 0 {
		return ptr(strings.TrimRightFunc(cut[:space], isPySpace))
	}
	return ptr(strings.TrimRightFunc(string(runes[:titleLimit]), isPySpace))
}

// loadSummary is the session's summary before the next event: summary.json, else the events
// folded again when summary.json is missing or damaged, else nil for a new session.
func loadSummary(dir, sessionID string, warn func(string, ...any)) (*Summary, error) {
	path := filepath.Join(dir, SummaryFile)
	var summary Summary
	found, err := readJSON(path, &summary)
	if found && err == nil {
		return &summary, nil
	}
	if err != nil {
		warn("%s is unreadable, folding the events again: %v", path, err)
	}
	return summarizeEvents(dir, sessionID, warn)
}

// summarizeEvents folds every event of events.jsonl into a summary; nil when it holds none.
func summarizeEvents(dir, sessionID string, warn func(string, ...any)) (*Summary, error) {
	path := filepath.Join(dir, EventsFile)
	file, err := os.Open(path) // #nosec G304 -- fixed name in the session's queue directory
	if errors.Is(err, os.ErrNotExist) {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("open %s: %w", path, err)
	}
	defer func() { _ = file.Close() }()
	var summary *Summary
	reader := bufio.NewReader(file)
	for number := 1; ; number++ {
		line, readErr := reader.ReadBytes('\n')
		if len(strings.TrimSpace(string(line))) > 0 {
			var event map[string]any
			if err := decodeNumbers(line, &event); err != nil || event == nil {
				warn("%s line %d is not a JSON object, skipped: %v", path, number, err)
			} else {
				summary = fold(summary, event, sessionID)
			}
		}
		if errors.Is(readErr, io.EOF) {
			break
		}
		if readErr != nil {
			return nil, fmt.Errorf("read %s: %w", path, readErr)
		}
	}
	if summary == nil {
		return nil, nil
	}
	// Handoffs copied by a rescan leave no trace in the events, only in the snapshots.
	entries, err := os.ReadDir(filepath.Join(dir, "handoffs"))
	if err != nil && !errors.Is(err, os.ErrNotExist) {
		return nil, fmt.Errorf("list %s: %w", filepath.Join(dir, "handoffs"), err)
	}
	for _, entry := range entries {
		if entry.IsDir() {
			addFeatures(summary, []string{entry.Name() + "/"})
		}
	}
	return summary, nil
}
