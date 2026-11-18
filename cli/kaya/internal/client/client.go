// Package client calls the Kaya API. Every answer is the CustomResponse wrapper
// {success, message, data, pagination, error}; a refusal carries error {code, message, errorValues}.
package client

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

// HeaderOrganizationID names the organization a user call acts in.
const HeaderOrganizationID = "X-Organization-ID"

// maxResponseBytes bounds what the client reads of one answer. A seed report of 200 sets with
// 5000 reported paths stays far below it.
const maxResponseBytes = 32 << 20

// TokenFunc returns the bearer token of the next call: a user JWT for user calls, the machine token
// (kyt_...) for the ingest and delivery calls.
type TokenFunc func(ctx context.Context) (string, error)

// APIError is a refusal of the API, with the error code and values of the CustomResponse.
type APIError struct {
	Status      int
	Code        string
	Message     string
	ErrorValues map[string]any
}

func (e *APIError) Error() string {
	if e.Code == "" {
		return fmt.Sprintf("Kaya API answered %d: %s", e.Status, e.Message)
	}
	return fmt.Sprintf("Kaya API answered %d %s: %s", e.Status, e.Code, e.Message)
}

// IsCode reports whether err is an APIError with this code.
func IsCode(err error, code string) bool {
	var apiErr *APIError
	return errors.As(err, &apiErr) && apiErr.Code == code
}

type envelope struct {
	Success bool            `json:"success"`
	Message string          `json:"message"`
	Data    json.RawMessage `json:"data"`
	Error   *struct {
		Code        string         `json:"code"`
		Message     string         `json:"message"`
		ErrorValues map[string]any `json:"errorValues"`
	} `json:"error"`
}

// Client calls one Kaya API base URL.
type Client struct {
	baseURL string
	orgID   string
	token   TokenFunc
	http    *http.Client
}

// New returns a client. orgID may be empty for calls without an organization (/users/me, and
// the machine-token calls).
func New(baseURL, orgID string, token TokenFunc) *Client {
	return &Client{
		baseURL: strings.TrimRight(baseURL, "/"),
		orgID:   orgID,
		token:   token,
		http:    &http.Client{Timeout: 60 * time.Second},
	}
}

// OrgID is the organization the client sends.
func (c *Client) OrgID() string {
	return c.orgID
}

// WithOrg returns a copy of the client that acts in another organization.
func (c *Client) WithOrg(orgID string) *Client {
	copied := *c
	copied.orgID = orgID
	return &copied
}

// doJSON sends payload as JSON (none when nil) and decodes the data of the answer into out
// (skipped when nil).
func (c *Client) doJSON(ctx context.Context, method, path string, payload, out any) error {
	var body []byte
	contentType := ""
	if payload != nil {
		encoded, err := json.Marshal(payload)
		if err != nil {
			return fmt.Errorf("encode the request to %s: %w", path, err)
		}
		body = encoded
		contentType = "application/json"
	}
	return c.do(ctx, method, path, contentType, body, out)
}

func (c *Client) do(ctx context.Context, method, path, contentType string, body []byte, out any) error {
	var reader io.Reader
	if body != nil {
		reader = bytes.NewReader(body)
	}
	req, err := http.NewRequestWithContext(ctx, method, c.baseURL+path, reader)
	if err != nil {
		return fmt.Errorf("build the request to %s: %w", path, err)
	}
	token, err := c.token(ctx)
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Accept", "application/json")
	if contentType != "" {
		req.Header.Set("Content-Type", contentType)
	}
	if c.orgID != "" {
		req.Header.Set(HeaderOrganizationID, c.orgID)
	}

	resp, err := c.http.Do(req)
	if err != nil {
		return fmt.Errorf("%s %s: %w", method, c.baseURL+path, err)
	}
	defer func() { _ = resp.Body.Close() }()
	raw, err := io.ReadAll(io.LimitReader(resp.Body, maxResponseBytes))
	if err != nil {
		return fmt.Errorf("read the answer of %s %s: %w", method, path, err)
	}

	var env envelope
	decodeErr := json.Unmarshal(raw, &env)
	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		apiErr := &APIError{Status: resp.StatusCode, Message: strings.TrimSpace(string(raw))}
		if decodeErr == nil && env.Error != nil {
			apiErr.Code = env.Error.Code
			apiErr.Message = env.Error.Message
			apiErr.ErrorValues = env.Error.ErrorValues
		} else if decodeErr == nil && env.Message != "" {
			apiErr.Message = env.Message
		}
		return apiErr
	}
	if decodeErr != nil {
		return fmt.Errorf("parse the answer of %s %s: %w", method, path, decodeErr)
	}
	if out == nil || len(env.Data) == 0 || string(env.Data) == "null" {
		return nil
	}
	if err := json.Unmarshal(env.Data, out); err != nil {
		return fmt.Errorf("parse the data of %s %s: %w", method, path, err)
	}
	return nil
}
