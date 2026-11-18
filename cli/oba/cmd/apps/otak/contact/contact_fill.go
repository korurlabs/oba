package contact

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"os/signal"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/otak/contact"
	"github.com/korurlabs/oba-cli/internal/apps/otak/relation"
	"github.com/korurlabs/oba-cli/internal/connect/claude"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

var contactFillHelp = fmt.Sprintf(`Has Claude Code, on this machine and under your own Claude account, search the web for the
business contacts of relations that have none yet, then creates the ones you pick.

With a relation id it searches that one relation, also when it already has contacts; those
are named in the search so that Claude Code proposes none of them again. Without an id it
searches the relations that have no contact at all, at most --limit of them, narrowed by
--type, --stage and --search like 'oba otak relation list'. Without --stage, LOST and PARKED
leads are left out. A relation without a website is never searched.

Claude Code searches at most %d relations per run, with web search and page fetch only, and
looks for at most %d people per company: people named in a business role on the company's own
website or in a public business register, with only their name, work role, the business
e-mail and phone the source publishes for them, and the page they were found on. Before they
are shown, an e-mail address off the company's domain and a phone number with letters are left
out, a contact without a valid source page is dropped, and so is one with the name of a contact
the relation already has.

The results list each relation with its contacts numbered, or "no contact found". Pick numbers
(1,3 or 1-3 or all) and confirm with y; q or Ctrl-D quits without creating anything. Ctrl-C
while Claude Code searches stops it, and nothing is created.

The command is interactive and needs Claude Code (https://claude.com/claude-code), signed in
once by running 'claude'.`, contact.MaxFillPerSearch, contact.MaxFound)

var errFillNotInteractive = errors.New("oba otak contact fill is interactive; run it in a terminal.") //nolint:staticcheck // ST1005: shown to the user verbatim

const (
	defaultFillLimit = 10
	quitAnswer       = "q"
)

// inactiveLeadStages are the lead stages a fill without --stage leaves out: a lost or parked
// lead is not worth a search on the user's Claude account. The API leaves out the same stages
// from a lead list that names none.
var inactiveLeadStages = map[string]bool{"LOST": true, "PARKED": true}

// fillOptions are the flags of contact fill.
// claudeInstalled, runClaude and stdoutIsTerminal are variables so that tests can stand in for
// Claude Code and a terminal.
var (
	claudeInstalled  = claude.Installed
	runClaude        = claude.Run
	stdoutIsTerminal = terminal.StdoutIsTerminal
)

type fillOptions struct {
	query relation.ListQuery
	limit int
	role  string
}

func newContactFillCmd() *cobra.Command {
	var opts fillOptions
	cmd := &cobra.Command{
		Use:   "fill [relationId]",
		Short: "Find business contacts for relations without one with Claude Code and create the ones you pick",
		Long:  contactFillHelp,
		Args:  cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return runContactFill(cmd, args, opts)
		},
	}
	cmd.Flags().StringVar(&opts.query.Type, "type", "", "fill only this type: LEAD, CUSTOMER or SUPPLIER (default every type)")
	cmd.Flags().StringSliceVar(&opts.query.Stages, "stage", nil, "fill only leads and customers at these stages; repeat or give a comma list (default every stage but LOST and PARKED)")
	cmd.Flags().StringVar(&opts.query.Search, "search", "", "fill only relations whose name contains this text, case ignored")
	cmd.Flags().IntVar(&opts.limit, "limit", defaultFillLimit, "the most relations to search in one run")
	cmd.Flags().StringVar(&opts.role, "role", contact.DefaultFillRole, "the role of the people to prefer")
	return cmd
}

