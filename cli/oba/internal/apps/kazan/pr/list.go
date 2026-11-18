package pr

import (
	"fmt"
	"io"
	"net/url"
	"strconv"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// ListFilter selects one page of the PR channel's shares. Statuses and AuthorIDs are resolved
// values; Pending keeps the shares that wait for the caller's approval.
type ListFilter struct {
	Page      int
	Amount    int
	Statuses  []string
	AuthorIDs []string
	Pending   bool
}

// CheckPaging refuses a page below 1 or an amount outside 1 to MaxSharePageSize, before any
// request.
func (f ListFilter) CheckPaging() error {
	if f.Page < 1 {
		return fmt.Errorf("--page must be 1 or more, got %d", f.Page)
	}
	if f.Amount < 1 || f.Amount > MaxSharePageSize {
		return fmt.Errorf("--amount must be from 1 to %d, got %d", MaxSharePageSize, f.Amount)
	}
	return nil
}

func (f ListFilter) query() url.Values {
	q := url.Values{}
	q.Set("page", strconv.Itoa(f.Page))
	q.Set("amount", strconv.Itoa(f.Amount))
	for _, s := range f.Statuses {
		q.Add("statuses", s)
	}
	if f.Pending {
		q.Set("awaitingMyReview", "true")
	}
	for _, id := range f.AuthorIDs {
		q.Add("authorIds", id)
	}
	return q
}

// List reads one page of the PR channel's shares, newest first; an edit does not move a share.
func List(c *client.Client, f ListFilter) (client.PullRequestSharePage, error) {
	return c.ListPullRequestShares(f.query())
}

// PrintList writes each share as a block with its title, a blank line between, and a footer
// when there are more pages.
func PrintList(out io.Writer, page client.PullRequestSharePage) {
	if len(page.Shares) == 0 {
		_, _ = fmt.Fprintln(out, "No pull request shares found.")
		return
	}
	for i, s := range page.Shares {
		if i > 0 {
			_, _ = fmt.Fprintln(out)
		}
		PrintBlock(out, s, true)
	}
	if page.TotalPages > 1 {
		_, _ = fmt.Fprintf(out, "\nPage %d of %d (%d shares). Use --page for more.\n", page.CurrentPage, page.TotalPages, page.TotalElements)
	}
}
