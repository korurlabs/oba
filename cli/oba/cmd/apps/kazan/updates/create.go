package updates

import (
	"errors"
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/updates"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

// composeFlags are the flags of create and preview.
type composeFlags struct {
	tickets   string
	percents  []string
	notes     []string
	focus     string
	questions string
	yes       bool
}

func newCreateCmd() *cobra.Command {
	var f composeFlags
	cmd := &cobra.Command{
		Use:   "create",
		Short: "Write and post your daily update",
		Long: `Write and post your daily update.

Without --tickets the update starts from your suggested tickets: those assigned
to you and IN_PROGRESS, sub-tickets included. On a terminal you confirm them or
pick from your open tickets, then set each percentage and note, your focus and
your questions; the preview is shown before posting unless --yes is given.
Without a terminal, or with --json, nothing is asked and the update is posted
as the flags describe; --focus is then required.

A ticket without --percent keeps its current progress as its percentage.
` + lineFlagsHelp,
		Example: `  oba kazan updates create
  oba kazan updates create --tickets WEB-218,KAGI-40 --percent WEB-218=40 --note "WEB-218=Waiting for icons" --focus "Finish the menu" --yes
  oba kazan updates create --json`,
		Args: cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runCompose(cmd, f, false)
		},
	}
	addComposeFlags(cmd, &f)
	cmd.Flags().BoolVarP(&f.yes, "yes", "y", false, "post without showing the preview and asking")
	return cmd
}

func newPreviewCmd() *cobra.Command {
	var f composeFlags
	cmd := &cobra.Command{
		Use:   "preview",
		Short: "Show the text of a daily update without posting it",
		Long: `Show the text an update would have, and which tickets would take its
percentage as their progress, without saving anything. It takes the same flags
as 'oba kazan updates create'; --focus is optional here.`,
		Example: `  oba kazan updates preview --tickets WEB-218 --percent WEB-218=40`,
		Args:    cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			return runCompose(cmd, f, true)
		},
	}
	addComposeFlags(cmd, &f)
	return cmd
}

func addComposeFlags(cmd *cobra.Command, f *composeFlags) {
	cmd.Flags().StringVar(&f.tickets, "tickets", "", "comma-separated ticket keys (e.g. WEB-218,KAGI-40); default: your suggested tickets")
	addLineFlags(cmd, &f.percents, &f.notes)
	cmd.Flags().StringVar(&f.focus, "focus", "", "what you will focus on today")
	cmd.Flags().StringVar(&f.questions, "questions", "", "your questions to colleagues")
}

// runCompose builds a new update and posts it, or with previewOnly shows its preview and saves
// nothing.
func runCompose(cmd *cobra.Command, f composeFlags, previewOnly bool) error {
	lineFlags, err := updates.ParseLineFlags(f.percents, f.notes)
	if err != nil {
		return err
	}
	ticketRefs := userinput.SplitCSV(f.tickets)
	if cmd.Flags().Changed("tickets") && len(ticketRefs) == 0 {
		return errors.New("--tickets names no ticket")
	}
	if len(ticketRefs) > 0 {
		if err := updates.CheckLineFlagRefs(ticketRefs, lineFlags); err != nil {
			return err
		}
	}
	focus := textFlag(cmd, "focus", f.focus)
	// A terminal asks for the focus later; a blank --focus or no terminal leaves nothing to ask
	// with, so that fails before any request.
	focusRequired := !previewOnly
	if focusRequired && updates.OptionalText(focus.Value) == nil && (focus.Changed || !interactive()) {
		return updates.ErrCreateFocusRequired
	}

	c, r, err := start(cmd)
	if err != nil {
		return err
	}
	p := prompter(cmd)
	req, err := updates.Compose(r, updates.ComposeInput{
		TicketRefs:    ticketRefs,
		LineFlags:     lineFlags,
		Focus:         focus,
		Questions:     textFlag(cmd, "questions", f.questions),
		FocusRequired: focusRequired,
		Prompter:      p,
		Me:            session.CurrentUserID,
	})
	if errors.Is(err, updates.ErrCancelled) {
		return cancelled(cmd)
	}
	if err != nil {
		return err
	}
	if focusRequired && req.Focus == nil {
		return updates.ErrCreateFocusRequired
	}

	out, errOut := cmd.OutOrStdout(), cmd.ErrOrStderr()
	if previewOnly {
		preview, err := c.PreviewDailyUpdate(req)
		if err != nil {
			return fmt.Errorf("failed to preview update: %w", err)
		}
		updates.PrintSummary(out, preview.SummaryText)
		updates.PrintProgressSync(errOut, preview.Items, "Would set ticket progress")
		return nil
	}

	if p != nil && !f.yes {
		preview, err := c.Unrecorded().PreviewDailyUpdate(req)
		if err != nil {
			return fmt.Errorf("failed to preview update: %w", err)
		}
		_, _ = fmt.Fprintln(out)
		updates.PrintSummary(out, preview.SummaryText)
		_, _ = fmt.Fprintln(out)
		updates.PrintProgressSync(errOut, preview.Items, "Sets ticket progress")
		confirmed, err := p.Confirm("Post this update?")
		if err != nil {
			return err
		}
		if !confirmed {
			return cancelled(cmd)
		}
	}

	posted, err := c.CreateDailyUpdate(req)
	if err != nil {
		return fmt.Errorf("failed to create update: %w", err)
	}
	updates.PrintSummary(out, posted.SummaryText)
	updates.PrintProgressSync(errOut, posted.Items, "Ticket progress set")
	_, _ = fmt.Fprintf(errOut, "Posted update %s\n", posted.ID)
	return nil
}
