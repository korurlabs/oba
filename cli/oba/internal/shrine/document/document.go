// Package document lists, creates, downloads, uploads and attaches the documents of the current
// organization: HTML files with their PDF, each owned by an Otak relation and written in the name
// of one legal entity. A document starts from a document template or a local .html file; its
// PDF is rendered locally on every upload, and a mail gets a copy of that PDF that later uploads
// leave as it is.
package document

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// ParseID checks a document id argument before any request: documents are named by their id
// only.
func ParseID(arg string) (string, error) {
	id := strings.TrimSpace(arg)
	if !userinput.IsID(id) {
		return "", fmt.Errorf("invalid document ID %q: use the ID that 'oba document list' prints", arg)
	}
	return id, nil
}

// flagID checks the id a flag names, before any request; hint names the command that prints it.
func flagID(flag, value, hint string) (string, error) {
	id := strings.TrimSpace(value)
	if id == "" {
		return "", fmt.Errorf("%s is required", flag)
	}
	if !userinput.IsID(id) {
		return "", fmt.Errorf("invalid %s %q: use the ID that '%s' prints", flag, value, hint)
	}
	return id, nil
}

// optionalFlagID is flagID for a filter flag: an empty value does not filter.
func optionalFlagID(flag, value, hint string) (string, error) {
	if strings.TrimSpace(value) == "" {
		return "", nil
	}
	return flagID(flag, value, hint)
}

// Label names a document in command output by its title and id.
func Label(d client.Document) string {
	if d.Title == "" {
		return d.ID
	}
	return fmt.Sprintf("%q (%s)", guardrail.OneLine(d.Title), d.ID)
}

// orDash is a value on one terminal-safe line, or "-" when it is empty.
func orDash(value string) string {
	value = guardrail.OneLine(value)
	if value == "" {
		return "-"
	}
	return value
}

// deref is the value of an optional field, empty when it is nil.
func deref(value *string) string {
	if value == nil {
		return ""
	}
	return *value
}
