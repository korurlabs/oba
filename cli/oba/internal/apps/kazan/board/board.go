// Package board lists, reads, creates, changes, deletes and restores the boards of Kazan,
// resolves the board a user names (by id, key prefix or name) and keeps the default board of
// each organization. The other Kazan domains resolve boards through this package.
package board

import "errors"

// ErrNoBoard is printed as it is, so it keeps the capital and the full stop.
var ErrNoBoard = errors.New("No board selected. Run 'oba kazan board use <board>'.") //nolint:staticcheck // ST1005: shown to the user verbatim

// MemberRoles are the values of the API's BoardMemberRole.
var MemberRoles = []string{"OWNER", "ADMIN", "MEMBER", "VIEWER"}

// emptyCell stands for an empty value in a table.
const emptyCell = "-"
