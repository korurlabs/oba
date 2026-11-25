package client

import (
	"fmt"
	"strings"
)

// KalkanLocale turns tr or en, in any case, into the API's locale name.
func KalkanLocale(lang string) (string, error) {
	switch strings.ToUpper(strings.TrimSpace(lang)) {
	case KalkanLocaleTR:
		return KalkanLocaleTR, nil
	case KalkanLocaleEN:
		return KalkanLocaleEN, nil
	default:
		return "", fmt.Errorf("unknown language %q; use tr or en", lang)
	}
}
