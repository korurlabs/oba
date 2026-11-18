package pr

import "github.com/korurlabs/oba-cli/internal/dojo/client"

// Delete deletes one of the caller's shares; its card leaves the PR channel. Only the author may.
func Delete(c *client.Client, shareID string) error {
	return c.DeletePullRequestShare(shareID)
}
