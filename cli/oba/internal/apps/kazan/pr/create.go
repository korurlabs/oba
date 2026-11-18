package pr

import (
	"errors"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// errPullRequired refuses a share without a pull request where nobody can be asked for one.
var errPullRequired = errors.New("a pull request is required: pass a GitHub URL, owner/name#N, name#N, or N with --repo")

// Create shares the pull request of req: it posts one card in the PR channel.
func Create(c *client.Client, req client.PullRequestShareRequest) (client.PullRequestShare, error) {
	return c.CreatePullRequestShare(req)
}
