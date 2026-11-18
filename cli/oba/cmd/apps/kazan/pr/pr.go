// Package pr builds the oba kazan pr commands: share a GitHub pull request in the
// organization's PR channel, list and read the shares, edit or delete your own, approve the ones
// you review, and list the repositories and pull requests Kazan's GitHub App can read.
package pr

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/pr"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

// NewCmd builds the oba kazan pr command with its subcommands.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:     "pr",
		Aliases: []string{"pull-requests"},
		Short:   "Share pull requests in the PR channel, show, edit and approve them",
		Long: `Share a GitHub pull request in your organization's PR channel, list and read
the shares, edit or delete your own, and approve the ones you review.

A share carries the team's review status (Draft, Open, In Review, Merged,
Discuss), an optional note, up to 10 reviewers and up to 10 related tickets.
The status is the team's, chosen by the author; it is preset from GitHub's
state but never follows it. GitHub is read through Kazan's GitHub App: the CLI
needs no GitHub login, and lists only the repositories that App can read for
the organization.

Only the author edits or deletes a share; only its reviewers approve it. The
organization is --org, else the current organization; --board plays no part.
With --json every command prints the data the API returned.

Exit codes: 1 for a refused value, a validation error or GitHub being
unreachable; 2 when the share, repository, pull request or a related ticket is
not found, or GitHub is not set up for the organization; 3 when you may not do
it.`,
	}
	cmd.AddCommand(
		newReposCmd(), newPullsCmd(), newShareCmd(), newPreviewCmd(), newListCmd(), newGetCmd(),
		newEditCmd(), newDeleteCmd(), newApproveCmd(), newWithdrawCmd(),
	)
	return cmd
}

// interactive reports whether the command may ask a question: stdin is a terminal and the
// output is not --json.
func interactive() bool {
	return stdinIsTerminal() && !jsonout.Enabled()
}

// prompter returns the prompter of an interactive run.
func prompter(cmd *cobra.Command) *pr.Prompter {
	return pr.NewPrompter(cmd.InOrStdin(), cmd.OutOrStdout())
}

// meFunc returns a pr.MeFunc that reads the signed-in user's id on its first call only. The
// read comes before the call whose data is the --json result.
func meFunc() pr.MeFunc {
	var id string
	return func() (string, error) {
		if id != "" {
			return id, nil
		}
		read, err := session.CurrentUserID()
		if err != nil {
			return "", err
		}
		id = read
		return id, nil
	}
}

// statusFlag reads --status when it was given, "" when not.
func statusFlag(cmd *cobra.Command, value string) (string, error) {
	if !cmd.Flags().Changed("status") {
		return "", nil
	}
	status, err := pr.ParseStatus(value)
	if err != nil {
		return "", fmt.Errorf("--status: %w", err)
	}
	return status, nil
}

// noteFlag reads --note, trimmed: nil when it was not given or is blank, since a blank note is
// none.
func noteFlag(cmd *cobra.Command, value string) (*string, error) {
	value = strings.TrimSpace(value)
	if !cmd.Flags().Changed("note") || value == "" {
		return nil, nil
	}
	if err := pr.CheckNote(&value); err != nil {
		return nil, err
	}
	return &value, nil
}

// cancelled ends a run the user left at a question: no change, no error, a null result.
func cancelled(cmd *cobra.Command) error {
	jsonresult.Nothing()
	_, _ = fmt.Fprintln(cmd.OutOrStdout(), "Cancelled.")
	return nil
}