func runContactFill(cmd *cobra.Command, args []string, opts fillOptions) error {
	// Each run may create several records; --json lists all of them.
	jsonout.CollectWrites()
	if opts.limit < 1 {
		return errors.New("--limit must be at least 1")
	}
	if err := claudeInstalled(); err != nil {
		return err
	}
	if !stdinIsTerminal() || !stdoutIsTerminal() {
		return errFillNotInteractive
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	orgName, err := session.OrganizationName(c.OrgID)
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()

	var targets []contact.FillTarget
	if len(args) == 1 {
		target, err := contact.GetFillTarget(c, strings.TrimSpace(args[0]))
		if err != nil {
			return err
		}
		targets = []contact.FillTarget{target}
	} else if targets, err = listFillTargets(c, out, opts); err != nil {
		return err
	}
	if len(targets) == 0 {
		_, _ = fmt.Fprintln(out, "No relation to fill.")
		return nil
	}

	results, err := searchContacts(cmd.Context(), out, targets, strings.TrimSpace(opts.role))
	if err != nil {
		return err
	}
	_, _ = fmt.Fprintln(out)
	count := contact.ShowFillResults(out, results)
	if count == 0 {
		_, _ = fmt.Fprintln(out, "Claude found no contacts.")
		return nil
	}

	src := cmd.InOrStdin()
	in := bufio.NewReader(src)
	picks, err := askFillPicks(in, src, out, count, results, orgName)
	if err != nil || len(picks) == 0 {
		return err
	}
	return contact.CreateFilled(c, out, contact.PickedContacts(results, picks))
}

// listFillTargets reads the relations without a contact that the flags select, drops those
// without a website, and the inactive leads unless --stage
// names stages, and keeps at most --limit. It prints what it left out.
func listFillTargets(c *client.Client, out io.Writer, opts fillOptions) ([]contact.FillTarget, error) {
	query := opts.query
	noContacts := false
	query.HasContacts = &noContacts
	items, err := relation.List(c, query)
	if err != nil {
		return nil, err
	}
	var targets []contact.FillTarget
	noWebsite := 0
	for _, item := range items {
		switch {
		case len(query.Stages) == 0 && item.Stage != nil && inactiveLeadStages[*item.Stage]:
			continue
		case item.Website == nil || strings.TrimSpace(*item.Website) == "":
			noWebsite++
		default:
			targets = append(targets, contact.FillTarget{ID: item.ID, Name: item.Name, Website: strings.TrimSpace(*item.Website)})
		}
	}
	found := len(targets)
	if len(targets) > opts.limit {
		targets = targets[:opts.limit]
	}
	_, _ = fmt.Fprintf(out, "%d relations without a contact; searching %d.", found, len(targets))
	if noWebsite > 0 {
		_, _ = fmt.Fprintf(out, " Skipped %d without a website.", noWebsite)
	}
	_, _ = fmt.Fprintln(out)
	return targets, nil
}

// searchContacts runs Claude Code once per batch of at most MaxFillPerSearch targets and
// returns the results of every batch. Ctrl-C stops the search and nothing is created.
func searchContacts(parent context.Context, out io.Writer, targets []contact.FillTarget, role string) ([]contact.FillResult, error) {
	if role == "" {
		role = contact.DefaultFillRole
	}
	schema, err := contact.FillSchema()
	if err != nil {
		return nil, err
	}
	var results []contact.FillResult
	for start := 0; start < len(targets); start += contact.MaxFillPerSearch {
		batch := targets[start:min(start+contact.MaxFillPerSearch, len(targets))]
		_, _ = fmt.Fprintln(out)
		_, _ = fmt.Fprintf(out, "Claude searches contacts for %d relations (about 1–3 minutes, Ctrl-C stops it)\n", len(batch))
		ctx, stop := signal.NotifyContext(parent, os.Interrupt)
		raw, err := runClaude(ctx, contact.FillBrief(batch, role), schema, func(line string) {
			_, _ = fmt.Fprintln(out, "  "+line)
		})
		stop()
		if err != nil {
			if errors.Is(err, context.Canceled) {
				return nil, errors.New("stopped; nothing was created")
			}
			return nil, err
		}
		batchResults, err := contact.DecodeFill(raw, batch)
		if err != nil {
			return nil, err
		}
		results = append(results, batchResults...)
	}
	return results, nil
}

// askFillPicks asks for the contacts to create until the user confirms picks or quits, which
// returns no picks.
func askFillPicks(in *bufio.Reader, src io.Reader, out io.Writer, count int, results []contact.FillResult, orgName string) ([]int, error) {
	for {
		answer, eof, err := terminal.Ask(in, src, out, "Numbers to create (1,3 or 1-3 or all), q to quit\n> ")
		if err != nil {
			return nil, err
		}
		if strings.EqualFold(strings.TrimSpace(answer), quitAnswer) || (answer == "" && eof) {
			_, _ = fmt.Fprintln(out, "Nothing created.")
			return nil, nil
		}
		if answer == "" {
			continue
		}
		picks, err := terminal.ParsePicks(answer, count, nil, "")
		if err != nil {
			_, _ = fmt.Fprintln(out, err.Error())
			if eof {
				return nil, nil
			}
			continue
		}
		relations := len(contact.PickedContacts(results, picks))
		question := fmt.Sprintf("Create %d contacts on %d relations in %s?", len(picks), relations, orgName)
		confirmed, err := terminal.Confirm(in, src, out, question)
		if err != nil {
			return nil, err
		}
		if confirmed {
			return picks, nil
		}
		_, _ = fmt.Fprintln(out, "Not created.")
	}
}
