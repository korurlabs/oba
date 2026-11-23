// Package role checks the organization role a user gives to an Ulak command before anything is
// sent, so that invite create and member role refuse the same values with the same message.
package role

import (
	"fmt"
	"strings"
)

// assignable are the roles an admin may give. OWNER is left out: the API never assigns it, so
// the CLI refuses it before a call instead of letting the API answer.
var assignable = []string{"ADMIN", "MEMBER", "GUEST"}

// ParseRole returns given as one of ADMIN, MEMBER and GUEST, case and surrounding spaces
// ignored, or an error naming the allowed roles.
func ParseRole(given string) (string, error) {
	upper := strings.ToUpper(strings.TrimSpace(given))
	for _, r := range assignable {
		if upper == r {
			return r, nil
		}
	}
	return "", fmt.Errorf("unknown role %q; allowed: %s", given, strings.Join(assignable, ", "))
}
