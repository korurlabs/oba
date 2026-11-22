// Package shrine holds the platform commands of oba: login, logout and org, which sign a user in
// and choose the organization, and legal-entity and document, which keep the organization-wide
// records apps share. Each lives in its own file.
package shrine

import "github.com/spf13/cobra"

// Commands builds the platform commands for the oba root.
func Commands() []*cobra.Command {
	return []*cobra.Command{newLoginCmd(), newLogoutCmd(), newOrgCmd(), newLegalEntityCmd(), newDocumentCmd()}
}
