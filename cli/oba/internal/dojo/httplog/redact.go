package httplog

import (
	"fmt"
	"io"
	"net/http"
	"net/url"
	"sort"
	"strings"
)

const (
	redacted = "<redacted>"
	// redactedQuery replaces a query value; it has no characters that a URL would escape.
	redactedQuery = "REDACTED"
)

// secretHeaders carry a credential: a bearer token, a session cookie.
var secretHeaders = map[string]bool{
	"Authorization":       true,
	"Proxy-Authorization": true,
	"Cookie":              true,
	"Set-Cookie":          true,
}

// secretQueryKeys carry a credential in a URL: the signature of a signed blob URL, or a token.
var secretQueryKeys = map[string]bool{
	"sig":           true,
	"signature":     true,
	"token":         true,
	"access_token":  true,
	"refresh_token": true,
	"code":          true,
	"client_secret": true,
	"password":      true,
}

// writeHeaders writes one "<prefix> Name: value" line per header, sorted by name. A credential
// keeps its scheme ("Bearer") so that the log still shows which kind was sent.
func writeHeaders(w io.Writer, prefix string, header http.Header) {
	names := make([]string, 0, len(header))
	for name := range header {
		names = append(names, name)
	}
	sort.Strings(names)
	for _, name := range names {
		for _, value := range header[name] {
			if secretHeaders[http.CanonicalHeaderKey(name)] {
				value = redactCredential(value)
			}
			_, _ = fmt.Fprintf(w, "%s %s: %s\n", prefix, name, value)
		}
	}
}

func redactCredential(value string) string {
	if scheme, _, found := strings.Cut(value, " "); found {
		return scheme + " " + redacted
	}
	return redacted
}

// redactURL returns u as text with the value of every credential query parameter replaced.
func redactURL(u *url.URL) string {
	if u.RawQuery == "" {
		return u.String()
	}
	query := u.Query()
	changed := false
	for key := range query {
		if secretQueryKeys[strings.ToLower(key)] {
			query[key] = []string{redactedQuery}
			changed = true
		}
	}
	if !changed {
		return u.String()
	}
	clone := *u
	clone.RawQuery = query.Encode()
	return clone.String()
}
