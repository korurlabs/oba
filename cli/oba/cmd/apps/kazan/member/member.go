// Package member builds the oba kazan member commands: list the members of a board, add and
// remove members, and change a member's role and specialties.
package member

import (
	"bufio"
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/kazan/boardflag"
	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/board"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/member"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const (
	memberUserHelp = "A member is named by email or user id; an email is looked up among the members of the " +
		"organization ('oba admin member list')."
	memberRemoveHelp = `Removes a member from the board. On a terminal it asks for confirmation first, unless --yes
is given; without a terminal, or with --json, it removes without asking.`
)

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

// NewCmd builds the oba kazan member command with list, add, remove, update and specialties.
// Every subcommand acts on the board of --board, else the default board.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "member",
		Short: "List, add and remove the members of a board, and change their role and specialties",
		Long: "The board is --board, else the default board chosen with 'oba kazan board use'. " + memberUserHelp +
			"\n\nRoles: " + strings.Join(board.MemberRoles, ", ") + ".",
	}
	listCmd := &cobra.Command{
		Use:   "list",
		Short: "List the members of the board",
		Args:  cobra.NoArgs,
		RunE:  runList,
	}

	var in member.AddInput
	var userIDAlias string
	addCmd := &cobra.Command{
		Use:   "add",
		Short: "Add a member to the board",
		Long:  "Adds a user to the board with a role.\n\n" + memberUserHelp + "\n\n  oba kazan member add --user ana@example.com --role MEMBER",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			if strings.TrimSpace(in.User) == "" {
				in.User = userIDAlias
			}
			return runAdd(cmd, in)
		},
	}
	addCmd.Flags().StringVar(&in.User, "user", "", "email or user id of the user to add (required)")
	addCmd.Flags().StringVar(&userIDAlias, "user-id", "", "alias of --user")
	addCmd.Flags().Lookup("user-id").Hidden = true
	addCmd.Flags().StringVar(&in.Role, "role", "", "member role: "+strings.Join(board.MemberRoles, ", ")+" (required)")

	var yes bool
	removeCmd := &cobra.Command{
		Use:   "remove <email|userId>",
		Short: "Remove a member from the board",
		Long:  memberRemoveHelp + "\n\n" + memberUserHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runRemove(cmd, args[0], yes)
		},
	}
	removeCmd.Flags().BoolVarP(&yes, "yes", "y", false, "remove without asking for confirmation")

	var role string
	updateCmd := &cobra.Command{
		Use:   "update <email|userId>",
		Short: "Change a member's role",
		Long:  "Gives a member of the board another role.\n\n" + memberUserHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runUpdate(cmd, args[0], role)
		},
	}
	updateCmd.Flags().StringVar(&role, "role", "", "new role: "+strings.Join(board.MemberRoles, ", ")+" (required)")

	var specialties string
	specialtiesCmd := &cobra.Command{
		Use:   "specialties <email|userId>",
		Short: "Set a member's specialties",
		Long: "Replaces the specialties of a member of the board with the comma-separated list, case ignored.\n\n" +
			memberUserHelp + "\n\n  oba kazan member specialties ana@example.com --specialties developer,architect",
		Args: cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runSpecialties(cmd, args[0], specialties)
		},
	}
	specialtiesCmd.Flags().StringVar(&specialties, "specialties", "",
		"comma-separated specialties: "+strings.Join(member.Specialties, ", ")+" (required)")

	cmd.AddCommand(listCmd, addCmd, removeCmd, updateCmd, specialtiesCmd)
	return cmd
}

func runList(cmd *cobra.Command, _ []string) error {
	c, boardID, err := selectBoard(cmd)
	if err != nil {
		return err
	}
	members, err := member.List(c, boardID)
	if err != nil {
		return err
	}
	return member.PrintList(cmd.OutOrStdout(), members)
}

func runAdd(cmd *cobra.Command, in member.AddInput) error {
	c, boardID, err := selectBoard(cmd)
	if err != nil {
		return err
	}
	added, err := member.Add(c, boardID, in)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Member %s added with role %s.\n", added.UserID, added.Role)
	return nil
}

func runRemove(cmd *cobra.Command, userRef string, yes bool) error {
	c, boardID, err := selectBoard(cmd)
	if err != nil {
		return err
	}
	userID, err := member.ResolveUser(c, userRef)
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	if !yes && stdinIsTerminal() && !jsonout.Enabled() {
		src := cmd.InOrStdin()
		confirmed, err := terminal.Confirm(bufio.NewReader(src), src, out, fmt.Sprintf("Remove member %s?", userRef))
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
	if err := member.Remove(c, boardID, userID); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Member %s removed.\n", userRef)
	return nil
}

func runUpdate(cmd *cobra.Command, userRef, role string) error {
	c, boardID, err := selectBoard(cmd)
	if err != nil {
		return err
	}
	updated, err := member.UpdateRole(c, boardID, userRef, role)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Member %s role updated to %s.\n", userRef, updated.Role)
	return nil
}

func runSpecialties(cmd *cobra.Command, userRef, specialties string) error {
	c, boardID, err := selectBoard(cmd)
	if err != nil {
		return err
	}
	if err := member.UpdateSpecialties(c, boardID, userRef, specialties); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Specialties updated for member %s.\n", userRef)
	return nil
}

// selectBoard returns a client in the current organization and the id of the board the command
// acts on.
func selectBoard(cmd *cobra.Command) (*client.Client, string, error) {
	c, err := session.OrganizationClient()
	if err != nil {
		return nil, "", err
	}
	ref, err := boardflag.Selected(cmd, c)
	if err != nil {
		return nil, "", err
	}
	return c, ref.ID, nil
}
