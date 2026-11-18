package find

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
	"github.com/korurlabs/oba-cli/internal/apps/otak/find"
	"github.com/korurlabs/oba-cli/internal/apps/otak/icp"
	"github.com/korurlabs/oba-cli/internal/connect/claude"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

var findHelp = fmt.Sprintf(`Has Claude Code, on this machine and under your own Claude account, search the web for new
companies that fit one ICP (Ideal Customer Profile) of the current organization, then creates
the ones you pick as leads.

It runs in four steps. Step 1 lists the organization's ICPs and asks for a number; --icp picks
one by id or name instead. In step 2 you may narrow the search, for example to a region or a
kind of company; --focus gives that text instead, and Ctrl-D right away skips it. In step 3
Claude Code searches the web, with web search and page fetch only, and gets the names and
websites of the organization's leads, customers and suppliers so that it proposes none of
them. In the same search it looks for at most %d business contacts per company: people named
in a business role on the company's own website or in a public business register, with only
their name, work role, the business e-mail and phone the source publishes for them, and the
page they were found on. One line per search or page shows its progress. Step 4 lists the
candidates with their website and a short summary, and under each its contacts with their
source page, or "no contact found". Before they are shown, an e-mail address off the company's
domain and a phone number with letters are left out, and a contact without a valid source page
is dropped. A candidate that still matches a known relation by name or website shows "already
in Otak as <name>" and cannot be picked.

At the menu, pick numbers (1,3 or 1-3 or all) and confirm with y; each pick becomes a lead at
stage NEW with source RESEARCH, the ICP, its city and size when known, and the reason in its
notes, its contacts are created on it with their source page, and the command ends. A contact
that fails keeps its lead; add it later with 'oba otak contact create <relationId>'. n goes
back to the menu. r gives Claude Code feedback and it searches again, at most %d searches per
run; q or Ctrl-D quits without creating anything.

Text may span several lines; press Ctrl-D on an empty line to send it. Ctrl-C while Claude Code
searches stops it, and nothing is created. Ctrl-C at a prompt or during the creates ends the
command; the leads created by then stay.

The command is interactive and needs Claude Code (https://claude.com/claude-code), signed in
once by running 'claude'.`, contact.MaxFound, find.MaxSearches)

// errFindNotInteractive is printed as it is, so it keeps the full stop.
var errFindNotInteractive = errors.New("oba otak find is interactive; run it in a terminal.") //nolint:staticcheck // ST1005: shown to the user verbatim

// claudeInstalled, runClaude, stdinIsTerminal and stdoutIsTerminal are variables so that tests
// can stand in for Claude Code and a terminal.
var (
	claudeInstalled  = claude.Installed
	runClaude        = claude.Run
	stdinIsTerminal  = terminal.StdinIsTerminal
	stdoutIsTerminal = terminal.StdoutIsTerminal
)

const (
	focusFlag   = "focus"
	stepDivider = " · "
)

func NewCmd() *cobra.Command {
	var icpFlag, focus string
	cmd := &cobra.Command{
		Use:   "find",
		Short: "Find new leads for an ICP with Claude Code and create the ones you pick",
		Long:  findHelp,
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			var focusGiven *string
			if cmd.Flags().Changed(focusFlag) {
				focusGiven = &focus
			}
			return runOtakFind(cmd, icpFlag, focusGiven)
		},
	}
	cmd.Flags().StringVar(&icpFlag, "icp", "", "the ICP to search for, by id or by name (case ignored); without it the command asks")
	cmd.Flags().StringVar(&focus, focusFlag, "", "text that narrows the search, such as a region or a kind of company; without it the command asks, and an empty value skips it")
	return cmd
}

// findSession is one run of the command: the chosen ICP, what the API allows and knows, the
// search with its history, and the terminal it is held on.
type findSession struct {
	cmd       *cobra.Command
	api       *client.Client
	orgName   string
	icp       client.Icp
	enums     find.LeadEnums
	schema    string
	relations []client.RelationListItem
	search    *find.Search
	known     find.KnownCompanies
	src       io.Reader
	in        *bufio.Reader
	out       io.Writer
}

// menuAction is what the user chose at the pick menu once the answer is final.
type menuAction struct {
	picks    []int
	feedback string
}

