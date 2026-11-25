package client

import (
	"fmt"
	"net/http"
)

// FindKalkanPage looks a normalized path up in the tree of pages the caller can see. A path the
// tree does not hold, missing or below a root the caller cannot see, is a 404 APIError, as the
// API reports a hidden page, so that it exits with the status of a denial.
func (c *Client) FindKalkanPage(path string) (KalkanPageListItem, error) {
	pages, err := c.ListKalkanPages()
	if err != nil {
		return KalkanPageListItem{}, err
	}
	for _, page := range pages {
		if page.Path == path {
			return page, nil
		}
	}
	return KalkanPageListItem{}, &APIError{
		Status:  http.StatusNotFound,
		Code:    CodeKalkanPageNotFound,
		Message: fmt.Sprintf("No page at %s, or you have no access to it.", path),
	}
}
