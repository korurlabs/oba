// Package member builds the oba ulak member commands: list the members of the current
// organization, change a member's role and remove a member.
package member

import (
	"bufio"
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/ulak/member"
	"github.com/korurlabs/oba-cli/internal/apps/ulak/role"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const (
	memberListHelp = `Lists the members of the current organization, the people who accepted an invitation, in one
table: NAME, EMAIL, ROLE, STATUS, USER ID. Owners come first, then ADMIN, MEMBER and GUEST, each
by e-mail. Any member may list them. People invited but not yet accepted are in
'oba ulak invite list'.`
	memberRoleHelp = `Gives a member of the current organization another role: ADMIN, MEMBER or GUEST, case ignored.
The member is named by user id or by e-mail, as 'oba ulak member list' shows them. OWNER is
never assigned. Only an admin or owner may change a role; nobody changes their own, and an
admin never changes an owner's.

  oba ulak member role ana@example.com ADMIN`
	memberRemoveHelp = `Removes a member from the current organization after asking for confirmation. The member is
named by user id or by e-mail, as 'oba ulak member list' shows them. Without a terminal to ask
on, pass --yes. Only an admin or owner may remove a member, and an admin never removes an owner.`
)

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

// NewCmd builds the oba ulak member command with list, role and remove.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "member",
		Short: "List the members of the organization, change a member's role, remove a member",
	}

	listCmd := &cobra.Command{
		Use:   "list",
		Short: "List the members of the organization with their roles",
		Long:  memberListHelp,
		Args:  cobra.NoArgs,
		RunE:  runMemberList,
	}

	roleCmd := &cobra.Command{
		Use:   "role <email|userId> <ROLE>",
		Short: "Change a member's role to ADMIN, MEMBER or GUEST; needs the ADMIN role",
		Long:  memberRoleHelp,
		Args:  cobra.ExactArgs(2),
		RunE:  runMemberRole,
	}

	var removeYes bool
	removeCmd := &cobra.Command{
		Use:   "remove <email|userId>",
		Short: "Remove a member from the organization; needs the ADMIN role",
		Long:  memberRemoveHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runMemberRemove(cmd, args, removeYes)
		},
	}
	removeCmd.Flags().BoolVarP(&removeYes, "yes", "y", false, "remove without asking for confirmation")

	cmd.AddCommand(listCmd, roleCmd, removeCmd)
	return cmd
}

func runMemberList(cmd *cobra.Command, _ []string) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	members, err := member.List(c)
	if err != nil {
		return err
	}
	return member.PrintList(cmd.OutOrStdout(), members)
}

// runMemberRole refuses OWNER and unknown roles before any call.
func runMemberRole(cmd *cobra.Command, args []string) error {
	newRole, err := role.ParseRole(args[1])
	if err != nil {
		return err
	}
	c, orgName, target, err := resolve(args[0])
	if err != nil {
		return err
	}
	if _, err := member.ChangeRole(c, target.UserID, newRole); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "%s is now %s in %s\n", guardrail.OneLine(target.Email), newRole, orgName)
	return nil
}

func runMemberRemove(cmd *cobra.Command, args []string, yes bool) error {
	if !yes && !stdinIsTerminal() {
		return member.ErrRemoveNeedsYes
	}
	c, orgName, target, err := resolve(args[0])
	if err != nil {
		return err
	}
	email := guardrail.OneLine(target.Email)
	out := cmd.OutOrStdout()
	if !yes {
		src := cmd.InOrStdin()
		question := fmt.Sprintf("Remove %s from %s?", email, orgName)
		confirmed, err := terminal.Confirm(bufio.NewReader(src), src, out, question)
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
	if err := member.Remove(c, target.UserID); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Removed %s from %s\n", email, orgName)
	return nil
}

// resolve returns a client in the current organization, the organization's name on one line
// for the messages, and the member value names.
func resolve(value string) (*client.Client, string, client.Member, error) {
	c, err := session.OrganizationClient()
	if err != nil {
		return nil, "", client.Member{}, err
	}
	name, err := session.OrganizationName(c.OrgID)
	if err != nil {
		return nil, "", client.Member{}, err
	}
	orgName := guardrail.OneLine(name)
	target, err := member.Resolve(c, orgName, value)
	if err != nil {
		return nil, "", client.Member{}, err
	}
	return c, orgName, target, nil
}
