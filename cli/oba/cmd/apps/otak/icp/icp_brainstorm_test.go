package icp

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/otak/icp"
	"github.com/korurlabs/oba-cli/internal/connect/claude"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

const (
	questionsReply = `{"message":"Tell me more.","questions":["What do you charge?","Who buys it?"],"proposals":[]}`
	proposalA      = `{"name":"Dutch textile SMEs","industry":"MANUFACTURING","country":"NL","companySizeMin":"SIZE_11_50","companySizeMax":null,"targetPersonRole":null,"description":"Family-owned producers."}`
	proposalB      = `{"name":"German logistics firms","industry":"LOGISTICS","country":"DE","companySizeMin":null,"companySizeMax":null,"targetPersonRole":null,"description":"Carriers with depots."}`
	proposalsReply = `{"message":"Two profiles fit.","questions":[],"proposals":[` + proposalA + `,` + proposalB + `]}`
	startText      = "We sell security audits\n"
	orgPath        = "/organizations/" + clitest.OrgIDAcme
	emptyProfile   = `{"data":{"id":"` + clitest.OrgIDAcme + `","name":"Acme","description":null,"industry":null,"companySize":null,"country":null,"website":null}}`
	noIcps         = `{"data":[],"pagination":{"currentPage":1,"last":true}}`
)

// brainstormEnv is icpEnv with Claude Code answering each turn with the next reply; the last
// reply repeats once the list is used up.
type brainstormEnv struct {
	*icpEnv
	briefs []string
}

func newBrainstormEnv(t *testing.T, replies ...string) *brainstormEnv {
	t.Helper()
	e := &brainstormEnv{icpEnv: newIcpEnv(t)}
	e.api.Respond("GET "+orgPath, http.StatusOK, emptyProfile)
	e.api.Respond("GET /otak/icps", http.StatusOK, noIcps)
	runClaude = func(_ context.Context, brief, schema string, onProgress claude.ProgressFunc) (json.RawMessage, error) {
		e.briefs = append(e.briefs, brief)
		e.schema = schema
		if onProgress != nil {
			onProgress("Searching: audit market")
		}
		if e.runErr != nil {
			return nil, e.runErr
		}
		reply := replies[min(len(e.briefs), len(replies))-1]
		return json.RawMessage(reply), nil
	}
	return e
}

func (e *brainstormEnv) posts() []string {
	var bodies []string
	for _, call := range e.api.Calls() {
		if call == "POST /otak/icps" {
			bodies = append(bodies, call)
		}
	}
	return bodies
}

func brainstorm(t *testing.T, reads ...string) (string, string, error) {
	t.Helper()
	return runCLITyped(t, clitest.Typed(reads...), "otak", "icp", "brainstorm")
}

func TestBrainstormQuestionsThenCreate(t *testing.T) {
	e := newBrainstormEnv(t, questionsReply, proposalsReply)
	e.api.Respond("POST /otak/icps", http.StatusCreated, icpCreatedJSON)
	out, _, err := brainstorm(t, startText, clitest.CtrlD, "About 5000 euro,\n", "CEOs buy.\n", clitest.CtrlD, "1\n", "y\n", "q\n")
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{
		"Step 2 of 3 · Tell Claude about your company:",
		"  Searching: audit market\n",
		"Tell me more.\n  1. What do you charge?\n  2. Who buys it?\n",
		"Your answer (press Ctrl-D on an empty line to send; Ctrl-D right away ends the brainstorm):\n> ",
		"Two profiles fit.\nProposal 1:\n  name:              Dutch textile SMEs\n",
		"Proposal 2:\n  name:              German logistics firms\n",
		"Number to create (1-2), r to refine, q to quit: Draft ICP:\n",
		"Create this ICP? (y/n) Created ICP Dutch textile SMEs (" + icpID + ")\n",
		"Number to create (1-2), r to refine, q to quit: Brainstorm ended.\n",
	} {
		if !strings.Contains(out, want) {
			t.Errorf("out lacks %q:\n%s", want, out)
		}
	}
	if len(e.briefs) != 2 {
		t.Fatalf("runs = %d", len(e.briefs))
	}
	if !strings.Contains(e.briefs[0], "[User]\nWe sell security audits\n") ||
		!strings.Contains(e.briefs[0], `"industry": "SOFTWARE" | "MANUFACTURING" | "LOGISTICS" | "OTHER",  (required)`) {
		t.Errorf("first brief:\n%s", e.briefs[0])
	}
	if !strings.Contains(e.briefs[1], "[You]\nMessage: Tell me more.\nQuestion 1: What do you charge?\nQuestion 2: Who buys it?\n\n[User]\nAbout 5000 euro,\nCEOs buy.\n") {
		t.Errorf("second brief:\n%s", e.briefs[1])
	}
	var sent, want map[string]any
	if err := json.Unmarshal([]byte(e.api.Request("POST /otak/icps").Body), &sent); err != nil {
		t.Fatal(err)
	}
	if err := json.Unmarshal([]byte(proposalA), &want); err != nil {
		t.Fatal(err)
	}
	if !jsonEqual(sent, want) {
		t.Errorf("sent %v, want %v", sent, want)
	}
}

