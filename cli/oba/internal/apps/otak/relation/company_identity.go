package relation

import (
	"regexp"
	"strings"
)

// websitePattern is the API's rule for a website, which a contact's source URL shares.
var websitePattern = regexp.MustCompile(`^https?://[^\s/?#]+\.[^\s]+$`)

// ValidWebsite reports whether value passes the API's rule for a website.
func ValidWebsite(value string) bool {
	return websitePattern.MatchString(value)
}

// NormalizeName lower-cases a name and collapses its whitespace, so that "Acme  B.V." and
// "acme b.v." are the same name.
func NormalizeName(name string) string {
	return strings.Join(strings.Fields(strings.ToLower(name)), " ")
}

// WebsiteHost reduces a website to its lower-cased host: no scheme, no "www.", no path, query or
// fragment. "https://www.Acme.com/about" and "acme.com" give the same host.
func WebsiteHost(website string) string {
	host := strings.ToLower(strings.TrimSpace(website))
	if i := strings.Index(host, "://"); i >= 0 {
		host = host[i+len("://"):]
	}
	if i := strings.IndexAny(host, "/?#"); i >= 0 {
		host = host[:i]
	}
	return strings.TrimPrefix(host, "www.")
}
