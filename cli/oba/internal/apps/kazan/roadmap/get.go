package roadmap

import (
	"fmt"
	"net/url"
	"strings"
	"time"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
)

// Options selects the roadmap: Children adds a lane for each child board the user is a member
// of; From and To, YYYY-MM-DD days or empty, keep the milestones whose span overlaps the window.
type Options struct {
	Children bool
	From     string
	To       string
}

// Check refuses a window bound that is not a day, or From after To, before any call. Spaces
// around a typed value are allowed.
func (o Options) Check() error {
	var fromDate, toDate time.Time
	var err error
	if o.From != "" {
		if fromDate, err = dates.ParseDate(strings.TrimSpace(o.From)); err != nil {
			return fmt.Errorf("--from must be a date in YYYY-MM-DD format, got %q", o.From)
		}
	}
	if o.To != "" {
		if toDate, err = dates.ParseDate(strings.TrimSpace(o.To)); err != nil {
			return fmt.Errorf("--to must be a date in YYYY-MM-DD format, got %q", o.To)
		}
	}
	if o.From != "" && o.To != "" && fromDate.After(toDate) {
		return fmt.Errorf("--from (%s) is after --to (%s)", o.From, o.To)
	}
	return nil
}

// Get reads the roadmap of board boardID.
func Get(c *client.Client, boardID string, opts Options) (client.Roadmap, error) {
	if err := opts.Check(); err != nil {
		return client.Roadmap{}, err
	}
	query := url.Values{}
	if opts.Children {
		query.Set("includeChildBoards", "true")
	}
	if from := strings.TrimSpace(opts.From); from != "" {
		query.Set("from", from)
	}
	if to := strings.TrimSpace(opts.To); to != "" {
		query.Set("to", to)
	}
	return c.GetRoadmap(boardID, query)
}
