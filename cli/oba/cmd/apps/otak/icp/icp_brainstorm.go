package icp

import (
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"strings"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/otak/icp"
	"github.com/korurlabs/oba-cli/internal/dojo/apidocs"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
	"github.com/korurlabs/oba-cli/internal/shrine/session"
)

var icpBrainstormHelp = fmt.Sprintf(`Works out ICPs (Ideal Customer Profiles) with you in a conversation with Claude Code, on this
machine and under your own Claude account, and creates the ones you pick.

It runs in three steps. Step 1 shows what Oba knows about your company: the organization
profile (it needs the admin or owner role to read it) and the existing ICPs, of which it sends
only name, industry, country, size range and role. In step 2 you tell Claude more: what you
sell, at what price, to whom and where. That text is used for this brainstorm only and never
written to the profile. With an empty profile and no ICPs, step 2 is where you tell about your
company. In step 3 Claude Code reads your website, asks questions until it knows enough, then
proposes two or three ICPs with the fields and allowed values of the live API. Pick a proposal
by number to see it and create it after a y; pick several in turn if you like. r gives Claude
Code feedback and it revises the proposals; q ends the brainstorm.

Answers and feedback may span several lines; press Ctrl-D on an empty line to send them.
Ctrl-D right away skips step 2 when Oba knows your company and cancels it otherwise, ends the
brainstorm at an answer and goes back to the menu at feedback. Ctrl-C stops the brainstorm at
any point; the ICPs created by then stay. One brainstorm has at most %d Claude Code turns.

The command is interactive and needs Claude Code (https://claude.com/claude-code), signed in
once by running 'claude'.`, icp.MaxBrainstormTurns)

// errIcpBrainstormNotInteractive is printed as it is, so it keeps the full stop.
var errIcpBrainstormNotInteractive = errors.New("oba otak icp brainstorm is a conversation; run it in a terminal, with nothing piped to stdin.") //nolint:staticcheck // ST1005: shown to the user verbatim

const (
	menuRefine = "r"
	menuQuit   = "q"
)

func newIcpBrainstormCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "brainstorm",
		Short: "Work out ICPs in a conversation with Claude Code and create the ones you pick",
		Long:  icpBrainstormHelp,
		Args:  cobra.NoArgs,
		RunE:  runIcpBrainstorm,
	}
}

// brainstormSession is one run of the command: the conversation, the terminal it is held on
// and the ICPs created so far.
type brainstormSession struct {
	cmd     *cobra.Command
	api     *client.Client
	fields  []apidocs.Field
	talk    *icp.Brainstorm
	schema  string
	src     io.Reader
	in      *bufio.Reader
	out     io.Writer
	created int
}

func runIcpBrainstorm(cmd *cobra.Command, _ []string) error {
	// Each run may create several records; --json lists all of them.
	jsonout.CollectWrites()
	if !stdinIsTerminal() || !stdoutIsTerminal() {
		return errIcpBrainstormNotInteractive
	}
	if err := claudeInstalled(); err != nil {
		return err
	}
	c, err := session.OrganizationClient()
	if err != nil {
		return err
	}
	fields, err := icp.LoadFields(c.BaseURL, icp.CreateFields)
	if err != nil {
		return err
	}
	schema, err := icp.BrainstormSchema(fields)
	if err != nil {
		return err
	}
	out := cmd.OutOrStdout()
	known, err := icp.LoadBrainstormContext(c)
	if err != nil {
		return err
	}
	src := cmd.InOrStdin()
	s := &brainstormSession{
		cmd: cmd, api: c, fields: fields, talk: icp.NewBrainstorm(fields, known), schema: schema,
		src: src, in: bufio.NewReader(src), out: out,
	}
	if proceed, err := s.open(known); err != nil || !proceed {
		return err
	}
	return s.run()
}

// open walks the user through the first two steps: what Oba knows about the company, then
// what the user wants to add. When Oba knows what the company does, nothing typed goes on with
// that alone; else the user has to tell about the company, and nothing typed cancels. What the
// user types goes to Claude Code only and is never written to the organization profile.
func (s *brainstormSession) open(known icp.BrainstormContext) (bool, error) {
	_, _ = fmt.Fprintln(s.out, "Brainstorm ICPs with Claude: find out who your ideal customers are.")
	_, _ = fmt.Fprintln(s.out)
	icp.PrintBrainstormContext(s.out, known)
	_, _ = fmt.Fprintln(s.out)
	question, ending := "Step 2 of 3 · Tell Claude more (optional)\n  What do you sell, at what price, to whom, and where?", "skip"
	if !known.Describes() {
		question, ending = "Step 2 of 3 · Tell Claude about your company: what you sell, at what price, to whom, and where.", "cancel"
	}
	start, err := s.askText(question,
		"  Example: On-site Cyberscan, 85 checks, €695, for Dutch MKB in Zuid-Holland.",
		"  Type or paste, then press Ctrl-D on an empty line to send.",
		"  Press Ctrl-D right away to "+ending+".")
	if err != nil {
		return false, err
	}
	switch {
	case start != "":
		s.talk.AddUser(start)
	case !known.Describes():
		_, _ = fmt.Fprintln(s.out, "Cancelled.")
		return false, nil
	}
	_, _ = fmt.Fprintln(s.out)
	_, _ = fmt.Fprintln(s.out, "Step 3 of 3 · Claude asks a few questions, then proposes ICPs.")
	_, _ = fmt.Fprintln(s.out, `  You pick one to create; nothing is saved without your "y".`)
	return true, nil
}

