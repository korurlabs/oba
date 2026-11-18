// Package label builds the oba kazan label commands: list, create, change and delete the labels
// of a board.
package label

import (
	"bufio"
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/kazan/boardflag"
	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/label"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

const boardHelp = "The board is --board, else the default board chosen with 'oba kazan board use'."

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

// NewCmd builds the oba kazan label command with its subcommands.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "label",
		Short: "List, create, change and delete the labels of a board",
		Long:  "Lists, creates, changes and deletes the labels of a Kazan board.\n\n" + boardHelp,
	}

	listCmd := &cobra.Command{
		Use:   "list",
		Short: "List the labels of a board",
		Long:  "Lists the labels of a board: NAME, COLOR and ID.\n\n" + boardHelp,
		Args:  cobra.NoArgs,
		RunE:  runList,
	}

	var createName, createColor string
	createCmd := &cobra.Command{
		Use:   "create",
		Short: "Create a label",
		Long: "Creates a label on a board. --color is #RRGGBB.\n\n" + boardHelp +
			"\n\n  oba kazan label create --name bug --color \"#EF4444\"",
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runCreate(cmd, createName, changed(cmd, "color", createColor))
		},
	}
	createCmd.Flags().StringVar(&createName, "name", "", "label name (required)")
	createCmd.Flags().StringVar(&createColor, "color", "", "label color as #RRGGBB")

	var updateName, updateColor string
	updateCmd := &cobra.Command{
		Use:   "update <labelId>",
		Short: "Change the given fields of a label",
		Long:  "Changes only the fields whose flags are given; the other keeps its value.\n\n" + boardHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runUpdate(cmd, args[0], changed(cmd, "name", updateName), changed(cmd, "color", updateColor))
		},
	}
	updateCmd.Flags().StringVar(&updateName, "name", "", "label name")
	updateCmd.Flags().StringVar(&updateColor, "color", "", "label color as #RRGGBB")

	var deleteYes bool
	deleteCmd := &cobra.Command{
		Use:   "delete <labelId>",
		Short: "Delete a label",
		Long:  "Deletes a label. On a terminal it asks for confirmation first, unless --yes is given.\n\n" + boardHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runDelete(cmd, args[0], deleteYes)
		},
	}
	deleteCmd.Flags().BoolVarP(&deleteYes, "yes", "y", false, "delete without asking for confirmation")

	cmd.AddCommand(listCmd, createCmd, updateCmd, deleteCmd)
	return cmd
}

// selectedBoard returns the organization client and the id of the board the command acts on.
func selectedBoard(cmd *cobra.Command) (*client.Client, string, error) {
	c, err := session.OrganizationClient()
	if err != nil {
		return nil, "", err
	}
	selected, err := boardflag.Selected(cmd, c)
	if err != nil {
		return nil, "", err
	}
	return c, selected.ID, nil
}

func runList(cmd *cobra.Command, _ []string) error {
	c, boardID, err := selectedBoard(cmd)
	if err != nil {
		return err
	}
	labels, err := label.List(c, boardID)
	if err != nil {
		return err
	}
	return label.PrintList(cmd.OutOrStdout(), labels)
}

func runCreate(cmd *cobra.Command, name string, color *string) error {
	c, boardID, err := selectedBoard(cmd)
	if err != nil {
		return err
	}
	created, err := label.Create(c, boardID, name, color)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Label created: %s (%s)\n", guardrail.OneLine(created.Name), created.ID)
	return nil
}

func runUpdate(cmd *cobra.Command, labelID string, name, color *string) error {
	if name == nil && color == nil {
		return terminal.ErrNothingToUpdate
	}
	c, boardID, err := selectedBoard(cmd)
	if err != nil {
		return err
	}
	if _, err := label.Update(c, boardID, labelID, name, color); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Label %s updated.\n", labelID)
	return nil
}

func runDelete(cmd *cobra.Command, labelID string, yes bool) error {
	c, boardID, err := selectedBoard(cmd)
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	if !yes && stdinIsTerminal() {
		src := cmd.InOrStdin()
		confirmed, err := terminal.Confirm(bufio.NewReader(src), src, out, fmt.Sprintf("Delete label %s?", labelID))
		if err != nil {
			return err
		}
		if !confirmed {
			jsonresult.Nothing()
			_, _ = fmt.Fprintln(out, "Cancelled.")
			return nil
		}
	}
	if err := label.Delete(c, boardID, labelID); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Label %s deleted.\n", labelID)
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
