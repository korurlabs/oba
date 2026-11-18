package pr

import "github.com/korurlabs/oba-cli/internal/dojo/client"

// Update saves the edited content e of share shareID: the full new status, note, reviewers and
// related tickets. Only the author may.
func Update(c *client.Client, shareID string, e Edit) (client.PullRequestShare, error) {
	return c.UpdatePullRequestShare(shareID, e.Request())
}
