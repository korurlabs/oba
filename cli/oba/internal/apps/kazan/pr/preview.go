package pr

import "github.com/korurlabs/oba-cli/internal/dojo/client"

// Preview renders the card req would post, without saving anything. It fails where a share
// would: a limit, a reviewer rule, a related ticket the caller cannot open, GitHub, or no PR
// channel.
func Preview(c *client.Client, req client.PullRequestShareRequest) (client.PullRequestSharePreview, error) {
	return c.PreviewPullRequestShare(req)
}
