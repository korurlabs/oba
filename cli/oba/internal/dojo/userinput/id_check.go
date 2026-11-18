package userinput

import (
	"regexp"
	"strings"
)

// idPattern matches the UUID every Oba record is named by.
var idPattern = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)

// IsID reports whether ref is a record id, which is used as it is, rather than a name, key or
// e-mail that needs a lookup.
func IsID(ref string) bool {
	return idPattern.MatchString(strings.TrimSpace(ref))
}
