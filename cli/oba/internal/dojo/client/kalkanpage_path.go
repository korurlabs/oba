package client

import (
	"errors"
	"fmt"
	"regexp"
	"strings"
)

// KalkanMaxPageDepth is the deepest page path the API accepts, in slugs.
const KalkanMaxPageDepth = 10

// kalkanSlugPattern is the API's slug rule.
var kalkanSlugPattern = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$`)

// ValidKalkanSlug reports whether s is a slug the API accepts.
func ValidKalkanSlug(s string) bool {
	return kalkanSlugPattern.MatchString(s)
}

// NormalizeKalkanPagePath trims surrounding spaces and slashes and checks every segment against
// the API's slug rule, before any call, so that a typo fails at once with exit status 1 instead
// of as a 422 or a 404 that reads like a denial.
func NormalizeKalkanPagePath(raw string) (string, error) {
	path := strings.Trim(strings.TrimSpace(raw), "/")
	if path == "" {
		return "", errors.New("the path is empty; name a page as root/slug/slug")
	}
	segments := strings.Split(path, "/")
	if len(segments) > KalkanMaxPageDepth {
		return "", fmt.Errorf("the path %q is deeper than %d pages", path, KalkanMaxPageDepth)
	}
	for _, segment := range segments {
		if !ValidKalkanSlug(segment) {
			return "", fmt.Errorf("%q in %q is not a valid slug: start with a letter or digit, then up to 99 letters, digits, '.', '_' or '-'", segment, path)
		}
	}
	return path, nil
}
