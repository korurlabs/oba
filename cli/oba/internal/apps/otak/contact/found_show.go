package contact

import (
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"

	"fmt"
	"io"
	"strings"
)

// FoundIndent is the indent of the lines of a found contact under its company's row.
const FoundIndent = "       "

// ShowFound prints the found contacts under a company's row: per contact its line (see Line)
// and its source page, or "no contact found".
func ShowFound(out io.Writer, contacts []FoundContact) {
	if len(contacts) == 0 {
		_, _ = fmt.Fprintln(out, FoundIndent+"no contact found")
		return
	}
	for _, contact := range contacts {
		_, _ = fmt.Fprintln(out, FoundIndent+"contact: "+Line(contact))
		_, _ = fmt.Fprintln(out, FoundIndent+"source: "+guardrail.OneLine(contact.SourceURL))
	}
}

// Line is a found contact on one line: its name, role, e-mail and phone, missing parts left out.
// Every value is sanitized, since it comes from the web.
func Line(contact FoundContact) string {
	parts := []string{guardrail.OneLine(contact.Name)}
	for _, value := range []*string{contact.Role, contact.Email, contact.Phone} {
		if part := guardrail.OneLine(orEmpty(value)); part != "" {
			parts = append(parts, part)
		}
	}
	return strings.Join(parts, " · ")
}