func TestBrainstormSchemaWrapsLiveRequest(t *testing.T) {
	e := newBrainstormEnv(t, questionsReply)
	if _, _, err := brainstorm(t, startText, clitest.CtrlD, clitest.CtrlD); err != nil {
		t.Fatal(err)
	}
	var schema struct {
		Required   []string `json:"required"`
		Properties struct {
			Proposals struct {
				Items struct {
					AdditionalProperties bool           `json:"additionalProperties"`
					Required             []string       `json:"required"`
					Properties           map[string]any `json:"properties"`
				} `json:"items"`
			} `json:"proposals"`
		} `json:"properties"`
	}
	if err := json.Unmarshal([]byte(e.schema), &schema); err != nil {
		t.Fatal(err)
	}
	items := schema.Properties.Proposals.Items
	if strings.Join(schema.Required, ",") != "message,questions,proposals" || items.AdditionalProperties ||
		strings.Join(items.Required, ",") != "name,industry" || len(items.Properties) != 7 {
		t.Errorf("schema = %s", e.schema)
	}
}

// Refining sends the feedback with the earlier proposals and the ICPs already created.
func TestBrainstormRefineAfterCreatingSeveral(t *testing.T) {
	e := newBrainstormEnv(t, proposalsReply)
	e.api.Respond("POST /otak/icps", http.StatusCreated, icpCreatedJSON)
	out, _, err := brainstorm(t, startText, clitest.CtrlD, "1\n", "y\n", "2\n", "yes\n", "1\n", "r\n", "Smaller firms.\n", clitest.CtrlD, "q\n")
	if err != nil {
		t.Fatal(err)
	}
	if len(e.posts()) != 2 || !strings.Contains(out, "Proposal 1 is already created.\n") {
		t.Errorf("posts %v out:\n%s", e.posts(), out)
	}
	if len(e.briefs) != 2 {
		t.Fatalf("runs = %d", len(e.briefs))
	}
	for _, want := range []string{
		"Proposal 1: {",
		`"name":"German logistics firms"`,
		"[Note]\nThe user created the ICP \"Dutch textile SMEs\".",
		"[User]\nSmaller firms.\n",
	} {
		if !strings.Contains(e.briefs[1], want) {
			t.Errorf("second brief lacks %q:\n%s", want, e.briefs[1])
		}
	}
}

func TestBrainstormDeclineCreatesNothing(t *testing.T) {
	e := newBrainstormEnv(t, proposalsReply)
	out, _, err := brainstorm(t, startText, clitest.CtrlD, "2\n", "n\n", "q\n")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Create this ICP? (y/n) Not created.\nNumber to create (1-2), r to refine, q to quit: Brainstorm ended.\n") {
		t.Errorf("out:\n%s", out)
	}
	if len(e.posts()) != 0 || len(e.briefs) != 1 {
		t.Errorf("posts %v runs %d", e.posts(), len(e.briefs))
	}
}

