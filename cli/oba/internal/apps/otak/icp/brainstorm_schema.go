package icp

import (
	"bytes"
	"encoding/json"
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
)

// Reply is one brainstorm turn of Claude Code: what it says, the questions it asks and the ICPs
// it proposes. Proposals holds only those the API would accept; Skipped says why each other one
// was left out.
type Reply struct {
	Message   string
	Questions []string
	Proposals []map[string]json.RawMessage
	Skipped   []string
}

// BrainstormSchema is the JSON Schema of one brainstorm reply. Each proposal has the schema of
// the live request, so a change of the API's fields reaches the proposals with no CLI change.
func BrainstormSchema(fields []apidocs.Field) (string, error) {
	proposal, err := apidocs.Schema(fields)
	if err != nil {
		return "", err
	}
	schema := map[string]any{
		"type":                 "object",
		"additionalProperties": false,
		"required":             []string{"message", "questions", "proposals"},
		"properties": map[string]any{
			"message":   map[string]any{"type": "string"},
			"questions": map[string]any{"type": "array", "items": map[string]any{"type": "string"}},
			"proposals": map[string]any{"type": "array", "items": json.RawMessage(proposal)},
		},
	}
	out, err := json.Marshal(schema)
	if err != nil {
		return "", fmt.Errorf("failed to build the brainstorm JSON schema: %w", err)
	}
	return string(out), nil
}

// DecodeReply reads a brainstorm reply of Claude Code. A proposal with a key the API does not
// know or without a required field is skipped with its reason, so that every proposal shown
// can be created as it is.
func DecodeReply(raw json.RawMessage, fields []apidocs.Field) (Reply, error) {
	trimmed := bytes.TrimSpace(raw)
	if len(trimmed) == 0 || trimmed[0] != '{' {
		return Reply{}, fmt.Errorf("the output of Claude Code is not one JSON object: %s", preview(trimmed))
	}
	var decoded struct {
		Message   string            `json:"message"`
		Questions []string          `json:"questions"`
		Proposals []json.RawMessage `json:"proposals"`
	}
	if err := json.Unmarshal(trimmed, &decoded); err != nil {
		return Reply{}, fmt.Errorf("the output of Claude Code is not a brainstorm reply: %w", err)
	}
	reply := Reply{Message: strings.TrimSpace(decoded.Message)}
	for _, question := range decoded.Questions {
		if question = strings.TrimSpace(question); question != "" {
			reply.Questions = append(reply.Questions, question)
		}
	}
	for _, raw := range decoded.Proposals {
		proposal, err := ParseDraft(raw, fields)
		if err == nil {
			err = checkRequired(proposal, fields)
		}
		if err != nil {
			reply.Skipped = append(reply.Skipped, err.Error())
			continue
		}
		reply.Proposals = append(reply.Proposals, proposal)
	}
	return reply, nil
}

// checkRequired refuses a proposal that leaves out a field the API requires or sets it to null.
func checkRequired(proposal map[string]json.RawMessage, fields []apidocs.Field) error {
	for _, field := range fields {
		if !field.Required {
			continue
		}
		if value, ok := proposal[field.Name]; !ok || string(bytes.TrimSpace(value)) == "null" {
			return fmt.Errorf("the proposal of Claude Code lacks the required field %q", field.Name)
		}
	}
	return nil
}
