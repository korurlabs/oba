package capture

import (
	"encoding/json"
	"regexp"
	"strconv"
	"strings"
	"unicode"
	"unicode/utf8"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/redact"
)

// Limits of the recorded text, in characters, applied after redaction. They bound what a session
// page shows of a prompt, a command or a message, and the queue holds prompts on disk.
const (
	PromptLimit  = 500
	CommandLimit = 300
	MessageLimit = 2000
	titleLimit   = 80
)

const isoSeconds = "2006-01-02T15:04:05Z"

// Event names and tools the capture treats specially.
const (
	sessionStart       = "SessionStart"
	sessionEnd         = "SessionEnd"
	stop               = "Stop"
	subagentStop       = "SubagentStop"
	userPromptSubmit   = "UserPromptSubmit"
	userPromptExpand   = "UserPromptExpansion"
	preToolUse         = "PreToolUse"
	postToolUse        = "PostToolUse"
	postToolUseFailure = "PostToolUseFailure"
)

// branchEvents carry the branch. Only they may run git: they are rare, and a tool event must
// stay fast.
var branchEvents = map[string]bool{sessionStart: true, stop: true, sessionEnd: true}

var writeTools = map[string]bool{"Edit": true, "Write": true, "MultiEdit": true}

func isAgentTool(tool string) bool { return tool == "Agent" || tool == "Task" }

func isPostEvent(name string) bool { return name == postToolUse || name == postToolUseFailure }

// pySpace is the whitespace of Python's str.isspace, which the recorder's text rules used and
// which Go's \s (ASCII only) does not cover.
const pySpace = `\t\n\x0b\f\r \x{1c}-\x{1f}\x{85}\x{a0}\x{1680}\x{2000}-\x{200a}\x{2028}\x{2029}\x{202f}\x{205f}\x{3000}`

// pyWord is Python's Unicode \w: letters, digits and the underscore.
const pyWord = `\p{L}\p{N}_`

var (
	handoffLine     = regexp.MustCompile("(?m)^[ \\t>*_-]*Handoff:\\**[ \\t]*`?([^" + pySpace + "`,]+)")
	handoffsSegment = regexp.MustCompile(`(?:^|/)handoffs/(.+)\n?$`)
	ticket          = regexp.MustCompile(`[A-Z][A-Z0-9]+-[0-9]+`)
	prCreate        = regexp.MustCompile(`(?:^|[;&|(][` + pySpace + `]*)gh[` + pySpace + `]+pr[` + pySpace + `]+create(?:[^` + pyWord + `]|$)`)
	githubURL       = regexp.MustCompile(`https://github\.com/[^` + pySpace + `"'<>)]+`)
)

// harnessPrefixes open the blocks Claude Code puts in front of what the user typed: the file or
// selection open in the IDE, reminders, subagent messages and notifications, slash command echoes.
// The second field says whether more name characters follow the prefix.
var harnessPrefixes = []struct {
	prefix string
	open   bool
	chars  string
}{
	{"ide_", true, "abcdefghijklmnopqrstuvwxyz_"},
	{"system-reminder", false, ""},
	{"agent-message", false, ""},
	{"task-notification", false, ""},
	{"command-", true, "abcdefghijklmnopqrstuvwxyz-"},
	{"local-command-", true, "abcdefghijklmnopqrstuvwxyz-"},
}

func isPySpace(r rune) bool {
	return unicode.IsSpace(r) || (r >= 0x1c && r <= 0x1f)
}

func isPyWord(r rune) bool {
	return r == '_' || unicode.IsLetter(r) || unicode.IsNumber(r)
}

func pyStrip(s string) string { return strings.TrimFunc(s, isPySpace) }

func pyFields(s string) []string { return strings.FieldsFunc(s, isPySpace) }

// cutRunes keeps the first limit characters of s.
func cutRunes(s string, limit int) string {
	if len(s) <= limit {
		return s
	}
	count := 0
	for i := range s {
		if count == limit {
			return s[:i]
		}
		count++
	}
	return s
}