// runOtakFind runs the four steps. focus is nil when --focus was not given, so that step 2 asks.
func runOtakFind(cmd *cobra.Command, icpFlag string, focus *string) error {
	// Each run may create several records; --json lists all of them.
	jsonout.CollectWrites()
	if err := claudeInstalled(); err != nil {
		return err
	}
	if !stdinIsTerminal() || !stdoutIsTerminal() {
		return errFindNotInteractive
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	orgName, err := session.OrganizationName(c.OrgID)
	if err != nil {
		return err
	}
	// One buffered reader serves every prompt; each prompt discards what waits in it and in the
	// terminal before it asks, so that a line typed ahead answers no question.
	src := cmd.InOrStdin()
	s := &findSession{cmd: cmd, api: c, orgName: orgName, src: src, in: bufio.NewReader(src), out: cmd.OutOrStdout()}

	icps, err := find.ListIcps(c)
	if err != nil {
		return fmt.Errorf("failed to read the ICPs: %w", err)
	}
	if len(icps) == 0 {
		return fmt.Errorf("%s has no ICPs; create one in Otak under ICPs first", orgName)
	}
	_, _ = fmt.Fprintln(s.out, "Find leads with Claude: search the web for companies that fit an ICP.")
	_, _ = fmt.Fprintln(s.out)
	chosen, ok, err := s.chooseIcp(icps, icpFlag)
	if err != nil || !ok {
		return err
	}
	if err := s.load(chosen); err != nil {
		return err
	}

	_, _ = fmt.Fprintln(s.out)
	focusText, err := s.askFocus(focus)
	if err != nil {
		return err
	}
	s.search = find.NewSearch(s.icp, s.relations, focusText)
	return s.run()
}

// chooseIcp is step 1: the ICP named by --icp, else the one the user picks by number. ok is
// false when the user cancels.
func (s *findSession) chooseIcp(icps []client.Icp, icpFlag string) (client.Icp, bool, error) {
	rows := icp.IcpRows(icps)
	if strings.TrimSpace(icpFlag) == "" {
		_, _ = fmt.Fprintln(s.out, "Step 1 of 4"+stepDivider+"Choose an ICP")
		picked, ok, err := find.PromptIcp(s.in, s.src, s.out, icps, rows)
		if err != nil {
			return client.Icp{}, false, err
		}
		if !ok {
			_, _ = fmt.Fprintln(s.out, "Cancelled.")
		}
		return picked, ok, nil
	}
	matched, err := find.MatchIcp(icps, icpFlag)
	if err != nil {
		return client.Icp{}, false, err
	}
	_, _ = fmt.Fprintln(s.out, "Step 1 of 4"+stepDivider+"ICP")
	for i, candidate := range icps {
		if candidate.ID == matched.ID {
			_, _ = fmt.Fprintln(s.out, "  "+rows[i])
		}
	}
	return matched, true, nil
}

// load reads what every search needs before the user types anything more: the ICP with its
// description, the allowed lead values, the known relations and the output schema.
func (s *findSession) load(chosen client.Icp) error {
	full, err := find.GetIcp(s.api, chosen.ID)
	if err != nil {
		return err
	}
	s.icp = full
	if s.enums, err = find.LeadFields(s.api.BaseURL); err != nil {
		return err
	}
	relations, err := find.ListKnownRelations(s.api, s.enums.Stages)
	if err != nil {
		return err
	}
	s.known = find.NewKnownCompanies(relations)
	if s.schema, err = find.Schema(s.enums); err != nil {
		return err
	}
	s.relations = relations
	return nil
}

// askFocus is step 2: the --focus text when given, else what the user types. An empty answer
// skips the focus.
func (s *findSession) askFocus(focus *string) (string, error) {
	if focus != nil {
		shown := guardrail.OneLine(*focus)
		if shown == "" {
			shown = "none"
		}
		_, _ = fmt.Fprintln(s.out, "Step 2 of 4"+stepDivider+"Focus: "+shown)
		return strings.TrimSpace(*focus), nil
	}
	return s.askText("Step 2 of 4"+stepDivider+"Focus the search (optional)",
		"  Narrow it down: a region, a niche, companies to skip.",
		"  Example: Only Zuid-Holland, webshops with their own payment flow.",
		"  Type or paste, then press Ctrl-D on an empty line to send.",
		"  Press Ctrl-D right away to skip.")
}

// askText discards the input already waiting, prints the question lines and the "> " marker,
// then reads text up to Ctrl-D on an empty line. Discarding keeps lines pasted with an earlier
// answer out of the text. Ctrl-D right away leaves the cursor after the marker, so an empty
// answer ends the line before anything else is printed.
func (s *findSession) askText(lines ...string) (string, error) {
	if err := terminal.DiscardPending(s.in, s.src); err != nil {
		return "", err
	}
	for _, line := range lines {
		_, _ = fmt.Fprintln(s.out, line)
	}
	_, _ = fmt.Fprint(s.out, "> ")
	answer, err := terminal.ReadText(s.in)
	if err != nil {
		return "", err
	}
	if answer == "" {
		_, _ = fmt.Fprintln(s.out)
	}
	return answer, nil
}

// run searches, shows the candidates and acts on the menu until the user creates leads, which
// ends the run, or quits.
func (s *findSession) run() error {
	for {
		candidates, err := s.searchOnce()
		if err != nil {
			return err
		}
		action, err := s.pick(candidates)
		if err != nil {
			return err
		}
		switch {
		case len(action.picks) > 0:
			return find.CreateLeads(s.api, s.out, candidates, action.picks, s.icp, s.enums)
		case action.feedback != "":
			s.search.AddFeedback(action.feedback)
		default:
			_, _ = fmt.Fprintln(s.out, "Nothing created.")
			return nil
		}
	}
}

// searchOnce is step 3: Claude Code runs once on the whole history of the search, and its
// candidates are recorded for the next brief.
func (s *findSession) searchOnce() ([]find.Candidate, error) {
	_, _ = fmt.Fprintln(s.out)
	_, _ = fmt.Fprintln(s.out, "Step 3 of 4"+stepDivider+"Claude searches the web (about 1–3 minutes, Ctrl-C stops it)")
	ctx, stop := signal.NotifyContext(s.cmd.Context(), os.Interrupt)
	result, err := runClaude(ctx, s.search.Brief(), s.schema, func(line string) {
		_, _ = fmt.Fprintln(s.out, "  "+line)
	})
	stop()
	if err != nil {
		if errors.Is(err, context.Canceled) {
			return nil, errors.New("stopped; nothing was created")
		}
		return nil, err
	}
	candidates, err := find.Decode(result)
	if err != nil {
		return nil, err
	}
	s.search.AddRound(candidates)
	return candidates, nil
}

// pick is step 4: it shows the candidates and asks until the user confirms picks, gives
// feedback for a new search, or quits, which returns an empty action.
func (s *findSession) pick(candidates []find.Candidate) (menuAction, error) {
	_, _ = fmt.Fprintln(s.out)
	_, _ = fmt.Fprintln(s.out, "Step 4 of 4"+stepDivider+"Pick the leads to create")
	skipped := find.ShowCandidates(s.out, candidates, s.known)
	pickable := len(skipped) < len(candidates)
	prompt := "Numbers to create (1,3 or 1-3 or all), r to search again with feedback, q to quit\n> "
	if !pickable {
		_, _ = fmt.Fprintln(s.out, "Claude found no new companies.")
		prompt = "r to search again with feedback, q to quit\n> "
	}
	for {
		answer, eof, err := terminal.Ask(s.in, s.src, s.out, prompt)
		if err != nil {
			return menuAction{}, err
		}
		if answer == "" {
			if eof {
				_, _ = fmt.Fprintln(s.out)
				return menuAction{}, nil
			}
			continue
		}
		choice, parseErr := find.ParseChoice(answer, len(candidates), skipped)
		if !pickable && (parseErr != nil || len(choice.Picks) > 0) {
			parseErr = errors.New("enter r or q")
		}
		switch {
		case parseErr != nil:
			_, _ = fmt.Fprintln(s.out, parseErr.Error())
			if eof {
				return menuAction{}, nil
			}
		case choice.Quit:
			return menuAction{}, nil
		case choice.Refine:
			feedback, err := s.askFeedback()
			if err != nil {
				return menuAction{}, err
			}
			if feedback != "" {
				return menuAction{feedback: feedback}, nil
			}
		default:
			confirmed, err := terminal.Confirm(s.in, s.src, s.out, confirmQuestion(candidates, choice.Picks, s.orgName))
			if err != nil {
				return menuAction{}, err
			}
			if confirmed {
				return menuAction{picks: choice.Picks}, nil
			}
			_, _ = fmt.Fprintln(s.out, "Not created.")
		}
	}
}

// confirmQuestion asks to create the picks, naming their contacts too when they have any.
func confirmQuestion(candidates []find.Candidate, picks []int, orgName string) string {
	contacts := find.CountContacts(candidates, picks)
	if contacts == 0 {
		return fmt.Sprintf("Create %d leads in %s?", len(picks), orgName)
	}
	return fmt.Sprintf("Create %d leads and %d contacts in %s?", len(picks), contacts, orgName)
}

// askFeedback asks for the feedback of a new search, or refuses when the run has used all its
// searches. An empty answer goes back to the menu.
func (s *findSession) askFeedback() (string, error) {
	if s.search.Searches() >= find.MaxSearches {
		_, _ = fmt.Fprintf(s.out, "This run has reached its limit of %d searches; pick from this list or run 'oba otak find' again.\n", find.MaxSearches)
		return "", nil
	}
	return s.askText("Your feedback (press Ctrl-D on an empty line to send; Ctrl-D right away goes back):")
}
