package pr

import (
	"fmt"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/ticket"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// ParseTickets splits a related ticket list and refuses an entry that is neither a ticket key
// nor an id, more than the limit, or an entry given twice, before any request. flag names the
// flag in messages.
func ParseTickets(flag, raw string) ([]string, error) {
	refs := userinput.SplitCSV(raw)
	if len(refs) > MaxRelatedTickets {
		return nil, fmt.Errorf("--%s names %d tickets; a share can name at most %d related tickets", flag, len(refs), MaxRelatedTickets)
	}
	for _, ref := range refs {
		if userinput.IsID(ref) {
			continue
		}
		if _, _, err := ticket.ParseKey(ref); err != nil {
			return nil, fmt.Errorf("--%s: %w", flag, err)
		}
	}
	if err := refuseTwice(flag, refs); err != nil {
		return nil, err
	}
	return refs, nil
}