func TestBrainstormEnds(t *testing.T) {
	tests := []struct {
		name    string
		reads   []string
		runs    int
		wantEnd string
	}{
		{"Ctrl-D at once cancels", []string{clitest.CtrlD}, 0, "Press Ctrl-D right away to cancel.\n> \nCancelled.\n"},
		{"empty answer", []string{startText, clitest.CtrlD, clitest.CtrlD}, 1, "Ctrl-D right away ends the brainstorm):\n> \nBrainstorm ended.\n"},
		{"q", []string{startText, clitest.CtrlD, "Q\n"}, 1, "q to quit: Brainstorm ended.\n"},
		{"end of input at menu", []string{startText, clitest.CtrlD}, 1, "q to quit: \nBrainstorm ended.\n"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			reply := proposalsReply
			if tt.name == "empty answer" {
				reply = questionsReply
			}
			e := newBrainstormEnv(t, reply)
			out, _, err := brainstorm(t, tt.reads...)
			if err != nil {
				t.Fatal(err)
			}
			if !strings.HasSuffix(out, tt.wantEnd) || len(e.briefs) != tt.runs {
				t.Errorf("runs %d out:\n%s", len(e.briefs), out)
			}
			if len(e.posts()) != 0 {
				t.Errorf("posts = %v", e.posts())
			}
		})
	}
}

// Empty feedback goes back to the menu without a turn.
func TestBrainstormEmptyFeedbackGoesBack(t *testing.T) {
	e := newBrainstormEnv(t, proposalsReply)
	out, _, err := brainstorm(t, startText, clitest.CtrlD, "r\n", clitest.CtrlD, "q\n")
	if err != nil {
		t.Fatal(err)
	}
	if len(e.briefs) != 1 || !strings.Contains(out, "Ctrl-D right away goes back):\n> \nNumber to create (1-2)") {
		t.Errorf("runs %d out:\n%s", len(e.briefs), out)
	}
}

func TestBrainstormTurnCap(t *testing.T) {
	e := newBrainstormEnv(t, questionsReply)
	reads := []string{startText, clitest.CtrlD}
	for range icp.MaxBrainstormTurns + 3 {
		reads = append(reads, "more\n", clitest.CtrlD)
	}
	out, _, err := brainstorm(t, reads...)
	if err != nil {
		t.Fatal(err)
	}
	if len(e.briefs) != icp.MaxBrainstormTurns {
		t.Errorf("runs = %d", len(e.briefs))
	}
	if !strings.HasSuffix(out, "This brainstorm has reached its limit of 12 Claude Code turns; run 'oba otak icp brainstorm' again to start a new one.\n") {
		t.Errorf("out ends:\n%s", out[max(0, len(out)-300):])
	}
}

func TestBrainstormSkipsInvalidProposals(t *testing.T) {
	e := newBrainstormEnv(t, `{"message":"Three.","questions":[],"proposals":[`+
		`{"name":"x","industry":"OTHER","website":"https://x.nl"},`+
		`{"name":"y","industry":null},`+
		proposalB+`]}`)
	out, _, err := brainstorm(t, startText, clitest.CtrlD, "2\n", "1\n", "n\n", "r\n", "Again.\n", clitest.CtrlD, "q\n")
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{
		`Skipped a proposal: the draft of Claude Code has a field the API does not accept: unknown field "website"`,
		`Skipped a proposal: the proposal of Claude Code lacks the required field "industry"`,
		"Proposal 1:\n  name:              German logistics firms\n",
		"Number to create (1-1), r to refine, q to quit: Enter a number from 1 to 1, r or q.\n",
	} {
		if !strings.Contains(out, want) {
			t.Errorf("out lacks %q:\n%s", want, out)
		}
	}
	if strings.Contains(out, "Proposal 2:") {
		t.Errorf("an invalid proposal was shown:\n%s", out)
	}
	if !strings.Contains(e.briefs[1], "(2 further proposals did not fit the fields and were not shown to the user.)") ||
		strings.Contains(e.briefs[1], "https://x.nl") {
		t.Errorf("second brief:\n%s", e.briefs[1])
	}
}

