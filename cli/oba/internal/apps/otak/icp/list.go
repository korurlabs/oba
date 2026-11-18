package icp

import (
	"fmt"
	"io"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// List reads every ICP of the organization.
func List(c *client.Client) ([]client.Icp, error) {
	return c.ListIcps()
}

// PrintList writes the ICPs as a table: name, industry, country, size range, target role, id.
func PrintList(out io.Writer, icps []client.Icp) error {
	if len(icps) == 0 {
		_, _ = fmt.Fprintln(out, "No ICPs.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "NAME\tINDUSTRY\tCOUNTRY\tSIZE\tTARGET ROLE\tID")
	for _, icp := range icps {
		_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%s\t%s\n", icp.Name, icp.Industry, valueOr(icp.Country, "any"),
			SizeRange(icp.CompanySizeMin, icp.CompanySizeMax), valueOr(icp.TargetPersonRole, "any"), icp.ID)
	}
	return w.Flush()
}
