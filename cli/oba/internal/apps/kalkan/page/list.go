package page

import (
	"fmt"
	"net/http"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// List reads the pages the caller can see, parents first. With a path it keeps that page and the
// pages below it; a path the tree does not hold, missing or below a root the caller cannot see,
// is a 404 APIError, as the API reports a hidden page, so that it exits with the status of a
// denial.
func List(c *client.Client, path string) ([]client.KalkanPageListItem, error) {
	pages, err := c.ListKalkanPages()
	if err != nil {
		return nil, err
	}
	if path == "" {
		return pages, nil
	}
	var kept []client.KalkanPageListItem
	for _, page := range pages {
		if page.Path == path || strings.HasPrefix(page.Path, path+"/") {
			kept = append(kept, page)
		}
	}
	if len(kept) == 0 {
		return nil, &client.APIError{
			Status:  http.StatusNotFound,
			Code:    client.CodeKalkanPageNotFound,
			Message: fmt.Sprintf("No page at %s, or you have no access to it.", path),
		}
	}
	return kept, nil
}
