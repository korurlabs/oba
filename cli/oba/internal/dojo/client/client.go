// Package client talks to the Oba API: bearer token, organization header, the CustomResponse
// envelope and its error shape, one retry after a token refresh on 401 and one on 429.
package client

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/korurlabs/oba-cli/internal/dojo/config"
	"github.com/korurlabs/oba-cli/internal/dojo/httplog"
	"github.com/korurlabs/oba-cli/internal/shrine/auth"
)

const (
	requestTimeout = 30 * time.Second

	contentTypeJSON = "application/json"
)

// TokenFunc returns the access token to send.
type TokenFunc func() (string, error)

// RefreshFunc obtains a new access token after a 401 and returns it.
type RefreshFunc func() (string, error)

// Client sends requests to one Oba API in one organization.
type Client struct {
	BaseURL    string
	OrgID      string
	httpClient *http.Client
	tokenFunc  TokenFunc
	refresh    RefreshFunc
	unrecorded bool
}

// New returns a client with a token source and an optional refresh, nil for none. An empty
// orgID sends no X-Organization-ID header, as the organization-exempt /users/me calls need.
func New(baseURL, orgID string, tokenFunc TokenFunc, refresh RefreshFunc) *Client {
	return &Client{
		BaseURL:    strings.TrimRight(baseURL, "/"),
		OrgID:      orgID,
		httpClient: &http.Client{Timeout: requestTimeout, Transport: httplog.Transport},
		tokenFunc:  tokenFunc,
		refresh:    refresh,
	}
}

// NewWithToken returns a client with a fixed token and no refresh, as login uses before the
// session is stored.
func NewWithToken(baseURL, orgID, token string) *Client {
	return New(baseURL, orgID, func() (string, error) { return token, nil }, nil)
}

// NewForSession returns a client on the stored session: the API URL resolved from the
// environment and the session, its access token, and a refresh through the session's refresh
// token that stores the new tokens.
func NewForSession(creds auth.Credentials, orgID string) *Client {
	endpoints := config.Resolve(config.Endpoints{APIURL: creds.APIURL, Issuer: creds.IssuerURL, ClientID: creds.ClientID})
	s := &session{creds: creds, endpoints: endpoints}
	return New(endpoints.APIURL, orgID, s.token, s.refresh)
}

// session holds the current tokens of one process, so that a refreshed token is used by every
// later request of the same client.
type session struct {
	creds     auth.Credentials
	endpoints config.Endpoints
}

func (s *session) token() (string, error) {
	return s.creds.AccessToken, nil
}

func (s *session) refresh() (string, error) {
	if s.creds.RefreshToken == "" {
		return "", errors.New("no refresh token in the stored session")
	}
	tokenResp, err := auth.RefreshTokenDirect(s.endpoints.Issuer, s.endpoints.ClientID, s.creds.RefreshToken)
	if err != nil {
		return "", err
	}
	s.creds.AccessToken = tokenResp.AccessToken
	if tokenResp.RefreshToken != "" {
		s.creds.RefreshToken = tokenResp.RefreshToken
	}
	s.creds.ExpiresAt = time.Now().Add(time.Duration(tokenResp.ExpiresIn) * time.Second)
	if err := auth.NewTokenStore().Save(s.creds); err != nil {
		return "", fmt.Errorf("refreshed the session but could not store it: %w", err)
	}
	return s.creds.AccessToken, nil
}

// APIResponse is the CustomResponse envelope of a successful call. Pagination is set on list
// calls only.
type APIResponse[T any] struct {
	Data       T           `json:"data"`
	Pagination *Pagination `json:"pagination"`
}

// Pagination is the part of the envelope's paging details (PaginationDetailsResponse) the CLI
// reads to walk a list, where Last is true on the final page, and to print the totals under a
// list read page by page.
type Pagination struct {
	CurrentPage   int  `json:"currentPage"`
	TotalPages    int  `json:"totalPages"`
	TotalElements int  `json:"totalElements"`
	Last          bool `json:"last"`
}

// ExitCode is the process exit status for an error a command returns: 2 for an API 404, 3 for
// an API 403 and 1 otherwise. A wrapped APIError counts.
func ExitCode(err error) int {
	var apiErr *APIError
	if errors.As(err, &apiErr) {
		switch apiErr.Status {
		case http.StatusNotFound:
			return 2
		case http.StatusForbidden:
			return 3
		}
	}
	return 1
}

// APIError is a failed call: the HTTP status and the envelope's error code, message and
// per-field messages (errorValues). Code is empty when the body was not an Oba error.
type APIError struct {
	Status  int
	Code    string
	Message string
	Fields  map[string]string
}

// Error renders "<code>: <message>", then one "  <field>: <message>" line per field, sorted by
// field name so that the output is stable.
func (e *APIError) Error() string {
	var b strings.Builder
	if e.Code != "" {
		b.WriteString(e.Code)
		b.WriteString(": ")
	}
	b.WriteString(e.Message)
	names := make([]string, 0, len(e.Fields))
	for name := range e.Fields {
		names = append(names, name)
	}
	sort.Strings(names)
	for _, name := range names {
		fmt.Fprintf(&b, "\n  %s: %s", name, e.Fields[name])
	}
	return b.String()
}

// Get sends a GET and returns the response body.
func (c *Client) Get(path string) ([]byte, error) {
	return c.do(http.MethodGet, path, nil)
}

// Post sends a POST with payload encoded as JSON; nil sends no body.
func (c *Client) Post(path string, payload any) ([]byte, error) {
	return c.do(http.MethodPost, path, payload)
}

