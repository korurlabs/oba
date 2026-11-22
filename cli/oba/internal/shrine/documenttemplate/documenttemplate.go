// Package documenttemplate lists, creates, downloads and uploads the document templates of the
// current organization: reusable HTML documents with their PDF, rendered locally on every upload,
// from which documents start ('oba document create --template'). A template bound to a legal
// entity serves that legal entity only; one without serves every legal entity.
package documenttemplate

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// ParseID checks a template id argument before any request: templates are named by their id
// only.
func ParseID(arg string) (string, error) {
	id := strings.TrimSpace(arg)
	if !userinput.IsID(id) {
		return "", fmt.Errorf("invalid document template ID %q: use the ID that 'oba document template list' prints", arg)
	}
	return id, nil
}

// legalEntityID checks the --legal-entity value before any request; an empty value is no legal
// entity.
func legalEntityID(value string) (string, error) {
	id := strings.TrimSpace(value)
	if id == "" {
		return "", nil
	}
	if !userinput.IsID(id) {
		return "", fmt.Errorf("invalid --legal-entity %q: use the ID that 'oba legal-entity list' prints", value)
	}
	return id, nil
}

// Label names a template in command output by its name and id.
func Label(t client.DocumentTemplate) string {
	if t.Name == "" {
		return t.ID
	}
	return fmt.Sprintf("%q (%s)", guardrail.OneLine(t.Name), t.ID)
}

// orDash is a value on one terminal-safe line, or "-" when it is empty.
func orDash(value string) string {
	value = guardrail.OneLine(value)
	if value == "" {
		return "-"
	}
	return value
}
