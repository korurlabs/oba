package terminal

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
)

// MaxStdinBytes bounds the JSON a command reads; one relation is a few kilobytes.
const MaxStdinBytes = 1024 * 1024

// ReadStdinObject reads one JSON object from r. Its values stay json.RawMessage, so that they
// reach the API exactly as given: a number such as 25000.10 is never re-encoded.
func ReadStdinObject(r io.Reader) (map[string]json.RawMessage, error) {
	data, err := ReadStdin(r)
	if err != nil {
		return nil, err
	}
	return ParseObject(data)
}

// ReadStdin reads all of r, refusing more than MaxStdinBytes.
func ReadStdin(r io.Reader) ([]byte, error) {
	data, err := io.ReadAll(io.LimitReader(r, MaxStdinBytes+1))
	if err != nil {
		return nil, fmt.Errorf("failed to read stdin: %w", err)
	}
	if len(data) > MaxStdinBytes {
		return nil, fmt.Errorf("stdin holds more than %d bytes; pass one relation as one JSON object", MaxStdinBytes)
	}
	return data, nil
}

// ParseObject checks that data is exactly one JSON object and returns its members.
func ParseObject(data []byte) (map[string]json.RawMessage, error) {
	trimmed := bytes.TrimSpace(data)
	if len(trimmed) == 0 {
		return nil, errors.New("no JSON on stdin; pass one JSON object (see --help for the input forms)")
	}
	if !json.Valid(trimmed) {
		var probe any
		err := json.Unmarshal(trimmed, &probe)
		return nil, fmt.Errorf("stdin is not valid JSON: %w", err)
	}
	if trimmed[0] != '{' {
		return nil, fmt.Errorf("stdin must hold one JSON object, not %s", jsonKind(trimmed[0]))
	}
	var obj map[string]json.RawMessage
	if err := json.Unmarshal(trimmed, &obj); err != nil {
		return nil, fmt.Errorf("stdin is not a JSON object: %w", err)
	}
	return obj, nil
}

func jsonKind(first byte) string {
	switch first {
	case '[':
		return "an array"
	case '"':
		return "a string"
	case 't', 'f':
		return "a boolean"
	case 'n':
		return "null"
	default:
		return "a number"
	}
}