// truthy is the truth value the hook input's optional fields were judged by: null, false, zero,
// an empty string, list or object count as absent.
func truthy(v any) bool {
	switch value := v.(type) {
	case nil:
		return false
	case bool:
		return value
	case string:
		return value != ""
	case json.Number:
		f, err := value.Float64()
		return err != nil || f != 0
	case float64:
		return value != 0
	case []any:
		return len(value) > 0
	case map[string]any:
		return len(value) > 0
	}
	return true
}

// firstTruthy is the first truthy value, else the last one, as a chain of `or` gives it.
func firstTruthy(values ...any) any {
	for _, v := range values {
		if truthy(v) {
			return v
		}
	}
	return values[len(values)-1]
}

func asString(v any) (string, bool) {
	s, ok := v.(string)
	return s, ok
}

// stringOf is v when it is a string, else "".
func stringOf(v any) string {
	s, _ := v.(string)
	return s
}

func asObject(v any) map[string]any {
	object, _ := v.(map[string]any)
	return object
}

// redactAny redacts a string and passes any other value through, so optional fields keep
// their shape.
func redactAny(v any) any {
	if s, ok := v.(string); ok {
		return redact.Text(s)
	}
	return v
}

// cutAny is a string cut to limit characters; any other value is absent.
func cutAny(v any, limit int) any {
	if s, ok := v.(string); ok {
		return cutRunes(s, limit)
	}
	return nil
}

// stripHarness removes the harness blocks in front of the user's own text. A prompt that starts
// with the user's text comes back unchanged; one that holds nothing else becomes "". A block
// that is never closed runs to the end, as when the prompt was cut inside it.
func stripHarness(prompt any) any {
	text, ok := prompt.(string)
	if !ok {
		return prompt
	}
	rest, stripped := text, false
	for {
		end, found := harnessBlock(rest)
		if !found {
			break
		}
		rest, stripped = rest[end:], true
	}
	if !stripped {
		return text
	}
	return pyStrip(rest)
}

// harnessBlock is the end of the harness block that opens s after optional whitespace.
func harnessBlock(s string) (int, bool) {
	i := len(s) - len(strings.TrimLeftFunc(s, isPySpace))
	if !strings.HasPrefix(s[i:], "<") {
		return 0, false
	}
	i++
	name := ""
	for _, p := range harnessPrefixes {
		if !strings.HasPrefix(s[i:], p.prefix) {
			continue
		}
		j := i + len(p.prefix)
		if p.open {
			k := j
			for k < len(s) && strings.IndexByte(p.chars, s[k]) >= 0 {
				k++
			}
			if k == j {
				continue
			}
			j = k
		}
		name = s[i:j]
		i = j
		break
	}
	if name == "" {
		return 0, false
	}
	r, size := utf8.DecodeRuneInString(s[i:])
	switch {
	case r == '>':
		i++
	case size > 0 && isPySpace(r):
		closing := strings.IndexByte(s[i:], '>')
		if closing < 0 {
			return 0, false
		}
		i += closing + 1
	default:
		return 0, false
	}
	closeTag := "</" + name + ">"
	if end := strings.Index(s[i:], closeTag); end >= 0 {
		return i + end + len(closeTag), true
	}
	return len(s), true
}

// handoffOf is the handoff file a brief names on its `Handoff:` line, as the path below
// handoffs/, or "".
func handoffOf(prompt string) string {
	for _, match := range handoffLine.FindAllStringSubmatch(prompt, -1) {
		path := strings.TrimRight(match[1], ".;:)")
		if below := handoffsSegment.FindStringSubmatch(path); below != nil {
			return below[1]
		}
	}
	return ""
}

// findTicket is the first ticket key in text that stands as a word of its own.
func findTicket(text string) string {
	for _, loc := range ticket.FindAllStringIndex(text, -1) {
		before, _ := utf8.DecodeLastRuneInString(text[:loc[0]])
		after, _ := utf8.DecodeRuneInString(text[loc[1]:])
		if (loc[0] == 0 || !isPyWord(before)) && (loc[1] == len(text) || !isPyWord(after)) {
			return text[loc[0]:loc[1]]
		}
	}
	return ""
}

