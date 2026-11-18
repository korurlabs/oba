package client

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strconv"

	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
)

// ListPageSize is the most items one list call returns; the API refuses more.
const ListPageSize = 50

// ListPages reads every page of a list endpoint, ListPageSize items per call, until the
// envelope's pagination marks the last page. For --json the items of every page are recorded as
// one list, as the API sent them, rather than page by page.
func ListPages[T any](c *Client, path string, query url.Values) ([]T, error) {
	var all []T
	var raw []json.RawMessage
	for page := 1; ; page++ {
		q := url.Values{}
		for key, values := range query {
			q[key] = append([]string(nil), values...)
		}
		q.Set("page", strconv.Itoa(page))
		q.Set("amount", strconv.Itoa(ListPageSize))
		body, err := c.exchange(http.MethodGet, path+"?"+q.Encode(), nil, contentTypeJSON, true)
		if err != nil {
			return nil, err
		}
		var resp APIResponse[[]T]
		if err := json.Unmarshal(body, &resp); err != nil {
			return nil, fmt.Errorf("failed to parse page %d of %s: %w", page, path, err)
		}
		all = append(all, resp.Data...)
		if jsonout.Enabled() && !c.unrecorded {
			var items APIResponse[[]json.RawMessage]
			if err := json.Unmarshal(body, &items); err != nil {
				return nil, fmt.Errorf("failed to parse page %d of %s: %w", page, path, err)
			}
			raw = append(raw, items.Data...)
		}
		if resp.Pagination == nil || resp.Pagination.Last || len(resp.Data) == 0 {
			if !c.unrecorded {
				jsonout.RecordItems(raw)
			}
			return all, nil
		}
	}
}
