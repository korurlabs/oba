package pr

import "github.com/korurlabs/oba-cli/internal/dojo/client"

// SetApproval sets the caller's approval of a share, or with approve false withdraws it. Only a
// reviewer of the share may; doing either twice changes nothing.
func SetApproval(c *client.Client, shareID string, approve bool) error {
	if approve {
		return c.ApprovePullRequestShare(shareID)
	}
	return c.WithdrawPullRequestShareApproval(shareID)
}
