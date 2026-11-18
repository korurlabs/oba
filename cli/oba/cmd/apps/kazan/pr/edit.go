package pr

import (
	"errors"
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/jsonresult"
	"github.com/korurlabs/oba-cli/internal/apps/kazan/pr"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/dojo/userinput"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

// editFlags are the edit flags as typed.
type editFlags struct {
	status, note, reviewers, addReviewers, removeReviewers string
	tickets, addTickets, removeTickets                     string
	yes                                                    bool
}

func newEditCmd() *cobra.Command {
	var f editFlags
	cmd := &cobra.Command{
		Use:   "edit <shareId>",
		Short: "Edit the status, note, reviewers or related tickets of your share",
		Long: `Edit one of your shares. Only the flags you pass change; the pull request
itself never changes. Only the author can edit a share.

--note "" clears the note. --reviewers replaces the reviewers ("" removes them
all); --add-reviewers and --remove-reviewers change the list instead and
cannot be combined with --reviewers. They take emails or user UUIDs,
comma-separated. Removing a reviewer drops their approval; adding them back
starts unapproved.

--tickets replaces the related tickets ("" removes them all); --add-tickets
and --remove-tickets change the list instead and cannot be combined with
--tickets. They take ticket keys or UUIDs, comma-separated; a share names at
most 10. A ticket already on the share keeps its stored key and title, and can
be named and removed even when it is deleted or you can no longer open it.

The changes are listed and you are asked before saving unless --yes is given.
In a terminal without a change flag you are asked for the status, note and
reviewers, with the current values as defaults, and the related tickets are
kept; without a terminal a change flag is required.`,
		Example: `  oba kazan pr edit 5b2e7c9a-1d3f-4a6b-8c0d-2e4f6a8b0c1d --status MERGED --yes
  oba kazan pr edit 5b2e7c9a-1d3f-4a6b-8c0d-2e4f6a8b0c1d --add-reviewers bob@example.com --note ""
  oba kazan pr edit 5b2e7c9a-1d3f-4a6b-8c0d-2e4f6a8b0c1d --add-tickets WEB-241 --remove-tickets KAGI-40 --yes
  oba kazan pr edit 5b2e7c9a-1d3f-4a6b-8c0d-2e4f6a8b0c1d --reviewers "" --yes --json`,
		Args: cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runEdit(cmd, args[0], f)
		},
	}
	fl := cmd.Flags()
	fl.StringVar(&f.status, "status", "", "New status: DRAFT, OPEN, IN_REVIEW, MERGED or DISCUSS")
	fl.StringVar(&f.note, "note", "", "New note (\"\" clears it)")
	fl.StringVar(&f.reviewers, "reviewers", "", "Replace the reviewers: emails or user UUIDs (\"\" removes all)")
	fl.StringVar(&f.addReviewers, "add-reviewers", "", "Reviewers to add: emails or user UUIDs (comma-separated)")
	fl.StringVar(&f.removeReviewers, "remove-reviewers", "", "Reviewers to remove; their approval is dropped")
	fl.StringVar(&f.tickets, "tickets", "", fmt.Sprintf("Replace the related tickets: keys or UUIDs, at most %d (\"\" removes all)", pr.MaxRelatedTickets))
	fl.StringVar(&f.addTickets, "add-tickets", "", "Related tickets to add: keys or UUIDs (comma-separated)")
	fl.StringVar(&f.removeTickets, "remove-tickets", "", "Related tickets to remove: keys or UUIDs (comma-separated)")
	fl.BoolVarP(&f.yes, "yes", "y", false, "Save without listing the changes and asking")
	return cmd
}

func runEdit(cmd *cobra.Command, arg string, f editFlags) error {
	shareID, err := pr.ShareID(arg)
	if err != nil {
		return err
	}
	flags, err := readEditFlags(cmd, f)
	if err != nil {
		return err
	}
	hasChanges := flags.HasChanges()
	if !hasChanges && !interactive() {
		return pr.ErrNoChanges
	}

	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	current, raw, err := pr.Get(c, shareID)
	if err != nil {
		return err
	}
	me := meFunc()
	before := pr.EditOf(current)
	after := pr.EditOf(current)
	if err := after.Apply(c, current, flags, me); err != nil {
		return err
	}

	if !hasChanges {
		if err := after.PromptEdit(c, prompter(cmd), me); err != nil {
			if errors.Is(err, pr.ErrCancelled) {
				return cancelled(cmd)
			}
			return err
		}
	}

	out := cmd.OutOrStdout()
	changes := pr.DescribeChanges(before, after)
	if len(changes) == 0 {
		// The share as read is the result; a lookup made after the read must not replace it.
		if err := jsonresult.Set(raw); err != nil {
			return err
		}
		_, _ = fmt.Fprintf(out, "No changes to share %s.\n", shareID)
		return nil
	}

	if interactive() && !f.yes {
		_, _ = fmt.Fprintf(out, "Changes to %s#%d:\n", guardrail.OneLine(current.RepositoryFullName), current.Number)
		for _, line := range changes {
			_, _ = fmt.Fprintf(out, "  %s\n", line)
		}
		confirmed, err := prompter(cmd).Confirm("Save these changes?")
		if err != nil {
			return err
		}
		if !confirmed {
			return cancelled(cmd)
		}
	}

	share, err := pr.Update(c, shareID, after)
	if err != nil {
		return err
	}
	pr.PrintCard(out, share.SummaryText)
	errOut := cmd.ErrOrStderr()
	_, _ = fmt.Fprintf(errOut, "Saved share %s\n", share.ID)
	pr.PrintBlock(errOut, share, false)
	return nil
}

// readEditFlags checks the edit flags before any request.
func readEditFlags(cmd *cobra.Command, f editFlags) (pr.EditFlags, error) {
	changed := cmd.Flags().Changed
	var e pr.EditFlags
	var err error
	if e.Status, err = statusFlag(cmd, f.status); err != nil {
		return e, err
	}
	if e.Note, err = noteFlag(cmd, f.note); err != nil {
		return e, err
	}
	e.NoteGiven = changed("note")
	e.ReplaceReviewer = changed("reviewers")
	if e.Reviewers, err = pr.ParseReviewers("reviewers", f.reviewers); err != nil {
		return e, err
	}
	if e.AddReviewers, err = pr.ParseReviewers("add-reviewers", f.addReviewers); err != nil {
		return e, err
	}
	e.RemoveReviewers = userinput.SplitCSV(f.removeReviewers)
	e.ReplaceTickets = changed("tickets")
	if e.Tickets, err = pr.ParseTickets("tickets", f.tickets); err != nil {
		return e, err
	}
	if e.AddTickets, err = pr.ParseTickets("add-tickets", f.addTickets); err != nil {
		return e, err
	}
	if e.RemoveTickets, err = pr.ParseTickets("remove-tickets", f.removeTickets); err != nil {
		return e, err
	}
	return e, e.Check()
}
