package contact

import (
	"fmt"
	"io"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// CreateFilled creates the picked contacts, per relation a header line and one line per
// contact, then a summary. It returns an error naming the failures, if any.
func CreateFilled(c *client.Client, out io.Writer, picked []FillResult) error {
	total, failed := 0, 0
	for _, result := range picked {
		_, _ = fmt.Fprintf(out, "%s (%s)\n", guardrail.OneLine(result.Target.Name), result.Target.ID)
		total += len(result.Contacts)
		failed += CreateFound(c, out, result.Target.ID, result.Contacts)
	}
	_, _ = fmt.Fprintf(out, "Created %d of %d contacts.\n", total-failed, total)
	if failed > 0 {
		return fmt.Errorf("%d of %d contacts were not created; add them with 'oba otak contact create <relationId>'", failed, total)
	}
	return nil
}
