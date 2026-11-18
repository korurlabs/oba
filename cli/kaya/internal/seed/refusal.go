package seed

import (
	"encoding/json"
	"errors"
	"fmt"
	"sort"
	"strings"

	"github.com/korurlabs/kaya-cli/internal/client"
)

// Refusal is Kaya's refusal of a seed run together with the reason it gives per field. The code
// and message alone say only that a field is invalid; the reason names the set, the path, the
// folder or the --pack flag the user has to change.
type Refusal struct {
	Err *client.APIError
}

func (r *Refusal) Error() string {
	var text strings.Builder
	text.WriteString("Kaya refused the seed run")
	if r.Err.Code != "" {
		_, _ = fmt.Fprintf(&text, " (%d %s)", r.Err.Status, r.Err.Code)
	} else {
		_, _ = fmt.Fprintf(&text, " (%d)", r.Err.Status)
	}
	text.WriteString(": " + r.Err.Message)
	fields := make([]string, 0, len(r.Err.ErrorValues))
	for field := range r.Err.ErrorValues {
		fields = append(fields, field)
	}
	sort.Strings(fields)
	for _, field := range fields {
		text.WriteString("\n  " + field + ": " + valueText(r.Err.ErrorValues[field]))
	}
	return text.String()
}

func (r *Refusal) Unwrap() error {
	return r.Err
}

// explainRefusal adds the field reasons of a refusal to its error; any other error, and a refusal
// without reasons, is returned as it is.
func explainRefusal(err error) error {
	var apiErr *client.APIError
	if !errors.As(err, &apiErr) || len(apiErr.ErrorValues) == 0 {
		return err
	}
	return &Refusal{Err: apiErr}
}

// valueText prints a reason Kaya sends as text unquoted, and any other value as JSON.
func valueText(value any) string {
	if text, ok := value.(string); ok {
		return text
	}
	raw, err := json.Marshal(value)
	if err != nil {
		return fmt.Sprint(value)
	}
	return string(raw)
}
