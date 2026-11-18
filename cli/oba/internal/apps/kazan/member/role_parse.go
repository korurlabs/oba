package member

import (
	"fmt"
	"slices"
	"strings"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/board"
)

// ParseRole returns the board role value names, case ignored, so that a typo fails before the
// request instead of as a 400.
func ParseRole(value string) (string, error) {
	role := strings.ToUpper(strings.TrimSpace(value))
	if role == "" {
		return "", fmt.Errorf("--role is required: use one of %s", strings.Join(board.MemberRoles, ", "))
	}
	if !slices.Contains(board.MemberRoles, role) {
		return "", fmt.Errorf("invalid role %q: use one of %s", strings.TrimSpace(value), strings.Join(board.MemberRoles, ", "))
	}
	return role, nil
}
