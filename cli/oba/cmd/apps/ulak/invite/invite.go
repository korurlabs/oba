// Package invite builds the oba ulak invite commands: invite a person to the current
// organization, list the pending invitations and revoke one.
package invite

import (
	"bufio"
	"fmt"
	"strings"
	"time"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/ulak/invite"
	"github.com/korurlabs/oba-cli/internal/apps/ulak/role"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const (
	defaultRole = "MEMBER"

	inviteCreateHelp = `Invites a person by e-mail to the current organization with a role: ADMIN, MEMBER or GUEST
(default MEMBER, case ignored). OWNER is never given. The same call serves a person who already
has an Oba account and one who has none: they get an e-mail and join when they accept in the
account app. Until then they show in 'oba ulak invite list', not in 'oba ulak member list'.
The invitation expires after 7 days. Only an admin or owner of the organization may invite.

  oba ulak invite create ayse@example.com --role admin`
	inviteRevokeHelp = `Revokes a pending invitation after asking for confirmation, so the link in its e-mail stops
working. Without a terminal to ask on, pass --yes. Find the id with 'oba ulak invite list'.`
)

// stdinIsTerminal and localZone are variables so that tests can stand in for a terminal and a
// time zone.
var (
	stdinIsTerminal = terminal.StdinIsTerminal
	localZone       = func() *time.Location { return time.Local }
)

// NewCmd builds the oba ulak invite command with its subcommands.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "invite",
		Short: "Invite people to the organization, list pending invitations and revoke them",
	}

	var createRole string
	createCmd := &cobra.Command{
		Use:   "create <email>",
		Short: "Invite a person by e-mail; they join when they accept",
		Long:  inviteCreateHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runInviteCreate(cmd, args, createRole)
		},
	}
	createCmd.Flags().StringVar(&createRole, "role", defaultRole, "the role on joining: ADMIN, MEMBER or GUEST")

	listCmd := &cobra.Command{
		Use:   "list",
		Short: "List the pending invitations of the organization",
		Long: "Lists the invitations of the current organization that nobody has accepted, declined or revoked yet: " +
			"EMAIL, ROLE, INVITED BY, EXPIRES (your calendar day), ID. Only an admin or owner may list them.",
		Args: cobra.NoArgs,
		RunE: runInviteList,
	}

	var revokeYes bool
	revokeCmd := &cobra.Command{
		Use:   "revoke <id>",
		Short: "Revoke a pending invitation; needs the ADMIN role",
		Long:  inviteRevokeHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runInviteRevoke(cmd, args, revokeYes)
		},
	}
	revokeCmd.Flags().BoolVarP(&revokeYes, "yes", "y", false, "revoke without asking for confirmation")

	cmd.AddCommand(createCmd, listCmd, revokeCmd)
	return cmd
}

func runInviteCreate(cmd *cobra.Command, args []string, givenRole string) error {
	r, err := role.ParseRole(givenRole)
	if err != nil {
		return err
	}
	email := strings.TrimSpace(args[0])
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	// The name is read before the invitation is sent, so that a failed read leaves nothing sent.
	orgName, err := session.OrganizationName(c.OrgID)
	if err != nil {
		return err
	}
	created, err := invite.Create(c, email, r)
	if err != nil {
		return err
	}
	invite.PrintCreated(cmd.OutOrStdout(), created, orgName, localZone())
	return nil
}

func runInviteList(cmd *cobra.Command, _ []string) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	invs, err := invite.List(c)
	if err != nil {
		return err
	}
	return invite.PrintList(cmd.OutOrStdout(), invs, localZone())
}

func runInviteRevoke(cmd *cobra.Command, args []string, yes bool) error {
	id := strings.TrimSpace(args[0])
	if !yes && !stdinIsTerminal() {
		return invite.ErrRevokeNeedsYes
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	if !yes {
		confirmed, err := confirmRevoke(cmd, c, id)
		if err != nil {
			return err
		}
		if !confirmed {
			// The lookups before the question are not the result of a cancelled run.
			jsonresult.Nothing()
			_, _ = fmt.Fprintln(out, "Cancelled.")
			return nil
		}
	}
	if err := invite.Revoke(c, id); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Revoked invitation %s\n", guardrail.OneLine(id))
	return nil
}

// confirmRevoke asks whether to revoke the invitation id, naming its e-mail. An id that is not
// among the pending invitations is not asked about: the API refuses to revoke it, so the call
// goes ahead and its 404 or 422 is what the user sees.
func confirmRevoke(cmd *cobra.Command, c *client.Client, id string) (bool, error) {
	invs, err := invite.List(c)
	if err != nil {
		return false, err
	}
	inv, ok := invite.Find(invs, id)
	if !ok {
		return true, nil
	}
	src := cmd.InOrStdin()
	question := fmt.Sprintf("Revoke the invitation of %s?", guardrail.OneLine(inv.Email))
	return terminal.Confirm(bufio.NewReader(src), src, cmd.OutOrStdout(), question)
}
