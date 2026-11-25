package cyberscore

import (
	"fmt"
	"io"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// PrintCheck writes one check as labelled lines; the description only when it has one.
func PrintCheck(out io.Writer, ch client.CyberscoreCheck) {
	line := func(label, value string) {
		_, _ = fmt.Fprintf(out, "%-12s %s\n", label, value)
	}
	line("Name:", guardrail.OneLine(ch.Name))
	line("ID:", ch.ID)
	line("Category:", orDash(ch.Category))
	line("Weight:", fmt.Sprint(ch.Weight))
	line("Sort Order:", fmt.Sprint(ch.SortOrder))
	line("Outcome:", guardrail.OneLine(ch.Outcome))
	line("Status:", guardrail.OneLine(ch.Status))
	line("Expected:", guardrail.OneLine(ch.Expected))
	if ch.Description != nil && *ch.Description != "" {
		line("Description:", guardrail.OneLine(*ch.Description))
	}
	if ch.UpdatedAt != "" {
		line("Updated:", dates.FormatInstant(ch.UpdatedAt))
	}
}
