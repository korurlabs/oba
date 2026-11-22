package legalentity

import (
	"fmt"
	"io"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// List reads every legal entity of the client's organization, sorted by short name.
func List(c *client.Client) ([]client.LegalEntity, error) {
	return c.ListLegalEntities()
}

// PrintList writes the legal entities as a SHORT NAME, LEGAL NAME, COUNTRY, REGISTRATION,
// UPDATED, ID table, or "No legal entities." when there are none. Every cell is cut to one
// terminal-safe line.
func PrintList(out io.Writer, entities []client.LegalEntity) error {
	if len(entities) == 0 {
		_, _ = fmt.Fprintln(out, "No legal entities.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "SHORT NAME\tLEGAL NAME\tCOUNTRY\tREGISTRATION\tUPDATED\tID")
	for _, e := range entities {
		updated := "-"
		if e.UpdatedAt != "" {
			updated = dates.FormatInstant(e.UpdatedAt)
		}
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%s\t%s\n", guardrail.OneLine(e.ShortName), guardrail.OneLine(e.LegalName),
			guardrail.OneLine(e.Country), orDash(e.RegistrationNumber), updated, guardrail.OneLine(e.ID))
	}
	return w.Flush()
}
