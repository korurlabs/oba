package icp

import (
	"encoding/json"
	"fmt"
	"io"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
)

// printProfile writes title, then the profile as PrintDraft describes.
func printProfile(out io.Writer, title string, draft map[string]json.RawMessage, fields []apidocs.Field) {
	width := 0
	for _, field := range fields {
		width = max(width, len(field.Name))
	}
	_, _ = fmt.Fprintln(out, title)
	for _, field := range fields {
		value := displayValue(draft[field.Name])
		lines := strings.Split(value, "\n")
		_, _ = fmt.Fprintf(out, "  %-*s  %s\n", width+1, field.Name+":", lines[0])
		for _, line := range lines[1:] {
			_, _ = fmt.Fprintf(out, "  %-*s  %s\n", width+1, "", line)
		}
	}
}

func displayValue(raw json.RawMessage) string {
	if len(raw) == 0 || string(raw) == "null" {
		return "-"
	}
	var s string
	if json.Unmarshal(raw, &s) == nil {
		return s
	}
	return string(raw)
}

// preview is the start of an output, for an error message.
func preview(data []byte) string {
	const limit = 80
	text := []rune(string(data))
	if len(text) > limit {
		return string(text[:limit]) + "..."
	}
	if len(text) == 0 {
		return "(empty)"
	}
	return string(text)
}
