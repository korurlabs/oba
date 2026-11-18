// Package project builds the oba kazan project commands: list, read, create, change and delete
// the projects of a board.
package project

import (
	"bufio"
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/kazan/boardflag"
	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/project"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

// descriptionDeprecation is shown when --description is passed: the project API has only a name
// and a color, so the value was never sent. The flag stays so that existing scripts still run.
const descriptionDeprecation = "projects have no description; the flag is ignored"

const projectDeleteHelp = `Deletes a project of the board. On a terminal it asks for confirmation first, unless --yes
is given; without a terminal, or with --json, it deletes without asking.`

// stdinIsTerminal is a variable so that tests can stand in for a terminal.
var stdinIsTerminal = terminal.StdinIsTerminal

// NewCmd builds the oba kazan project command with list, get, create, update and delete. Every
// subcommand acts on the board of --board, else the default board.
func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "project",
		Short: "List, read, create, change and delete the projects of a board",
		Long: "A project groups tickets of one board and has a name and a color. The board is --board, else the " +
			"default board chosen with 'oba kazan board use'.",
	}
	listCmd := &cobra.Command{
		Use:   "list",
		Short: "List the projects of the board",
		Args:  cobra.NoArgs,
		RunE:  runList,
	}
	getCmd := &cobra.Command{
		Use:   "get <projectId>",
		Short: "Show a project",
		Args:  cobra.ExactArgs(1),
		RunE:  runGet,
	}
	var yes bool
	deleteCmd := &cobra.Command{
		Use:   "delete <projectId>",
		Short: "Delete a project",
		Long:  projectDeleteHelp,
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runDelete(cmd, args[0], yes)
		},
	}
	deleteCmd.Flags().BoolVarP(&yes, "yes", "y", false, "delete without asking for confirmation")
	cmd.AddCommand(listCmd, getCmd, newCreateCmd(), newUpdateCmd(), deleteCmd)
	return cmd
}

func runList(cmd *cobra.Command, _ []string) error {
	c, boardID, err := selectBoard(cmd)
	if err != nil {
		return err
	}
	projects, err := project.List(c, boardID)
	if err != nil {
		return err
	}
	return project.PrintList(cmd.OutOrStdout(), projects)
}

func runGet(cmd *cobra.Command, args []string) error {
	c, boardID, err := selectBoard(cmd)
	if err != nil {
		return err
	}
	p, err := project.Get(c, boardID, args[0])
	if err != nil {
		return err
	}
	project.PrintDetails(cmd.OutOrStdout(), p)
	return nil
}

func newCreateCmd() *cobra.Command {
	var name, color, description string
	cmd := &cobra.Command{
		Use:   "create",
		Short: "Create a project on the board",
		Long:  "Creates a project with a name and, with --color, a color.\n\n  oba kazan project create --name Billing --color '#3366ff'",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runCreate(cmd, project.CreateInput{Name: name, Color: changed(cmd, "color", color)})
		},
	}
	f := cmd.Flags()
	f.StringVar(&name, "name", "", "project name (required)")
	f.StringVar(&color, "color", "", "project color")
	addDescriptionFlag(cmd, &description)
	return cmd
}

func runCreate(cmd *cobra.Command, in project.CreateInput) error {
	c, boardID, err := selectBoard(cmd)
	if err != nil {
		return err
	}
	created, err := project.Create(c, boardID, in)
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Project created: %s (%s)\n", created.Name, created.ID)
	return nil
}

func newUpdateCmd() *cobra.Command {
	var name, color, description string
	cmd := &cobra.Command{
		Use:   "update <projectId>",
		Short: "Change the given fields of a project",
		Long:  "Changes only the fields whose flags are given; every other field keeps its value.",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			in := project.UpdateInput{Name: changed(cmd, "name", name), Color: changed(cmd, "color", color)}
			return runUpdate(cmd, args[0], in)
		},
	}
	f := cmd.Flags()
	f.StringVar(&name, "name", "", "project name")
	f.StringVar(&color, "color", "", "project color")
	addDescriptionFlag(cmd, &description)
	return cmd
}

func runUpdate(cmd *cobra.Command, id string, in project.UpdateInput) error {
	c, boardID, err := selectBoard(cmd)
	if err != nil {
		return err
	}
	if _, err := project.Update(c, boardID, id, in); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Project %s updated.\n", id)
	return nil
}

func runDelete(cmd *cobra.Command, id string, yes bool) error {
	c, boardID, err := selectBoard(cmd)
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	if !yes && stdinIsTerminal() && !jsonout.Enabled() {
		src := cmd.InOrStdin()
		confirmed, err := terminal.Confirm(bufio.NewReader(src), src, out, fmt.Sprintf("Delete project %s?", id))
		if err != nil {
			return err
		}
		if !confirmed {
			// The board lookup before the question is not the result of a cancelled run.
			jsonresult.Nothing()
			_, _ = fmt.Fprintln(out, "Cancelled.")
			return nil
		}
	}
	if err := project.Delete(c, boardID, id); err != nil {
		return err
	}
	_, _ = fmt.Fprintf(out, "Project %s deleted.\n", id)
	return nil
}

// addDescriptionFlag adds the deprecated --description, which projects never had.
func addDescriptionFlag(cmd *cobra.Command, description *string) {
	cmd.Flags().StringVar(description, "description", "", "Deprecated: projects have no description")
	flag := cmd.Flags().Lookup("description")
	flag.Deprecated = descriptionDeprecation
	flag.Hidden = true
}

// changed returns the flag's value when the flag was given, else nil, so that an empty value
// given on purpose is still sent.
func changed(cmd *cobra.Command, name, value string) *string {
	if !cmd.Flags().Changed(name) {
		return nil
	}
	return &value
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
