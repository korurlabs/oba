package updates

import (
	"fmt"
	"net/url"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
)

// Missing reads who has not posted an update in the organization of c, on day, a
// Europe/Istanbul YYYY-MM-DD, or today there when day is empty: the server picks today, which
// the local clock cannot tell reliably.
func Missing(c *client.Client, day string) (client.DailyUpdateMissing, error) {
	q := url.Values{}
	if day != "" {
		d, err := dates.ToAPIDate(day)
		if err != nil {
			return client.DailyUpdateMissing{}, fmt.Errorf("--date: %w", err)
		}
		q.Set("date", d)
	}
	q.Set("organizationId", c.OrgID)
	report, err := c.MissingDailyUpdates(q)
	if err != nil {
		return client.DailyUpdateMissing{}, fmt.Errorf("failed to list members without an update: %w", err)
	}
	return report, nil
}

// CheckDay validates a --date before any request.
func CheckDay(day string) error {
	if day == "" {
		return nil
	}
	if _, err := dates.ToAPIDate(day); err != nil {
		return fmt.Errorf("--date: %w", err)
	}
	return nil
}