// Input waiting before a menu question is discarded and junk asks again.
func TestBrainstormMenuDiscardsPendingAndRejectsJunk(t *testing.T) {
	e := newBrainstormEnv(t, proposalsReply)
	out, _, err := brainstorm(t, startText, clitest.CtrlD, "1\ny\n", "n\n", "create\n", "0\n", "q\n")
	if err != nil {
		t.Fatal(err)
	}
	if len(e.posts()) != 0 {
		t.Errorf("the pasted y answered the confirmation: %v", e.posts())
	}
	if strings.Count(out, "Enter a number from 1 to 2, r or q.\n") != 2 || !strings.HasSuffix(out, "Brainstorm ended.\n") {
		t.Errorf("out:\n%s", out)
	}
}

func TestBrainstormNameTakenGoesOn(t *testing.T) {
	e := newBrainstormEnv(t, proposalsReply)
	e.api.Respond("POST /otak/icps", http.StatusConflict,
		`{"error":{"code":"OTK_STA_005","message":"An ICP with this name already exists"}}`)
	out, errOut, err := brainstorm(t, startText, clitest.CtrlD, "1\n", "y\n", "q\n")
	if err != nil {
		t.Fatal(err)
	}
	if errOut != "Not created: OTK_STA_005: An ICP with this name already exists\n" || !strings.HasSuffix(out, "Brainstorm ended.\n") {
		t.Errorf("stderr %q out:\n%s", errOut, out)
	}
	if len(e.posts()) != 1 {
		t.Errorf("posts = %v", e.posts())
	}
}

func TestBrainstormForbiddenStops(t *testing.T) {
	e := newBrainstormEnv(t, proposalsReply)
	e.api.Respond("POST /otak/icps", http.StatusForbidden, `{"error":{"code":"ORG_SEC_003","message":"Insufficient role"}}`)
	_, _, err := brainstorm(t, startText, clitest.CtrlD, "1\n", "y\n", "q\n")
	if err == nil || err.Error() != "ORG_SEC_003: Insufficient role" {
		t.Fatalf("err = %v", err)
	}
}

func TestBrainstormStopped(t *testing.T) {
	e := newBrainstormEnv(t, proposalsReply)
	e.runErr = context.Canceled
	_, _, err := brainstorm(t, startText, clitest.CtrlD)
	if err == nil || err.Error() != "stopped; nothing was created" {
		t.Fatalf("err = %v", err)
	}
}

func TestBrainstormNotAReply(t *testing.T) {
	newBrainstormEnv(t, `["x"]`)
	_, _, err := brainstorm(t, startText, clitest.CtrlD)
	if err == nil || !strings.Contains(err.Error(), "not one JSON object") {
		t.Fatalf("err = %v", err)
	}
}

func TestBrainstormNeedsTerminal(t *testing.T) {
	tests := []struct {
		name          string
		stdin, stdout bool
	}{{"piped stdin", false, true}, {"redirected stdout", true, false}}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			e := newBrainstormEnv(t, proposalsReply)
			clitest.FakeTerminal(t, &stdinIsTerminal, tt.stdin)
			stdoutIsTerminal = func() bool { return tt.stdout }
			_, _, err := runCLI(t, "We sell audits\n", "otak", "icp", "brainstorm")
			if !errors.Is(err, errIcpBrainstormNotInteractive) {
				t.Fatalf("err = %v", err)
			}
			if len(e.briefs) != 0 || len(e.api.Calls()) != 0 {
				t.Errorf("runs %d calls %v", len(e.briefs), e.api.Calls())
			}
		})
	}
}

