package dates

import (
	"time"

	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// instantDisplayLayout is how an instant is shown: minutes in the viewer's zone.
const instantDisplayLayout = "2006-01-02 15:04"

// FormatInstant renders an API instant in the zone of the machine the CLI runs on, the viewer's
// zone. A value that does not parse is shown as the API sent it, on one terminal-safe line.
func FormatInstant(instant string) string {
	t, err := time.Parse(time.RFC3339Nano, instant)
	if err != nil {
		return guardrail.OneLine(instant)
	}
	return t.Local().Format(instantDisplayLayout)
}
