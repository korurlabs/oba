package pr

import (
	"errors"
	"fmt"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/pr"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

// composeFlags are the flags share and preview share.
type composeFlags struct {
	repo, status, note, reviewers, tickets string
	yes                                    bool
}

func (f *composeFlags) add(cmd *cobra.Command) {
	fl := cmd.Flags()
	fl.StringVar(&f.repo, "repo", "", "Repository of a bare number: name, owner/name or ID")
	fl.StringVar(&f.status, "status", "", "DRAFT, OPEN, IN_REVIEW, MERGED or DISCUSS; default: preset from GitHub")
	fl.StringVar(&f.note, "note", "", fmt.Sprintf("Note shown after the status, at most %d characters", pr.MaxNoteLength))
	fl.StringVar(&f.reviewers, "reviewers", "", fmt.Sprintf("Reviewers: emails or user UUIDs (comma-separated, at most %d)", pr.MaxReviewers))
	fl.StringVar(&f.tickets, "tickets", "", fmt.Sprintf("Related tickets: keys or UUIDs (comma-separated, at most %d)", pr.MaxRelatedTickets))
	fl.BoolVarP(&f.yes, "yes", "y", false, "Ask nothing: use the flags as given (share: post without the preview)")
}

func newShareCmd() *cobra.Command {
	var f composeFlags
	cmd := &cobra.Command{
		Use:   "share [<pr>]",
		Short: "Share a pull request in the PR channel",
		Long: `Share a pull request in your organization's PR channel. It posts one card
with the pull request, the team's status, a note, reviewers and related
tickets. The same pull request may be shared again.

<pr> is a GitHub URL, owner/name#N, name#N, or N with --repo. Without --status
the status is preset from GitHub: draft, open or merged. A closed pull request
has no preset: pass --status, or choose one when asked in a terminal.

--status takes DRAFT, OPEN, IN_REVIEW, MERGED or DISCUSS, case-insensitive.
--note is at most 500 characters. --reviewers takes emails or user UUIDs,
comma-separated, at most 10; you cannot review your own share. --tickets takes
ticket keys or UUIDs of any of your boards, comma-separated, at most 10, in the
order the card lists them.

In a terminal you are asked for what the flags leave open (without <pr>: the
repository, a search and the pull request; then status, note and reviewers),
and the card text is shown before posting; related tickets come only from
--tickets. --yes, --json or no terminal asks nothing and posts as the flags
describe. The organization needs a PR channel.`,
		Example: `  oba kazan pr share
  oba kazan pr share https://github.com/acme/shop/pull/688 --status IN_REVIEW --reviewers ada@example.com --yes
  oba kazan pr share shop#688 --note "Ready for a look" --tickets OPS-241,WEB-40 --yes
  oba kazan pr share 688 --repo shop --status discuss --json`,
		Args: cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runCompose(cmd, args, f, false)
		},
	}
	f.add(cmd)
	return cmd
}

func newPreviewCmd() *cobra.Command {
	var f composeFlags
	cmd := &cobra.Command{
		Use:   "preview [<pr>]",
		Short: "Show the card text of a share without posting it",
		Long: `Show the text the card of a share would have, without saving anything. It
takes the flags of 'oba kazan pr share' and fails where a share would: a
limit, a reviewer rule, a related ticket you cannot open, GitHub, or no PR
channel.`,
		Example: `  oba kazan pr preview https://github.com/acme/shop/pull/688 --status IN_REVIEW --yes
  oba kazan pr preview shop#688 --reviewers ada@example.com --json`,
		Args: cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runCompose(cmd, args, f, true)
		},
	}
	f.add(cmd)
	return cmd
}

// runCompose builds a share request and posts it, or with previewOnly shows its card text and
// saves nothing. Every value a flag or the argument gives is checked before any request.
func runCompose(cmd *cobra.Command, args []string, f composeFlags, previewOnly bool) error {
	asking := interactive() && !f.yes
	in, err := composeInput(cmd, args, f, asking)
	if err != nil {
		return err
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	var p *pr.Prompter
	if asking {
		p = prompter(cmd)
	}
	req, err := pr.Compose(c, in, p, meFunc())
	if errors.Is(err, pr.ErrCancelled) {
		return cancelled(cmd)
	}
	if err != nil {
		return err
	}

	out := cmd.OutOrStdout()
	if previewOnly {
		preview, err := pr.Preview(c, req)
		if err != nil {
			return err
		}
		pr.PrintCard(out, preview.SummaryText)
		return nil
	}

	if asking {
		confirmed, err := confirmShare(c, p, req)
		if err != nil {
			return err
		}
		if !confirmed {
			return cancelled(cmd)
		}
	}

	share, err := pr.Create(c, req)
	if err != nil {
		return err
	}
	pr.PrintCard(out, share.SummaryText)
	errOut := cmd.ErrOrStderr()
	_, _ = fmt.Fprintf(errOut, "Shared %s\n", share.ID)
	_, _ = fmt.Fprintln(errOut, pr.TicketLine(share.RelatedTickets))
	if link, ok := pr.Link(share); ok {
		_, _ = fmt.Fprintf(errOut, "Kazan: %s\n", link)
	}
	return nil
}

// composeInput reads the argument and the flags of share and preview, before any request.
func composeInput(cmd *cobra.Command, args []string, f composeFlags, asking bool) (pr.ComposeInput, error) {
	var in pr.ComposeInput
	if len(args) == 1 {
		ref, err := pr.ParsePullRef(args[0], f.repo)
		if err != nil {
			return in, err
		}
		in.Ref = &ref
	} else if !asking {
		return in, errors.New("a pull request is required: pass a GitHub URL, owner/name#N, name#N, or N with --repo")
	}
	var err error
	if in.Status, err = statusFlag(cmd, f.status); err != nil {
		return in, err
	}
	if in.Note, err = noteFlag(cmd, f.note); err != nil {
		return in, err
	}
	in.NoteGiven = cmd.Flags().Changed("note")
	if in.ReviewerRefs, err = pr.ParseReviewers("reviewers", f.reviewers); err != nil {
		return in, err
	}
	in.ReviewersGiven = cmd.Flags().Changed("reviewers")
	if in.TicketRefs, err = pr.ParseTickets("tickets", f.tickets); err != nil {
		return in, err
	}
	return in, nil
}

// confirmShare shows the card req would post and asks whether to post it. The preview is a
// lookup and stays out of the --json output.
func confirmShare(c *client.Client, p *pr.Prompter, req client.PullRequestShareRequest) (bool, error) {
	preview, err := pr.Preview(c.Unrecorded(), req)
	if err != nil {
		return false, err
	}
	out := p.Out()
	_, _ = fmt.Fprintln(out)
	pr.PrintCard(out, preview.SummaryText)
	_, _ = fmt.Fprintln(out)
	return p.Confirm("Share this pull request?")
}
