package board

import (
	"fmt"
	"slices"
	"strings"
)

// MemberSpec is one initial member of a new board: an email or user id, and a role.
type MemberSpec struct {
	User string
	Role string
}

// ParseMemberSpecs reads each "<email or userId>:<ROLE>" value, split at its last colon. The
// role is checked against MemberRoles, case ignored, so that a typo fails before any call.
func ParseMemberSpecs(values []string) ([]MemberSpec, error) {
	specs := make([]MemberSpec, 0, len(values))
	for _, value := range values {
		trimmed := strings.TrimSpace(value)
		sep := strings.LastIndex(trimmed, ":")
		if sep <= 0 || sep == len(trimmed)-1 {
			return nil, fmt.Errorf("invalid --member %q: use <email or userId>:<ROLE>", value)
		}
		ref := strings.TrimSpace(trimmed[:sep])
		role := strings.ToUpper(strings.TrimSpace(trimmed[sep+1:]))
		if ref == "" || !slices.Contains(MemberRoles, role) {
			return nil, fmt.Errorf("invalid --member %q: use <email or userId>:<ROLE> with ROLE one of %s",
				value, strings.Join(MemberRoles, ", "))
		}
		specs = append(specs, MemberSpec{User: ref, Role: role})
	}
	return specs, nil
}
