package client

import (
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"time"

	"github.com/korurlabs/oba-cli/internal/dojo/httplog"
)

const (
	downloadTimeout = 2 * time.Minute

	// MaxDownloadBytes bounds one downloaded file. The API takes at most 12 MB per upload.
	MaxDownloadBytes = 32 * 1024 * 1024
)

// downloadClient fetches signed blob URLs. It is not the API client: a signed URL carries its
// own read grant, and the bearer token must never reach the storage host.
var downloadClient = &http.Client{Timeout: downloadTimeout, Transport: httplog.Transport}

// Download reads the file at a signed URL the API returned, such as a template's htmlUrl or
// pdfUrl, without any credential of the session.
func Download(signedURL string) ([]byte, error) {
	host := signedURL
	if parsed, err := url.Parse(signedURL); err == nil {
		// The query holds the signature; it is left out of every message.
		host = parsed.Scheme + "://" + parsed.Host + parsed.Path
	}
	resp, err := downloadClient.Get(signedURL)
	if err != nil {
		return nil, fmt.Errorf("failed to download %s: %w", host, redactURLError(err))
	}
	defer func() { _ = resp.Body.Close() }()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("failed to download %s: %s", host, resp.Status)
	}
	data, err := io.ReadAll(io.LimitReader(resp.Body, MaxDownloadBytes+1))
	if err != nil {
		return nil, fmt.Errorf("failed to read %s: %w", host, err)
	}
	if len(data) > MaxDownloadBytes {
		return nil, fmt.Errorf("%s is larger than %d bytes", host, MaxDownloadBytes)
	}
	return data, nil
}

// redactURLError drops the URL, and with it the signature, from a transport error.
func redactURLError(err error) error {
	var urlErr *url.Error
	if errors.As(err, &urlErr) {
		return urlErr.Err
	}
	return err
}
