// Package member lists the members of the current organization, the people who accepted an
// invitation, changes a member's role and removes a member. A member is addressed by user id or
// e-mail, resolved against the member list.
package member

import "github.com/korurlabs/oba-cli/internal/dojo/guardrail"

const emptyCell = "-"

// cell is value on one line with every terminal control removed, or "-" when nothing is left:
// names and e-mails are typed by users, and a newline or escape sequence must not forge a row
// or drive the terminal.
func cell(value string) string {
	clean := guardrail.OneLine(value)
	if clean == "" {
		return emptyCell
	}
	return clean
}
