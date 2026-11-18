package pr

import (
	"fmt"
	"io"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// ListRepositories reads the repositories Kazan's GitHub App can read for the organization.
func ListRepositories(c *client.Client) ([]client.GithubRepository, error) {
	return c.ListGithubRepositories()
}

// PrintRepositories writes the repositories by name as one table: name, GitHub id and whether
// the repository is private.
func PrintRepositories(out io.Writer, repos []client.GithubRepository) error {
	if len(repos) == 0 {
		_, _ = fmt.Fprintln(out, "No repositories found.")
		return nil
	}
	sorted := append([]client.GithubRepository(nil), repos...)
	SortRepositories(sorted)
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "REPOSITORY\tID\tPRIVATE")
	for _, r := range sorted {
		private := "no"
		if r.PrivateRepository {
			private = "yes"
		}
		_, _ = fmt.Fprintf(w, "%s\t%d\t%s\n", guardrail.OneLine(r.FullName), r.GithubRepositoryID, private)
	}
	return w.Flush()
}
