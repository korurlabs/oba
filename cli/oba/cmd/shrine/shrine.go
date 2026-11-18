// Package shrine holds the platform commands of oba that sign a user in and choose the
// organization: login, logout and org. Each lives in its own file.
package shrine

import "github.com/spf13/cobra"

// Commands builds the platform commands for the oba root.
func Commands() []*cobra.Command {
	return []*cobra.Command{newLoginCmd(), newLogoutCmd(), newOrgCmd()}
}
