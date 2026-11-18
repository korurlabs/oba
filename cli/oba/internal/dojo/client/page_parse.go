package client

import (
	"encoding/json"
	"fmt"
)

// parsePage reads one page of a list read page by page: the items and the envelope's paging
// details, zero when the API sent none. what names the list in the error.
func parsePage[T any](body []byte, what string) ([]T, Pagination, error) {
	var resp APIResponse[[]T]
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, Pagination{}, fmt.Errorf("failed to read %s: %w", what, err)
	}
	if resp.Pagination == nil {
		return resp.Data, Pagination{}, nil
	}
	return resp.Data, *resp.Pagination, nil
}