// toolOK is false when the tool call failed or was interrupted. Only this flag is kept of the
// tool's output.
func toolOK(in map[string]any) bool {
	if in["hook_event_name"] == postToolUseFailure {
		return false
	}
	response, isObject := in["tool_response"].(map[string]any)
	if !isObject {
		return true
	}
	if response["interrupted"] == true || response["is_error"] == true {
		return false
	}
	if response["success"] == false || truthy(response["error"]) {
		return false
	}
	status := response["status"]
	return status != "failed" && status != "error"
}

func toolInput(in map[string]any) map[string]any {
	if object := asObject(in["tool_input"]); object != nil {
		return object
	}
	return map[string]any{}
}

// writtenPath is the file a Write, Edit or MultiEdit names, from its input or its response.
func writtenPath(in map[string]any) any {
	var fallback any
	if response := asObject(in["tool_response"]); response != nil {
		fallback = response["filePath"]
	}
	return firstTruthy(toolInput(in)["file_path"], fallback)
}

// eventFrom is the event recorded for one hook call: the fields Kaya reads, and nothing of the
// tool output beyond the ok flag, a pull request URL and the id of a started subagent. Prompts,
// command arguments, descriptions, commands and messages are redacted before they are cut.
func eventFrom(in map[string]any, at string) map[string]any {
	nameValue := firstTruthy(in["hook_event_name"], "")
	name := stringOf(nameValue)
	event := map[string]any{"at": at, "event": nameValue}
	for _, key := range []string{"agent_id", "agent_type"} {
		if truthy(in[key]) {
			event[key] = in[key]
		}
	}
	event["cwd"] = firstTruthy(in["cwd"], "")
	if truthy(in["session_title"]) {
		event["session_title"] = in["session_title"]
	}

	switch {
	case name == sessionStart:
		event["source"] = in["source"]
	case name == userPromptSubmit:
		if prompt := cutAny(redactAny(stripHarness(in["prompt"])), PromptLimit); truthy(prompt) {
			event["prompt"] = prompt
		}
	case name == userPromptExpand:
		event["command_name"] = in["command_name"]
		event["command_args"] = cutAny(redactAny(in["command_args"]), PromptLimit)
	case name == preToolUse || isPostEvent(name):
		toolValue := firstTruthy(in["tool_name"], "")
		tool := stringOf(toolValue)
		input := toolInput(in)
		event["tool_name"] = toolValue
		event["tool_use_id"] = in["tool_use_id"]
		switch {
		case isAgentTool(tool):
			event["subagent_type"] = input["subagent_type"]
			event["description"] = redactAny(input["description"])
			if handoff := handoffOf(stringOf(input["prompt"])); handoff != "" {
				event["handoff"] = handoff
			}
		case tool == "Skill":
			event["skill"] = firstTruthy(input["skill"], input["name"], input["command"])
		case tool == "Bash":
			event["command"] = cutRunes(redact.Text(stringOf(input["command"])), CommandLimit)
		case writeTools[tool]:
			event["file_path"] = writtenPath(in)
		}
		if isPostEvent(name) {
			event["ok"] = toolOK(in)
			response := asObject(in["tool_response"])
			if tool == "Bash" && response != nil && prCreate.MatchString(stringOf(input["command"])) {
				if url := githubURL.FindString(stringOf(response["stdout"])); url != "" {
					event["url"] = url
				}
			}
			if isAgentTool(tool) && response != nil {
				if id, ok := asString(response["agentId"]); ok {
					event["spawned_agent_id"] = id
				}
			}
		}
	case name == subagentStop || name == stop:
		event["last_assistant_message"] = cutAny(redactAny(in["last_assistant_message"]), MessageLimit)
	case name == sessionEnd:
		event["reason"] = in["reason"]
	}
	for key, value := range event {
		if value == nil {
			delete(event, key)
		}
	}
	return event
}

// sessionIDOf is the session id of the hook input; a number is taken as its digits.
func sessionIDOf(in map[string]any) string {
	switch value := in["session_id"].(type) {
	case string:
		return value
	case json.Number:
		return value.String()
	case float64:
		return strconv.FormatFloat(value, 'f', -1, 64)
	}
	return ""
}
