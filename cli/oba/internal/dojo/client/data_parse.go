package client

import (
	"encoding/json"
	"fmt"
)

// parseData reads the data of a CustomResponse envelope; what names the object in the error.
func parseData[T any](body []byte, what string) (T, error) {
	var resp APIResponse[T]
	if err := json.Unmarshal(body, &resp); err != nil {
		var zero T
		return zero, fmt.Errorf("failed to read %s: %w", what, err)
	}
	return resp.Data, nil
}