// askText prints the question lines and the "> " marker, then reads text up to Ctrl-D on an
// empty line. Ctrl-D right away leaves the cursor after the marker, so an empty answer ends
// the line before anything else is printed.
func (s *brainstormSession) askText(lines ...string) (string, error) {
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

func (s *brainstormSession) run() error {
	for {
		if s.talk.Turns() >= icp.MaxBrainstormTurns {
			_, _ = fmt.Fprintf(s.out, "This brainstorm has reached its limit of %d Claude Code turns; run 'oba otak icp brainstorm' again to start a new one.\n", icp.MaxBrainstormTurns)
			return nil
		}
		reply, err := s.turn()
		if err != nil {
			return err
		}
		s.printReply(reply)

		var next string
		if len(reply.Proposals) == 0 {
			if next, err = s.askText("Your answer (press Ctrl-D on an empty line to send; Ctrl-D right away ends the brainstorm):"); err != nil {
				return err
			}
		} else if next, err = s.menu(reply.Proposals); err != nil {
			return err
		}
		if next == "" {
			_, _ = fmt.Fprintln(s.out, "Brainstorm ended.")
			return nil
		}
		s.talk.AddUser(next)
	}
}

// turn runs Claude Code once on the whole conversation and records its reply.
func (s *brainstormSession) turn() (icp.Reply, error) {
	_, _ = fmt.Fprintln(s.out, "Thinking with Claude Code. Ctrl-C stops it.")
	ctx, stop := signal.NotifyContext(s.cmd.Context(), os.Interrupt)
	result, err := runClaude(ctx, s.talk.Brief(), s.schema, func(line string) {
		_, _ = fmt.Fprintln(s.out, "  "+line)
	})
	stop()
	if err != nil {
		if errors.Is(err, context.Canceled) {
			if s.created > 0 {
				return icp.Reply{}, errors.New("stopped; the ICPs created before stay")
			}
			return icp.Reply{}, errors.New("stopped; nothing was created")
		}
		return icp.Reply{}, err
	}
	reply, err := icp.DecodeReply(result, s.fields)
	if err != nil {
		return icp.Reply{}, err
	}
	s.talk.AddReply(reply)
	return reply, nil
}

func (s *brainstormSession) printReply(reply icp.Reply) {
	if reply.Message != "" {
		_, _ = fmt.Fprintln(s.out, reply.Message)
	}
	for i, question := range reply.Questions {
		_, _ = fmt.Fprintf(s.out, "%3d. %s\n", i+1, question)
	}
	for _, reason := range reply.Skipped {
		_, _ = fmt.Fprintf(s.out, "Skipped a proposal: %s\n", reason)
	}
	for i, proposal := range reply.Proposals {
		icp.PrintProposal(s.out, i+1, proposal, s.fields)
	}
}

// menu lets the user create proposals one by one until they ask to refine, which returns the
// feedback, or to quit, which returns "".
func (s *brainstormSession) menu(proposals []map[string]json.RawMessage) (string, error) {
	done := make(map[int]bool)
	prompt := fmt.Sprintf("Number to create (1-%d), r to refine, q to quit: ", len(proposals))
	for {
		answer, eof, err := terminal.Ask(s.in, s.src, s.out, prompt)
		if err != nil {
			return "", err
		}
		answer = strings.ToLower(answer)
		number, convErr := strconv.Atoi(answer)
		switch {
		case answer == menuQuit:
			return "", nil
		case answer == menuRefine:
			feedback, err := s.askText("Your feedback on the proposals (press Ctrl-D on an empty line to send; Ctrl-D right away goes back):")
			if err != nil {
				return "", err
			}
			if feedback != "" {
				return feedback, nil
			}
			continue
		case convErr == nil && number >= 1 && number <= len(proposals):
			if done[number] {
				_, _ = fmt.Fprintf(s.out, "Proposal %d is already created.\n", number)
				continue
			}
			created, err := s.create(proposals[number-1])
			if err != nil {
				return "", err
			}
			done[number] = created
			continue
		}
		if eof {
			_, _ = fmt.Fprintln(s.out)
			return "", nil
		}
		_, _ = fmt.Fprintf(s.out, "Enter a number from 1 to %d, r or q.\n", len(proposals))
	}
}

// create shows a proposal as a draft and creates it once the user confirms. A refusal of the
// API, such as a name already taken, is shown and the brainstorm goes on, so that the user can
// pick another proposal or ask for a revision.
func (s *brainstormSession) create(proposal map[string]json.RawMessage) (bool, error) {
	icp.PrintDraft(s.out, proposal, s.fields)
	confirmed, err := terminal.Confirm(s.in, s.src, s.out, "Create this ICP?")
	if err != nil {
		return false, err
	}
	if !confirmed {
		_, _ = fmt.Fprintln(s.out, "Not created.")
		return false, nil
	}
	body, err := json.Marshal(proposal)
	if err != nil {
		return false, fmt.Errorf("failed to encode the proposal: %w", err)
	}
	created, err := icp.Create(s.api, s.fields, body, proposal)
	if err != nil {
		if !proposalRefused(err) {
			return false, err
		}
		_, _ = fmt.Fprintf(s.cmd.ErrOrStderr(), "Not created: %v\n", err)
		return false, nil
	}
	s.created++
	s.talk.AddCreated(created.Name)
	_, _ = fmt.Fprintf(s.out, "Created ICP %s (%s)\n", created.Name, created.ID)
	return true, nil
}

// proposalRefused reports whether the API refused the proposal itself, for its content, such as
// a name already taken or an invalid value. A missing right, a lost sign-in or a failing server
// ends the brainstorm instead, since every further create would fail the same way.
func proposalRefused(err error) bool {
	var apiErr *client.APIError
	if !errors.As(err, &apiErr) {
		return false
	}
	switch apiErr.Status {
	case http.StatusBadRequest, http.StatusConflict, http.StatusUnprocessableEntity:
		return true
	default:
		return false
	}
}
