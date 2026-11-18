package pr

import (
	"encoding/json"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// Get reads one share of the organization, with its data as the API sent it.
func Get(c *client.Client, shareID string) (client.PullRequestShare, json.RawMessage, error) {
	return c.GetPullRequestShare(shareID)
}
