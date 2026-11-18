// Package board builds the oba kazan board commands: list, read, create, change, delete and
// restore boards, add child boards, list related boards and choose the default board.
package board

import (
	"bufio"
	"errors"
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/board"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const (
	boardRefHelp = "A board is named by its name, its key prefix or its id, case ignored. A name or prefix that " +
		"matches two boards is refused; use the id then."
	boardDeleteHelp = `Deletes a board. When the board has tickets, --tickets-to says where they go: backlog or sprint
of --target-board (with --target-sprint for sprint), or delete. When it has child boards,
--children says what happens to them: promote (they become top-level), move (under --new-parent)
or delete. Deleting tickets or child boards needs --yes. A deleted board can be restored with
'oba kazan board restore <id>'.

  oba kazan board delete OLD --tickets-to backlog --target-board NEW
  oba kazan board delete OLD --tickets-to sprint --target-board NEW --target-sprint sprint --children promote`
	boardUseHelp = `Makes a board the default board of the current organization: the board the board-scoped
commands act on without --board. Each organization keeps its own default.

Without an argument on a terminal, the boards are listed and you pick one by number.

  oba kazan board use OPS`
)

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

// NewCmd builds the oba kazan board command with its subcommands.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "board",
		Short: "List, read, create, change, delete and restore boards, and choose the default board",
		Long:  "Boards hold the tickets and sprints of Kazan and form a tree of parent and child boards.\n\n" + boardRefHelp,
	}

	var search string
	listCmd := &cobra.Command{
		Use:   "list",
		Short: "List your boards as a tree",
		Long: "Lists the boards of the current organization you are a member of, child boards under their parent.\n\n" +
			"--search keeps the boards whose name or key prefix contains the text, case ignored, with their parents.",
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runList(cmd, search)
		},
	}
	listCmd.Flags().StringVar(&search, "search", "", "show only boards whose name or key prefix contains this text")

	getCmd := &cobra.Command{
		Use:   "get <board>",
		Short: "Show a board with its parent and children",
		Long:  "Shows a board: name, id, key, slug, description, its parent and its child boards.\n\n" + boardRefHelp,
		Args:  cobra.ExactArgs(1),
		RunE:  runGet,
	}

	createCmd := newCreateCmd()
	updateCmd := newUpdateCmd()
	deleteCmd := newDeleteCmd()

	restoreCmd := &cobra.Command{
		Use:   "restore <board>",
		Short: "Restore a deleted board",
		Long:  "Restores a deleted board. A deleted board is no longer listed, so name it by its id.",
		Args:  cobra.ExactArgs(1),
		RunE:  runRestore,
	}

	childrenCmd := &cobra.Command{
		Use:   "children",
		Short: "Manage the child boards of a board",
	}
	var childIDs string
	childrenAddCmd := &cobra.Command{
		Use:   "add <board>",
		Short: "Make boards children of a board",
		Long:  "Makes the boards --child-ids names, comma separated, children of the board.\n\n" + boardRefHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runChildrenAdd(cmd, args, childIDs)
		},
	}
	childrenAddCmd.Flags().StringVar(&childIDs, "child-ids", "", "the child boards, comma separated: names, key prefixes or ids (required)")
	childrenCmd.AddCommand(childrenAddCmd)

	relatedCmd := &cobra.Command{
		Use:   "related <board>",
		Short: "List the boards related to a board in its tree",
		Args:  cobra.ExactArgs(1),
		RunE:  runRelated,
	}

	useCmd := &cobra.Command{
		Use:   "use [board]",
		Short: "Choose the default board of the current organization",
		Long:  boardUseHelp,
		Args:  cobra.MaximumNArgs(1),
		RunE:  runUse,
	}

	cmd.AddCommand(listCmd, getCmd, createCmd, updateCmd, deleteCmd, restoreCmd, childrenCmd, relatedCmd, useCmd)
	return cmd
}

func runList(cmd *cobra.Command, search string) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	tree, err := board.List(c, search)
	if err != nil {
		return err
	}
	// With --search the result is the filtered tree, which board.List leaves to the caller.
	if strings.TrimSpace(search) != "" && jsonout.Enabled() {
		items, err := board.EncodeTree(tree)
		if err != nil {
			return err
		}
		jsonresult.SetList(items)
	}
	return board.PrintList(cmd.OutOrStdout(), tree)
}

func runGet(cmd *cobra.Command, args []string) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	details, err := board.Get(c, args[0])
	if err != nil {
		return err
	}
	board.PrintDetails(cmd.OutOrStdout(), details)
	return nil
}

