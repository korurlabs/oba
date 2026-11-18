package icp

import (
	"bytes"
	"encoding/json"
	"fmt"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
)

// ParseDraft reads the draft Claude Code returned and refuses a key the API does not know, so
// that the draft shown is exactly what would be sent.
func ParseDraft(raw json.RawMessage, fields []apidocs.Field) (map[string]json.RawMessage, error) {
	trimmed := bytes.TrimSpace(raw)
	if len(trimmed) == 0 || trimmed[0] != '{' {
		return nil, fmt.Errorf("the output of Claude Code is not one JSON object: %s", preview(trimmed))
	}
	var draft map[string]json.RawMessage
	if err := json.Unmarshal(trimmed, &draft); err != nil {
		return nil, fmt.Errorf("the output of Claude Code is not one JSON object: %w", err)
	}
	if err := apidocs.CheckKeys(draft, fields); err != nil {
		return nil, fmt.Errorf("the draft of Claude Code has a field the API does not accept: %w", err)
	}
	return draft, nil
}
