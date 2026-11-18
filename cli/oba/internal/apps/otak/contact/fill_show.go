package contact

import (
	"fmt"
	"io"

	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// ShowFillResults prints each relation with its found contacts numbered across all relations,
// or "no contact found", and returns the contact count.
func ShowFillResults(out io.Writer, results []FillResult) int {
	number := 0
	for _, result := range results {
		_, _ = fmt.Fprintf(out, "%s - %s\n", guardrail.OneLine(result.Target.Name), guardrail.OneLine(result.Target.Website))
		if len(result.Contacts) == 0 {
			_, _ = fmt.Fprintln(out, FoundIndent+"no contact found")
			continue
		}
		for _, contact := range result.Contacts {
			number++
			_, _ = fmt.Fprintf(out, "%*d. %s\n", len(FoundIndent)-2, number, Line(contact))
			_, _ = fmt.Fprintln(out, FoundIndent+"source: "+guardrail.OneLine(contact.SourceURL))
		}
	}
	return number
}
