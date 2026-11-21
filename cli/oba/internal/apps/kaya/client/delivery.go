package client

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
)

// MaxBlobBytes is the largest delivered file; the server stores nothing bigger as one blob.
const MaxBlobBytes = 5 << 20

// DeliveryCurrent is the answer of GET /kaya/delivery/current. ReleaseID, Number and ManifestSHA256
// are empty while the repository has no release.
type DeliveryCurrent struct {
	RepositoryID   string `json:"repositoryId"`
	ReleaseID      string `json:"releaseId"`
	Number         int    `json:"number"`
	ManifestSHA256 string `json:"manifestSha256"`
}

// DeliveryRelease is a signed release. Manifest is the signed canonical JSON as a string: it is
// verified byte for byte before it is parsed.
type DeliveryRelease struct {
	ReleaseID      string `json:"releaseId"`
	RepositoryID   string `json:"repositoryId"`
	Number         int    `json:"number"`
	Manifest       string `json:"manifest"`
	ManifestSHA256 string `json:"manifestSha256"`
	Signature      string `json:"signature"`
	KeyID          string `json:"keyId"`
}

// CurrentRelease asks for the current release of the checkout whose remote is remoteURL. With
// an etag from an earlier answer, a 304 answers notModified and no body.
func (c *Client) CurrentRelease(ctx context.Context, remoteURL, etag string) (current DeliveryCurrent, newETag string, notModified bool, err error) {
	header := http.Header{}
	if etag != "" {
		header.Set("If-None-Match", etag)
	}
	resp, raw, err := c.raw(ctx, "/kaya/delivery/current?remoteUrl="+url.QueryEscape(remoteURL), header, maxResponseBytes)
	if err != nil {
		return DeliveryCurrent{}, "", false, err
	}
	if resp.StatusCode == http.StatusNotModified {
		return DeliveryCurrent{}, etag, true, nil
	}
	if err := decodeData(resp, raw, "/kaya/delivery/current", &current); err != nil {
		return DeliveryCurrent{}, "", false, err
	}
	return current, resp.Header.Get("ETag"), false, nil
}

// ReleaseByID reads a signed release.
func (c *Client) ReleaseByID(ctx context.Context, releaseID string) (DeliveryRelease, error) {
	path := "/kaya/delivery/releases/" + url.PathEscape(releaseID)
	resp, raw, err := c.raw(ctx, path, nil, maxResponseBytes)
	if err != nil {
		return DeliveryRelease{}, err
	}
	var release DeliveryRelease
	return release, decodeData(resp, raw, path, &release)
}

// ReleaseByNumber reads a signed release of the checkout's repository by its number.
func (c *Client) ReleaseByNumber(ctx context.Context, remoteURL string, number int) (DeliveryRelease, error) {
	path := fmt.Sprintf("/kaya/delivery/releases?remoteUrl=%s&number=%d", url.QueryEscape(remoteURL), number)
	resp, raw, err := c.raw(ctx, path, nil, maxResponseBytes)
	if err != nil {
		return DeliveryRelease{}, err
	}
	var release DeliveryRelease
	return release, decodeData(resp, raw, "/kaya/delivery/releases", &release)
}

// Blob reads the raw content stored under sha256. The caller checks the hash.
func (c *Client) Blob(ctx context.Context, sha256 string) ([]byte, error) {
	path := "/kaya/delivery/blobs/" + url.PathEscape(sha256)
	resp, raw, err := c.raw(ctx, path, nil, MaxBlobBytes+1)
	if err != nil {
		return nil, err
	}
	if resp.StatusCode != http.StatusOK {
		return nil, decodeData(resp, raw, path, nil)
	}
	if len(raw) > MaxBlobBytes {
		return nil, fmt.Errorf("the blob %s is larger than %d bytes", sha256, MaxBlobBytes)
	}
	return raw, nil
}

// raw sends a GET and returns the status, headers and at most limit bytes of the body, for the
// answers that are not always the JSON envelope (a 304, raw blob bytes).
func (c *Client) raw(ctx context.Context, path string, header http.Header, limit int64) (*http.Response, []byte, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.baseURL+path, nil)
	if err != nil {
		return nil, nil, fmt.Errorf("build the request to %s: %w", path, err)
	}
	token, err := c.token(ctx)
	if err != nil {
		return nil, nil, err
	}
	for key, values := range header {
		for _, value := range values {
			req.Header.Add(key, value)
		}
	}
	req.Header.Set("Authorization", "Bearer "+token)
	resp, err := c.http.Do(req)
	if err != nil {
		return nil, nil, fmt.Errorf("GET %s: %w", c.baseURL+path, err)
	}
	defer func() { _ = resp.Body.Close() }()
	raw, err := io.ReadAll(io.LimitReader(resp.Body, limit))
	if err != nil {
		return nil, nil, fmt.Errorf("read the answer of GET %s: %w", path, err)
	}
	return resp, raw, nil
}

// decodeData turns a non-2xx answer into an APIError and decodes the envelope's data into out.
func decodeData(resp *http.Response, raw []byte, path string, out any) error {
	var env envelope
	decodeErr := json.Unmarshal(raw, &env)
	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		apiErr := &APIError{Status: resp.StatusCode, Message: strings.TrimSpace(string(raw))}
		if decodeErr == nil && env.Error != nil {
			apiErr.Code = env.Error.Code
			apiErr.Message = env.Error.Message
			apiErr.ErrorValues = env.Error.ErrorValues
		}
		return apiErr
	}
	if decodeErr != nil {
		return fmt.Errorf("parse the answer of GET %s: %w", path, decodeErr)
	}
	if out == nil || len(env.Data) == 0 || string(env.Data) == "null" {
		return nil
	}
	if err := json.Unmarshal(env.Data, out); err != nil {
		return fmt.Errorf("parse the data of GET %s: %w", path, err)
	}
	return nil
}

// SigningKey is a public key Kaya signs releases with, or still trusts during a key rotation.
type SigningKey struct {
	KeyID     string  `json:"keyId"`
	PublicKey string  `json:"publicKey"`
	ValidFrom *string `json:"validFrom"`
	ValidTo   *string `json:"validTo"`
}

// SigningKeys lists the release signing keys; a user call (JWT and organization).
func (c *Client) SigningKeys(ctx context.Context) ([]SigningKey, error) {
	var keys []SigningKey
	return keys, c.doJSON(ctx, http.MethodGet, "/kaya/signing-keys", nil, &keys)
}