func TestBrainstormNeedsClaude(t *testing.T) {
	e := newBrainstormEnv(t, proposalsReply)
	claudeInstalled = func() error { return claude.ErrNotInstalled }
	_, _, err := brainstorm(t, startText, clitest.CtrlD)
	if !errors.Is(err, claude.ErrNotInstalled) || len(e.briefs) != 0 {
		t.Fatalf("err = %v, runs %d", err, len(e.briefs))
	}
}

const (
	knownProfile = `{"data":{"id":"` + clitest.OrgIDAcme + `","name":"Acme","description":"Security audits\nfor banks.","industry":"SOFTWARE","companySize":null,"country":"NL","website":"https://acme.example","memberCount":7}}`
	knownIcps    = `{"data":[{"id":"i1","name":"Benelux banks","industry":"FINANCE","country":"NL","companySizeMin":"SIZE_51_200","companySizeMax":null,"targetPersonRole":"CISO","description":"Regional banks under DNB rules."}],"pagination":{"currentPage":1,"last":true}}`
)

// The first brief carries the profile and a compact form of each ICP, read through the
// organization and ICP endpoints only.
func TestBrainstormSendsContextInFirstBrief(t *testing.T) {
	e := newBrainstormEnv(t, proposalsReply)
	e.api.Respond("GET "+orgPath, http.StatusOK, knownProfile)
	e.api.Respond("GET /otak/icps", http.StatusOK, knownIcps)
	out, _, err := brainstorm(t, "We also serve insurers.\n", clitest.CtrlD, "q\n")
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{
		"Brainstorm ICPs with Claude: find out who your ideal customers are.\n\n" +
			"Step 1 of 3 · What Oba knows about Acme\n" +
			"  description:   Security audits\n" +
			"                 for banks.\n" +
			"  industry:      SOFTWARE\n" +
			"  company size:  -   (empty)\n" +
			"  country:       NL\n" +
			"  website:       https://acme.example   (Claude will read this site)\n" +
			"  ICPs (1):\n" +
			"    1. Benelux banks  FINANCE  NL  at least SIZE_51_200  CISO\n" +
			"\nStep 2 of 3 · Tell Claude more (optional)\n",
		"> \nStep 3 of 3 · Claude asks a few questions, then proposes ICPs.\n" +
			"  You pick one to create; nothing is saved without your \"y\".\nThinking with Claude Code.",
	} {
		if !strings.Contains(out, want) {
			t.Errorf("out lacks %q:\n%s", want, out)
		}
	}
	if strings.Contains(out, "Tell Claude about your company") {
		t.Errorf("asked the fallback question:\n%s", out)
	}
	if calls := strings.Join(e.api.Calls(), ","); calls != "GET "+orgPath+",GET /otak/icps" {
		t.Errorf("calls = %s", calls)
	}
	if len(e.briefs) != 1 {
		t.Fatalf("runs = %d", len(e.briefs))
	}
	brief := e.briefs[0]
	for _, want := range []string{
		`"description": "Security audits\nfor banks."`,
		`"website": "https://acme.example"`,
		`"name": "Benelux banks"`,
		`"targetPersonRole": "CISO"`,
		"[User]\nWe also serve insurers.\n",
	} {
		if !strings.Contains(brief, want) {
			t.Errorf("brief lacks %q:\n%s", want, brief)
		}
	}
	if strings.Contains(out, "No description yet.") {
		t.Errorf("note shown with a description:\n%s", out)
	}
	for _, unwanted := range []string{"Regional banks under DNB rules.", "memberCount", "relations"} {
		if strings.Contains(brief, unwanted) {
			t.Errorf("brief holds %q:\n%s", unwanted, brief)
		}
	}
}

