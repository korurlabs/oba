package legalentity

import (
	"fmt"
	"io"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// Get reads legal entity id with every field.
func Get(c *client.Client, id string) (client.LegalEntity, error) {
	return c.GetLegalEntity(id)
}

// PrintDetails writes one legal entity as labelled lines, "-" marking an empty optional field.
// Every value is cut to one terminal-safe line, since any admin of the organization types them.
func PrintDetails(out io.Writer, e client.LegalEntity) {
	line := func(label, value string) {
		_, _ = fmt.Fprintf(out, "%-21s %s\n", label, value)
	}
	line("Short Name:", guardrail.OneLine(e.ShortName))
	line("ID:", e.ID)
	line("Legal Name:", guardrail.OneLine(e.LegalName))
	line("Country:", guardrail.OneLine(e.Country))
	line("Registration Number:", orDash(e.RegistrationNumber))
	line("VAT Number:", orDash(e.VATNumber))
	line("Street:", orDash(e.Street))
	line("Postal Code:", orDash(e.PostalCode))
	line("City:", orDash(e.City))
	line("Governing Law:", orDash(e.GoverningLaw))
	line("Court:", orDash(e.Court))
	if e.CreatedAt != "" {
		line("Created:", dates.FormatInstant(e.CreatedAt))
	}
	if e.UpdatedAt != "" {
		line("Updated:", dates.FormatInstant(e.UpdatedAt))
	}
}