func newCreateCmd() *cobra.Command {
	var in board.CreateInput
	var color, icon string
	cmd := &cobra.Command{
		Use:   "create",
		Short: "Create a board",
		Long: "Creates a board with a name and a key prefix, the prefix of its ticket keys. --parent makes it a child " +
			"board; each --member adds a member as <email or userId>:<ROLE>, ROLE one of " +
			strings.Join(board.MemberRoles, ", ") + ".\n\n" +
			"  oba kazan board create --name Operations --key OPS --member a@example.com:ADMIN",
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			in.Color = changed(cmd, "color", color)
			in.Icon = changed(cmd, "icon", icon)
			return runCreate(cmd, in)
		},
	}
	f := cmd.Flags()
	f.StringVar(&in.Name, "name", "", "board name (required)")
	f.StringVar(&in.Description, "description", "", "board description")
	f.StringVar(&in.Key, "key", "", "board key prefix, the prefix of its ticket keys (required)")
	f.StringVar(&color, "color", "", "board color")
	f.StringVar(&icon, "icon", "", "board icon")
	f.StringVar(&in.Parent, "parent", "", "parent board: name, key prefix or id")
	f.StringArrayVar(&in.Members, "member", nil, "initial member as <email or userId>:<ROLE> (repeatable)")
	return cmd
}

func runCreate(cmd *cobra.Command, in board.CreateInput) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	created, err := board.Create(c, in)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Board created: %s (%s)\n", created.Name, created.ID)
	return nil
}

func newUpdateCmd() *cobra.Command {
	var name, description, key, color, icon string
	cmd := &cobra.Command{
		Use:   "update <board>",
		Short: "Change the given fields of a board",
		Long:  "Changes only the fields whose flags are given; every other field keeps its value.\n\n" + boardRefHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			in := board.UpdateInput{
				Name:        changed(cmd, "name", name),
				Description: changed(cmd, "description", description),
				Key:         changed(cmd, "key", key),
				Color:       changed(cmd, "color", color),
				Icon:        changed(cmd, "icon", icon),
			}
			return runUpdate(cmd, args[0], in)
		},
	}
	f := cmd.Flags()
	f.StringVar(&name, "name", "", "board name")
	f.StringVar(&description, "description", "", "board description")
	f.StringVar(&key, "key", "", "board key prefix")
	f.StringVar(&color, "color", "", "board color")
	f.StringVar(&icon, "icon", "", "board icon")
	return cmd
}

func runUpdate(cmd *cobra.Command, ref string, in board.UpdateInput) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	if _, err := board.Update(c, ref, in); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Board %s updated.\n", ref)
	return nil
}

func newDeleteCmd() *cobra.Command {
	var opts board.DeleteOptions
	cmd := &cobra.Command{
		Use:   "delete <board>",
		Short: "Delete a board, saying where its tickets and child boards go",
		Long:  boardDeleteHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runDelete(cmd, args[0], opts)
		},
	}
	f := cmd.Flags()
	f.StringVar(&opts.TicketsTo, "tickets-to", "", "where the board's tickets go: backlog or sprint (of --target-board), or delete")
	f.StringVar(&opts.TargetBoard, "target-board", "", "the board that receives the tickets: name, key prefix or id")
	f.StringVar(&opts.TargetSprint, "target-sprint", "", "the sprint of --target-board for --tickets-to sprint ('sprint' means the running one)")
	f.StringVar(&opts.Children, "children", "", "what happens to child boards: promote, move or delete")
	f.StringVar(&opts.NewParent, "new-parent", "", "the new parent for --children move: name, key prefix or id")
	f.BoolVar(&opts.Yes, "yes", false, "confirm --tickets-to delete or --children delete")
	return cmd
}

func runDelete(cmd *cobra.Command, ref string, opts board.DeleteOptions) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	if _, err := board.Delete(c, ref, opts); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Board %s deleted.\n", ref)
	return nil
}

func runRestore(cmd *cobra.Command, args []string) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	if _, err := board.Restore(c, args[0]); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Board %s restored.\n", args[0])
	return nil
}

func runChildrenAdd(cmd *cobra.Command, args []string, childIDs string) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	added, err := board.AddChildren(c, args[0], strings.Split(childIDs, ","))
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Added %d child board(s) to %s.\n", added, args[0])
	return nil
}

func runRelated(cmd *cobra.Command, args []string) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	related, err := board.ListRelated(c, args[0])
	if err != nil {
		return err
	}
	if len(related) == 0 {
		_, _ = fmt.Fprintln(cmd.OutOrStdout(), "No related boards found.")
		return nil
	}
	return board.PrintList(cmd.OutOrStdout(), related)
}

func runUse(cmd *cobra.Command, args []string) error {
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	ref := ""
	if len(args) == 1 {
		ref = args[0]
	} else {
		if !stdinIsTerminal() {
			return errors.New("name the board: 'oba kazan board use <board>'")
		}
		src := cmd.InOrStdin()
		picked, err := board.Pick(c, bufio.NewReader(src), src, out)
		if err != nil {
			return err
		}
		ref = picked
	}
	chosen, err := board.Use(c, ref)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Default board: %s (%s)\n", chosen.Name, chosen.Prefix)
	return nil
}

// changed returns the flag's value when the flag was given, else nil, so that an empty value
// given on purpose is still sent.
func changed(cmd *cobra.Command, name, value string) *string {
	if !cmd.Flags().Changed(name) {
		return nil
	}
	return &value
}