// With context, nothing typed at the first question goes on with the context alone.
func TestBrainstormEmptyAnswerProceedsWithContext(t *testing.T) {
	e := newBrainstormEnv(t, proposalsReply)
	e.api.Respond("GET /otak/icps", http.StatusOK, knownIcps)
	out, _, err := brainstorm(t, clitest.CtrlD, "q\n")
	if err != nil {
		t.Fatal(err)
	}
	want := "Brainstorm ICPs with Claude: find out who your ideal customers are.\n" +
		"\n" +
		"Step 1 of 3 · What Oba knows about Acme\n" +
		"  description:   -   (empty)\n" +
		"  industry:      -   (empty)\n" +
		"  company size:  -   (empty)\n" +
		"  country:       -   (empty)\n" +
		"  website:       -   (empty)\n" +
		"  ICPs (1):\n" +
		"    1. Benelux banks  FINANCE  NL  at least SIZE_51_200  CISO\n" +
		"\n" +
		"  ! No description yet. You can add one in the next step.\n" +
		"    It is used for this brainstorm only; to keep it, add it to the Acme profile in Oba.\n" +
		"\n" +
		"Step 2 of 3 · Tell Claude more (optional)\n" +
		"  What do you sell, at what price, to whom, and where?\n" +
		"  Example: On-site Cyberscan, 85 checks, €695, for Dutch MKB in Zuid-Holland.\n" +
		"  Type or paste, then press Ctrl-D on an empty line to send.\n" +
		"  Press Ctrl-D right away to skip.\n" +
		"> \n" +
		"\n" +
		"Step 3 of 3 · Claude asks a few questions, then proposes ICPs.\n" +
		"  You pick one to create; nothing is saved without your \"y\".\n" +
		"Thinking with Claude Code."
	if !strings.HasPrefix(out, want) || strings.Contains(out, "Cancelled.") {
		t.Errorf("got:\n%s\nwant prefix:\n%s", out, want)
	}
	if len(e.briefs) != 1 || !strings.Contains(e.briefs[0], "(empty: the user added nothing to what Oba knows)") ||
		strings.Contains(e.briefs[0], "[User]") {
		t.Errorf("runs %d briefs %v", len(e.briefs), e.briefs)
	}
}

// An empty profile and no ICPs ask about the company in the same steps, and nothing typed cancels.
func TestBrainstormWithoutContextAsksAboutCompany(t *testing.T) {
	e := newBrainstormEnv(t, proposalsReply)
	out, _, err := brainstorm(t, clitest.CtrlD)
	if err != nil {
		t.Fatal(err)
	}
	want := "Brainstorm ICPs with Claude: find out who your ideal customers are.\n" +
		"\n" +
		"Step 1 of 3 · Oba has no profile for Acme yet.\n" +
		"\n" +
		"Step 2 of 3 · Tell Claude about your company: what you sell, at what price, to whom, and where.\n" +
		"  Example: On-site Cyberscan, 85 checks, €695, for Dutch MKB in Zuid-Holland.\n" +
		"  Type or paste, then press Ctrl-D on an empty line to send.\n" +
		"  Press Ctrl-D right away to cancel.\n" +
		"> \n" +
		"Cancelled.\n"
	if out != want {
		t.Errorf("got:\n%s\nwant:\n%s", out, want)
	}
	if len(e.briefs) != 0 {
		t.Errorf("runs = %d", len(e.briefs))
	}
}

func TestBrainstormContextCallFailureStops(t *testing.T) {
	tests := []struct {
		name, route, body string
		status            int
		want              string
	}{
		{"profile forbidden", "GET " + orgPath, `{"error":{"code":"ORG_SEC_003","message":"Insufficient role"}}`, http.StatusForbidden,
			"failed to read the organization profile: ORG_SEC_003: Insufficient role"},
		{"ICPs fail", "GET /otak/icps", `{"error":{"code":"GEN_001","message":"Server error"}}`, http.StatusInternalServerError,
			"failed to read the ICPs: GEN_001: Server error"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			e := newBrainstormEnv(t, proposalsReply)
			e.api.Respond(tt.route, tt.status, tt.body)
			out, _, err := brainstorm(t, startText, clitest.CtrlD, "q\n")
			if err == nil || err.Error() != tt.want {
				t.Fatalf("err = %v", err)
			}
			if len(e.briefs) != 0 || strings.Contains(out, "Tell about your company") {
				t.Errorf("runs %d out:\n%s", len(e.briefs), out)
			}
		})
	}
}
