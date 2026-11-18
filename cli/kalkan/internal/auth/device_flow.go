// Package auth signs a user in to the sensey realm by the OAuth 2.0 Device Authorization Grant
// (RFC 8628), stores the tokens and refreshes them. Every Kalkan call needs this user JWT.
package auth

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// TokenResponse is the answer of the Keycloak token endpoint.
type TokenResponse struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
	ExpiresIn    int    `json:"expires_in"`
	TokenType    string `json:"token_type"`
}

// Endpoints are the OpenID Connect endpoints the device flow needs.
type Endpoints struct {
	DeviceAuthorizationEndpoint string `json:"device_authorization_endpoint"`
	TokenEndpoint               string `json:"token_endpoint"`
}

// DeviceAuthorization is the answer of the device authorization endpoint.
type DeviceAuthorization struct {
	DeviceCode              string `json:"device_code"`
	UserCode                string `json:"user_code"`
	VerificationURI         string `json:"verification_uri"`
	VerificationURIComplete string `json:"verification_uri_complete"`
	ExpiresIn               int    `json:"expires_in"`
	Interval                int    `json:"interval"`
}

type tokenError struct {
	Error            string `json:"error"`
	ErrorDescription string `json:"error_description"`
}

// DeviceFlow runs the device authorization grant against one issuer and public client.
type DeviceFlow struct {
	issuerURL string
	clientID  string
	http      *http.Client
	// sleep waits between token polls; tests replace it so they do not wait.
	sleep func(ctx context.Context, d time.Duration) error
}

// NewDeviceFlow returns a device flow for the issuer and client.
func NewDeviceFlow(issuerURL, clientID string) *DeviceFlow {
	return &DeviceFlow{
		issuerURL: strings.TrimRight(issuerURL, "/"),
		clientID:  clientID,
		http:      &http.Client{Timeout: 15 * time.Second},
		sleep:     sleepContext,
	}
}

// TokenEndpoint is the token endpoint of a Keycloak realm, used for a refresh without discovery.
func TokenEndpoint(issuerURL string) string {
	return strings.TrimRight(issuerURL, "/") + "/protocol/openid-connect/token"
}

// Discover reads the issuer's OpenID configuration.
func (d *DeviceFlow) Discover(ctx context.Context) (Endpoints, error) {
	wellKnown := d.issuerURL + "/.well-known/openid-configuration"
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, wellKnown, nil)
	if err != nil {
		return Endpoints{}, fmt.Errorf("build the OpenID configuration request: %w", err)
	}
	body, status, err := d.do(req)
	if err != nil {
		return Endpoints{}, fmt.Errorf("read the OpenID configuration from %s: %w", wellKnown, err)
	}
	if status != http.StatusOK {
		return Endpoints{}, fmt.Errorf("the OpenID configuration at %s answered %d", wellKnown, status)
	}
	var endpoints Endpoints
	if err := json.Unmarshal(body, &endpoints); err != nil {
		return Endpoints{}, fmt.Errorf("parse the OpenID configuration: %w", err)
	}
	if endpoints.DeviceAuthorizationEndpoint == "" || endpoints.TokenEndpoint == "" {
		return Endpoints{}, fmt.Errorf("the issuer %s offers no device authorization; enable it on client %s", d.issuerURL, d.clientID)
	}
	return endpoints, nil
}

// Authorize starts a device authorization.
func (d *DeviceFlow) Authorize(ctx context.Context, endpoint string) (DeviceAuthorization, error) {
	body, status, err := d.postForm(ctx, endpoint, url.Values{"client_id": {d.clientID}, "scope": {"openid"}})
	if err != nil {
		return DeviceAuthorization{}, fmt.Errorf("start the device authorization: %w", err)
	}
	if status != http.StatusOK {
		return DeviceAuthorization{}, fmt.Errorf("the device authorization answered %d: %s", status, string(body))
	}
	var authorization DeviceAuthorization
	if err := json.Unmarshal(body, &authorization); err != nil {
		return DeviceAuthorization{}, fmt.Errorf("parse the device authorization: %w", err)
	}
	return authorization, nil
}

