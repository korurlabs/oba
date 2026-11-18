package jsonout

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
)

// Flush writes the result of the run, indented, to the destination Start was given: the data
// of the last call, or with CollectWrites the data of every write. A run without an API call
// writes null. The values are written as the API sent them; only the white space changes.
func Flush() error {
	mu.Lock()
	defer mu.Unlock()
	if dest == nil {
		return nil
	}
	value, err := resultValue()
	if err != nil {
		return err
	}
	var buf bytes.Buffer
	if err := json.Indent(&buf, value, "", "  "); err != nil {
		return fmt.Errorf("failed to format the JSON output: %w", err)
	}
	buf.WriteByte('\n')
	if _, err := dest.Write(buf.Bytes()); err != nil {
		return fmt.Errorf("failed to write the JSON output: %w", err)
	}
	return nil
}

func resultValue() (json.RawMessage, error) {
	if writesOnly {
		writes := []json.RawMessage{}
		for _, r := range results {
			if r.method != http.MethodGet {
				writes = append(writes, r.data)
			}
		}
		return marshal(writes)
	}
	if len(results) == 0 {
		return nullData, nil
	}
	last := results[len(results)-1]
	if last.list {
		return marshal(last.items)
	}
	return last.data, nil
}

func marshal(items []json.RawMessage) (json.RawMessage, error) {
	value, err := json.Marshal(items)
	if err != nil {
		return nil, fmt.Errorf("failed to encode the JSON output: %w", err)
	}
	return value, nil
}
