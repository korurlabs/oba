package updates

import (
	"fmt"
	"io"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// PrintSummary prints the paste-ready text of an update alone, so that it can be piped to the
// clipboard.
func PrintSummary(out io.Writer, text string) {
	_, _ = fmt.Fprintln(out, strings.TrimRight(guardrail.Sanitize(text), "\n"))
}

// PrintProgressSync tells which tickets take their line's percentage as their progress, after
// label. It goes to the error stream, so that the output carries only the update text.
func PrintProgressSync(errOut io.Writer, items []client.DailyUpdateItem, label string) {
	var synced []string
	for _, item := range items {
		if item.ProgressSynced {
			synced = append(synced, fmt.Sprintf("%s %d%%", cell(item.TicketKey), item.Percentage))
		}
	}
	if len(synced) > 0 {
		_, _ = fmt.Fprintf(errOut, "%s: %s\n", label, strings.Join(synced, ", "))
	}
}