// Poll asks the token endpoint until the user has approved the device, refused it, or the code
// expired.
func (d *DeviceFlow) Poll(ctx context.Context, endpoint string, authorization DeviceAuthorization) (TokenResponse, error) {
	interval := time.Duration(authorization.Interval) * time.Second
	if interval <= 0 {
		interval = 5 * time.Second
	}
	expiresAt := time.Now().Add(time.Duration(authorization.ExpiresIn) * time.Second)
	form := url.Values{
		"grant_type":  {"urn:ietf:params:oauth:grant-type:device_code"},
		"client_id":   {d.clientID},
		"device_code": {authorization.DeviceCode},
	}
	for {
		if time.Now().After(expiresAt) {
			return TokenResponse{}, fmt.Errorf("the sign-in code expired; run kalkan login again")
		}
		if err := d.sleep(ctx, interval); err != nil {
			return TokenResponse{}, err
		}
		body, status, err := d.postForm(ctx, endpoint, form)
		if err != nil {
			return TokenResponse{}, fmt.Errorf("poll the token endpoint: %w", err)
		}
		if status == http.StatusOK {
			return parseToken(body)
		}
		var refusal tokenError
		if err := json.Unmarshal(body, &refusal); err != nil {
			return TokenResponse{}, fmt.Errorf("the token endpoint answered %d: %s", status, string(body))
		}
		switch refusal.Error {
		case "authorization_pending":
			continue
		case "slow_down":
			interval += 5 * time.Second
			continue
		case "expired_token":
			return TokenResponse{}, fmt.Errorf("the sign-in code expired; run kalkan login again")
		case "access_denied":
			return TokenResponse{}, fmt.Errorf("the sign-in was refused in the browser")
		default:
			return TokenResponse{}, fmt.Errorf("token error %s: %s", refusal.Error, refusal.ErrorDescription)
		}
	}
}

// Refresh exchanges a refresh token for new tokens. A refusal of the token endpoint (400 or 401,
// such as an expired or revoked session) wraps ErrNotSignedIn, because only a new kalkan login
// helps; an unreachable or failing endpoint is a plain error, since a retry may succeed.
func (d *DeviceFlow) Refresh(ctx context.Context, endpoint, refreshToken string) (TokenResponse, error) {
	body, status, err := d.postForm(ctx, endpoint, url.Values{
		"grant_type":    {"refresh_token"},
		"client_id":     {d.clientID},
		"refresh_token": {refreshToken},
	})
	if err != nil {
		return TokenResponse{}, fmt.Errorf("refresh the sign-in: %w", err)
	}
	if status == http.StatusBadRequest || status == http.StatusUnauthorized {
		return TokenResponse{}, fmt.Errorf("the sign-in was refused at refresh (%d): %w", status, ErrNotSignedIn)
	}
	if status != http.StatusOK {
		return TokenResponse{}, fmt.Errorf("the sign-in could not be refreshed: the token endpoint answered %d", status)
	}
	return parseToken(body)
}

func (d *DeviceFlow) postForm(ctx context.Context, endpoint string, form url.Values) ([]byte, int, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, strings.NewReader(form.Encode()))
	if err != nil {
		return nil, 0, err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	return d.do(req)
}

func (d *DeviceFlow) do(req *http.Request) ([]byte, int, error) {
	resp, err := d.http.Do(req)
	if err != nil {
		return nil, 0, err
	}
	defer func() { _ = resp.Body.Close() }()
	body, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		return nil, 0, err
	}
	return body, resp.StatusCode, nil
}

func parseToken(body []byte) (TokenResponse, error) {
	var token TokenResponse
	if err := json.Unmarshal(body, &token); err != nil {
		return TokenResponse{}, fmt.Errorf("parse the token response: %w", err)
	}
	if token.AccessToken == "" {
		return TokenResponse{}, fmt.Errorf("the token response holds no access token")
	}
	return token, nil
}

func sleepContext(ctx context.Context, d time.Duration) error {
	timer := time.NewTimer(d)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		return nil
	}
}