// Put sends a PUT with payload encoded as JSON.
func (c *Client) Put(path string, payload any) ([]byte, error) {
	return c.do(http.MethodPut, path, payload)
}

// Patch sends a PATCH with payload encoded as JSON.
func (c *Client) Patch(path string, payload any) ([]byte, error) {
	return c.do(http.MethodPatch, path, payload)
}

// Delete sends a DELETE.
func (c *Client) Delete(path string) ([]byte, error) {
	return c.do(http.MethodDelete, path, nil)
}

func (c *Client) do(method, path string, payload any) ([]byte, error) {
	var body []byte
	if payload != nil {
		// json.RawMessage values are written as they are, so numbers given on stdin are never
		// re-encoded.
		encoded, err := json.Marshal(payload)
		if err != nil {
			return nil, fmt.Errorf("failed to encode request body: %w", err)
		}
		body = encoded
	}
	return c.send(method, path, body, contentTypeJSON, true)
}

// send sends body like exchange and records the response for --json.
func (c *Client) send(method, path string, body []byte, contentType string, allowRetry bool) ([]byte, error) {
	respBody, err := c.exchange(method, path, body, contentType, allowRetry)
	if err != nil {
		return nil, err
	}
	c.record(method, respBody)
	return respBody, nil
}

// exchange sends body, when not nil, with the given Content-Type. A retry after a refresh or a
// 429 sends the same bytes again.
func (c *Client) exchange(method, path string, body []byte, contentType string, allowRetry bool) ([]byte, error) {
	url := c.BaseURL + path

	var bodyReader io.Reader
	if body != nil {
		bodyReader = bytes.NewReader(body)
	}
	req, err := http.NewRequest(method, url, bodyReader)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	token, err := c.tokenFunc()
	if err != nil {
		return nil, fmt.Errorf("failed to get auth token: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Accept", "application/json")
	if body != nil {
		req.Header.Set("Content-Type", contentType)
	}
	if c.OrgID != "" {
		req.Header.Set("X-Organization-ID", c.OrgID)
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		if os.IsTimeout(err) || strings.Contains(err.Error(), "connection refused") {
			return nil, fmt.Errorf("could not connect to %s. Check your network or whether the API is running", c.BaseURL)
		}
		return nil, fmt.Errorf("request to %s failed: %w", url, err)
	}
	defer func() { _ = resp.Body.Close() }()

	respBody, err := io.ReadAll(io.LimitReader(resp.Body, 10*1024*1024))
	if err != nil {
		return nil, fmt.Errorf("failed to read response from %s: %w", url, err)
	}

	if resp.StatusCode == http.StatusUnauthorized && allowRetry && c.refresh != nil {
		newToken, refreshErr := c.refresh()
		if refreshErr != nil {
			return nil, fmt.Errorf("your session has expired and could not be refreshed (%w). Run 'oba login'", refreshErr)
		}
		c.tokenFunc = func() (string, error) { return newToken, nil }
		return c.exchange(method, path, body, contentType, false)
	}

	if resp.StatusCode == http.StatusTooManyRequests && allowRetry {
		waitSeconds := 5
		if parsed, parseErr := strconv.Atoi(resp.Header.Get("Retry-After")); parseErr == nil && parsed >= 0 {
			waitSeconds = parsed
		}
		time.Sleep(time.Duration(waitSeconds) * time.Second)
		return c.exchange(method, path, body, contentType, false)
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, ParseError(resp.StatusCode, respBody)
	}
	return respBody, nil
}

// errorEnvelope is the error part of CustomResponse. errorValues holds any JSON value per
// field; strings are shown as they are, anything else as its JSON text.
type errorEnvelope struct {
	Message string `json:"message"`
	Error   *struct {
		Code        string                     `json:"code"`
		Message     string                     `json:"message"`
		ErrorValues map[string]json.RawMessage `json:"errorValues"`
	} `json:"error"`
}

// ParseError turns a failed response into an *APIError: the envelope's code, message and
// fields when the body carries them, else a message for the status.
func ParseError(status int, body []byte) *APIError {
	var env errorEnvelope
	if json.Unmarshal(body, &env) == nil && env.Error != nil && (env.Error.Code != "" || env.Error.Message != "") {
		apiErr := &APIError{Status: status, Code: env.Error.Code, Message: env.Error.Message}
		if len(env.Error.ErrorValues) > 0 {
			apiErr.Fields = make(map[string]string, len(env.Error.ErrorValues))
			for name, raw := range env.Error.ErrorValues {
				var s string
				if json.Unmarshal(raw, &s) == nil {
					apiErr.Fields[name] = s
				} else {
					apiErr.Fields[name] = string(raw)
				}
			}
		}
		return apiErr
	}
	if env.Message != "" {
		return &APIError{Status: status, Message: env.Message}
	}

	switch status {
	case http.StatusUnauthorized:
		return &APIError{Status: status, Message: "unauthorized. Run 'oba login'"}
	case http.StatusForbidden:
		return &APIError{Status: status, Message: "access denied. You may not have permission for this operation"}
	case http.StatusNotFound:
		return &APIError{Status: status, Message: "resource not found"}
	}
	text := strings.TrimSpace(string(body))
	if len(text) > 200 {
		text = text[:200] + "..."
	}
	if text == "" {
		text = http.StatusText(status)
	}
	return &APIError{Status: status, Message: fmt.Sprintf("request failed (%d): %s", status, text)}
}
