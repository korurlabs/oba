package capture

import (
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
)

// transcriptTail is how much of a transcript's end is read: the newest records carry the facts,
// and a long session must not cost more than a short one.
const transcriptTail = 262144

// transcriptFacts are the model and the Claude Code version of the newest transcript records
// that carry them; "" for each one the transcript does not hold. A transcript that cannot be
// read gives no facts: it is Claude Code's file, and an event is recorded without them.
func transcriptFacts(path string) (model, version string) {
	if path == "" {
		return "", ""
	}
	file, err := os.Open(path) // #nosec G304 -- the transcript Claude Code names in the hook input
	if err != nil {
		return "", ""
	}
	defer func() { _ = file.Close() }()
	info, err := file.Stat()
	if err != nil {
		return "", ""
	}
	start := max(info.Size()-transcriptTail, 0)
	data := make([]byte, info.Size()-start)
	if _, err := file.ReadAt(data, start); err != nil {
		return "", ""
	}
	lines := splitLines(data)
	versionFound := false
	for i := len(lines) - 1; i >= 0; i-- {
		var item map[string]any
		if err := json.Unmarshal(lines[i], &item); err != nil || item == nil {
			continue
		}
		if s, ok := item["version"].(string); ok && !versionFound {
			version, versionFound = s, true
		}
		if message := asObject(item["message"]); model == "" && item["type"] == "assistant" && message != nil {
			if candidate := stringOf(message["model"]); candidate != "" && !strings.HasPrefix(candidate, "<") {
				model = candidate
			}
		}
		if model != "" && version != "" {
			break
		}
	}
	return model, version
}

// splitLines splits at \n, \r\n and \r, as the records of a transcript may end.
func splitLines(data []byte) [][]byte {
	data = bytes.ReplaceAll(data, []byte("\r\n"), []byte("\n"))
	return bytes.FieldsFunc(data, func(r rune) bool { return r == '\n' || r == '\r' })
}

// subagentTranscript is the transcript of the subagent a hook input belongs to.
func subagentTranscript(in map[string]any) string {
	if path := stringOf(in["agent_transcript_path"]); path != "" {
		return path
	}
	transcript, agentID := stringOf(in["transcript_path"]), stringOf(in["agent_id"])
	if strings.HasSuffix(transcript, ".jsonl") && agentID != "" {
		return filepath.Join(strings.TrimSuffix(transcript, ".jsonl"), "subagents", "agent-"+agentID+".jsonl")
	}
	return ""
}

// eventModel is the model an event names: the session's on SessionStart, the one an Agent call
// asks for, and a subagent's own, read from its transcript when it stops.
func eventModel(in map[string]any) string {
	switch stringOf(in["hook_event_name"]) {
	case sessionStart:
		return stringOf(in["model"])
	case preToolUse:
		if isAgentTool(stringOf(in["tool_name"])) {
			return stringOf(toolInput(in)["model"])
		}
	case subagentStop:
		model, _ := transcriptFacts(subagentTranscript(in))
		return model
	}
	return ""
}
