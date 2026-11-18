package updates

import (
	"errors"
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/updates"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
)

// editFlags are the flags of edit.
type editFlags struct {
	add       string
	remove    string
	percents  []string
	notes     []string
	focus     string
	questions string
	yes       bool
}

func newEditCmd() *cobra.Command {
	var f editFlags
	cmd := &cobra.Command{
		Use:   "edit <updateId>",
		Short: "Edit one of your daily updates",
		Long: `Edit one of your daily updates. Only the flags you pass change; the rest of
the update stays as it is.

--remove and --add take ticket keys, comma-separated; an added ticket starts at
its current progress. ` + lineFlagsHelp + ` "--note KEY=" clears a note, and
--questions "" clears the questions. The focus is required: --focus "" is an
error, and an update stored without a focus needs --focus. On a terminal without
change flags you are asked for each line's percentage and note, then focus and
questions, with the current values as defaults. The preview is shown before
saving unless --yes is given.

An update keeps at least one ticket; use 'oba kazan updates delete' to remove it.
The percentage of an added line, or of a kept line whose percentage this edit
changes, also becomes the ticket's progress when it differs from that progress
and you are OWNER, ADMIN or MEMBER on its board; an unchanged line writes
nothing.`,
		Example: `  oba kazan updates edit 5b2e7c9a-1d3f-4a6b-8c0d-2e4f6a8b0c1d --percent WEB-218=60 --yes
  oba kazan updates edit 5b2e7c9a-1d3f-4a6b-8c0d-2e4f6a8b0c1d --add KAGI-41 --remove KAGI-40 --note "WEB-218="`,
		Args: cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runEdit(cmd, args[0], f)
		},
	}
	cmd.Flags().StringVar(&f.add, "add", "", "comma-separated ticket keys to add")
	cmd.Flags().StringVar(&f.remove, "remove", "", "comma-separated ticket keys to remove")
	addLineFlags(cmd, &f.percents, &f.notes)
	cmd.Flags().StringVar(&f.focus, "focus", "", "new focus")
	cmd.Flags().StringVar(&f.questions, "questions", "", `new questions ("" clears them)`)
	cmd.Flags().BoolVarP(&f.yes, "yes", "y", false, "save without showing the preview and asking")
	return cmd
}

func runEdit(cmd *cobra.Command, arg string, f editFlags) error {
	id, err := updates.ParseID(arg)
	if err != nil {
		return err
	}
	lineFlags, err := updates.ParseLineFlags(f.percents, f.notes)
	if err != nil {
		return err
	}
	in := updates.EditInput{
		AddRefs:    userinput.SplitCSV(f.add),
		RemoveRefs: userinput.SplitCSV(f.remove),
		LineFlags:  lineFlags,
		Focus:      textFlag(cmd, "focus", f.focus),
		Questions:  textFlag(cmd, "questions", f.questions),
	}
	if err := in.Check(interactive()); err != nil {
		return err
	}

	c, r, err := start(cmd)
	if err != nil {
		return err
	}
	current, err := c.Unrecorded().GetDailyUpdate(id)
	if err != nil {
		return fmt.Errorf("failed to get update: %w", err)
	}
	p := prompter(cmd)
	req, err := updates.EditRequest(r, current, in, p)
	if errors.Is(err, updates.ErrCancelled) {
		return cancelled(cmd)
	}
	if err != nil {
		return err
	}

	out, errOut := cmd.OutOrStdout(), cmd.ErrOrStderr()
	if p != nil && !f.yes {
		preview, err := c.Unrecorded().PreviewDailyUpdateEdit(id, req)
		if err != nil {
			return fmt.Errorf("failed to preview update: %w", err)
		}
		_, _ = fmt.Fprintln(out)
		updates.PrintSummary(out, preview.SummaryText)
		_, _ = fmt.Fprintln(out)
		updates.PrintProgressSync(errOut, preview.Items, "Sets ticket progress")
		confirmed, err := p.Confirm("Save this update?")
		if err != nil {
			return err
		}
		if !confirmed {
			return cancelled(cmd)
		}
	}

	saved, err := c.EditDailyUpdate(id, req)
	if err != nil {
		return fmt.Errorf("failed to edit update: %w", err)
	}
	updates.PrintSummary(out, saved.SummaryText)
	_, _ = fmt.Fprintf(errOut, "Saved update %s\n", saved.ID)
	return nil
}
