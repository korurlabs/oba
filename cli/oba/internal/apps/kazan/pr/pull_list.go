package pr

import (
	"fmt"
	"io"
	"net/url"
	"strconv"
	"text/tabwriter"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/dates"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// PullFilter narrows a page of a repository's pull requests. Empty States and Authors narrow
// nothing.
type PullFilter struct {
	Search  string
	States  []string
	Authors []string
	Page    int
}

// query is the filter as the API's query parameters.
func (f PullFilter) query() url.Values {
	q := url.Values{}
	if f.Search != "" {
		q.Set("search", f.Search)
	}
	for _, state := range f.States {
		q.Add("states", state)
	}
	for _, author := range f.Authors {
		q.Add("authors", author)
	}
	q.Set("page", strconv.Itoa(f.Page))
	return q
}

// ListPulls resolves the repository repoRef names and reads one page of its pull requests, last
// updated first.
func ListPulls(c *client.Client, repoRef string, f PullFilter) (client.GithubPullRequestPage, error) {
	repo, err := FindRepository(c, repoRef)
	if err != nil {
		return client.GithubPullRequestPage{}, err
	}
	return c.ListGithubPullRequests(repo.GithubRepositoryID, f.query())
}

// PrintPulls writes a page of pull requests as one table, then a line when a next page may
// follow and one when GitHub shows only the first 1000 results. page is the page number shown.
func PrintPulls(out io.Writer, result client.GithubPullRequestPage, page int) error {
	if len(result.Items) == 0 {
		_, _ = fmt.Fprintln(out, "No pull requests found.")
		return nil
	}
	w := tabwriter.NewWriter(out, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(w, "NUMBER\tSTATE\tUPDATED\tAUTHOR\tTITLE")
	for _, pr := range result.Items {
		_, _ = fmt.Fprintf(w, "#%d\t%s\t%s\t%s\t%s\n", pr.Number, guardrail.OneLine(pr.State),
			dates.FormatInstant(pr.UpdatedAt), guardrail.OneLine(pr.AuthorLogin), guardrail.OneLine(pr.Title))
	}
	if err := w.Flush(); err != nil {
		return err
	}
	if result.HasMore {
		_, _ = fmt.Fprintf(out, "\nMore pull requests may follow (%d per page). Use --page %d.\n", PullPageSize, page+1)
	}
	if result.ResultsLimited {
		_, _ = fmt.Fprintln(out, "\nGitHub shows only the first 1000 results. Narrow the filters.")
	}
	return nil
}
