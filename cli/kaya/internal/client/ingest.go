package client

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

// IngestTimeout bounds one ingest call, so a hung connection holds the sender for one round only.
const IngestTimeout = 15 * time.Second

// NewIngest returns the client of the session ingest. It follows no redirect: a redirect would
// carry the machine token in its Authorization header to wherever it points, so the 3xx answer
// itself comes back as a refusal.
func NewIngest(baseURL string, token TokenFunc) *Client {
	return &Client{
		baseURL: strings.TrimRight(baseURL, "/"),
		token:   token,
		http: &http.Client{
			Timeout:       IngestTimeout,
			CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
		},
	}
}

// PostIngestBatch sends one session batch, already encoded, to POST /kaya/ingest/batches. Only a 200
// is an acceptance: it returns the data of the answer, nil when the body holds none or is not
// the JSON envelope. Every other status is an *APIError with the code and values Kaya gave. Any
// other error means Kaya was not reached or did not answer in time.
func (c *Client) PostIngestBatch(ctx context.Context, body []byte) (json.RawMessage, error) {
	const path = "/kaya/ingest/batches"
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.baseURL+path, bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("build the request to %s: %w", path, err)
	}
	token, err := c.token(ctx)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Accept", "application/json")
	req.Header.Set("Content-Type", "application/json")
	resp, err := c.http.Do(req)
	if err != nil {
		return nil, fmt.Errorf("POST %s: %w", c.baseURL+path, err)
	}
	defer func() { _ = resp.Body.Close() }()
	raw, err := io.ReadAll(io.LimitReader(resp.Body, maxResponseBytes))
	if err != nil {
		return nil, fmt.Errorf("read the answer of POST %s: %w", path, err)
	}
	var env envelope
	decodeErr := json.Unmarshal(raw, &env)
	if resp.StatusCode != http.StatusOK {
		apiErr := &APIError{Status: resp.StatusCode, Message: strings.TrimSpace(string(raw))}
		if decodeErr == nil && env.Error != nil {
			apiErr.Code = env.Error.Code
			apiErr.Message = env.Error.Message
			apiErr.ErrorValues = env.Error.ErrorValues
		}
		return nil, apiErr
	}
	if decodeErr != nil || len(env.Data) == 0 || string(env.Data) == "null" {
		// rule-10-no-op -- reason: Kaya stored the batch (200); an answer without readable data only means the caller acknowledges what it sent instead of what Kaya counted.
		return nil, nil
	}
	return env.Data, nil
}
