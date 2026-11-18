package find

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/otak/contact"
	"github.com/korurlabs/oba-cli/internal/apps/otak/find"
	"github.com/korurlabs/oba-cli/internal/connect/claude"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

const (
	findDocsFile = "testdata/api-docs-find.json"
	icpAlphaID   = "aaaa1111-1111-4111-8111-111111111111"
	icpBetaID    = "bbbb2222-2222-4222-8222-222222222222"
)

// findEnv is a signed-in find run against the mock API with the runner stubbed.
type findEnv struct {
	api        *clitest.MockAPI
	mu         sync.Mutex
	brief      string
	schema     string
	runs       int
	candidates []find.Candidate
	runErr     error
	relQueries []url.Values
	icpPages   []string
}

// newFindEnv serves two ICPs over two pages, one known relation per type (and a lost lead),
// the api document with the Country enum, and a terminal on stdin and stdout.
func newFindEnv(t *testing.T) *findEnv {
	t.Helper()
	clitest.SetupHome(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	oldOut, oldInstalled, oldRun := stdoutIsTerminal, claudeInstalled, runClaude
	stdoutIsTerminal = func() bool { return true }
	claudeInstalled = func() error { return nil }
	t.Cleanup(func() { stdoutIsTerminal, claudeInstalled, runClaude = oldOut, oldInstalled, oldRun })

	e := &findEnv{api: clitest.NewMockAPI(t)}
	e.api.ServeDocs(findDocsFile)
	e.api.Respond("GET /users/me", http.StatusOK, clitest.UserMeJSON)
	e.api.Handle("GET /otak/icps", func(w http.ResponseWriter, r *http.Request) {
		page := r.URL.Query().Get("page")
		e.mu.Lock()
		e.icpPages = append(e.icpPages, page+"/"+r.URL.Query().Get("amount"))
		e.mu.Unlock()
		if page == "1" {
			clitest.WriteJSON(w, `{"data":[{"id":"`+icpAlphaID+`","name":"Alpha","industry":"Software","country":"NL"}],"pagination":{"currentPage":1,"last":false}}`)
			return
		}
		clitest.WriteJSON(w, `{"data":[{"id":"`+icpBetaID+`","name":"Beta","industry":"Logistics","country":null}],"pagination":{"currentPage":2,"last":true}}`)
	})
	e.api.Respond("GET /otak/icps/"+icpAlphaID, 200, `{"data":{"id":"`+icpAlphaID+`","name":"Alpha","industry":"Software","country":"NL","description":"SaaS for clinics"}}`)
	e.api.Respond("GET /otak/icps/"+icpBetaID, 200, `{"data":{"id":"`+icpBetaID+`","name":"Beta","industry":"Logistics","country":null}}`)
	e.api.Handle("GET /otak/relations", func(w http.ResponseWriter, r *http.Request) {
		q := r.URL.Query()
		e.mu.Lock()
		e.relQueries = append(e.relQueries, q)
		e.mu.Unlock()
		switch q.Get("type") {
		case "LEAD":
			clitest.WriteJSON(w, `{"data":[{"id":"r1","name":"Known Lead","website":"https://known-lead.nl"},{"id":"r2","name":"Lost Co","website":null}],"pagination":{"currentPage":1,"last":true}}`)
		case "CUSTOMER":
			clitest.WriteJSON(w, `{"data":[{"id":"r3","name":"Big Customer","website":"https://www.bigcustomer.com/"}],"pagination":{"currentPage":1,"last":true}}`)
		default:
			clitest.WriteJSON(w, `{"data":[],"pagination":{"currentPage":1,"last":true}}`)
		}
	})
	clitest.LoginTo(t, e.api.URL(), clitest.OrgIDAcme)

	e.candidates = []find.Candidate{
		{Name: "Newco", Website: "https://newco.io", Country: "NL", Reason: "Builds clinic software"},
		{Name: "known lead", Website: "https://other.example", Country: "DE", Reason: "same name"},
		{Name: "Hostmatch", Website: "http://bigcustomer.com/x", Country: "BE", Reason: "same host"},
		{Name: "Oddco", Website: "not a url", Country: "XX", Reason: "bad fields"},
	}
	runClaude = func(_ context.Context, brief, schema string, onProgress claude.ProgressFunc) (json.RawMessage, error) {
		e.mu.Lock()
		defer e.mu.Unlock()
		e.runs++
		e.brief, e.schema = brief, schema
		if onProgress != nil {
			onProgress("Searching: clinic software")
		}
		if e.runErr != nil {
			return nil, e.runErr
		}
		result, err := json.Marshal(map[string]any{"candidates": e.candidates})
		if err != nil {
			t.Fatal(err)
		}
		return result, nil
	}
	return e
}

func (e *findEnv) posts() []string {
	var out []string
	for _, r := range e.api.Requests() {
		if r.Method == "POST" && r.Path == "/otak/relations" {
			out = append(out, r.Body)
		}
	}
	return out
}

func (e *findEnv) createOK() {
	var n int
	e.api.Handle("POST /otak/relations", func(w http.ResponseWriter, _ *http.Request) {
		e.mu.Lock()
		n++
		id := "new-" + strconv.Itoa(n)
		e.mu.Unlock()
		w.WriteHeader(http.StatusCreated)
		clitest.WriteJSON(w, `{"data":{"id":"`+id+`","name":"x","type":"LEAD"}}`)
	})
}

func TestFindClaudeMissing(t *testing.T) {
	e := newFindEnv(t)
	claudeInstalled = claude.Installed
	t.Setenv("PATH", t.TempDir())
	_, _, err := runCLILines(t, "1\n", "otak", "find")
	if !errors.Is(err, claude.ErrNotInstalled) {
		t.Fatalf("err = %v", err)
	}
	if !strings.HasSuffix(err.Error(), "sign in.") {
		t.Errorf("message = %q", err)
	}
	if len(e.api.Calls()) != 0 || e.runs != 0 {
		t.Errorf("calls %v runs %d", e.api.Calls(), e.runs)
	}
}

func TestFindNeedsTerminal(t *testing.T) {
	tests := []struct {
		name          string
		stdin, stdout bool
	}{{"stdin piped", false, true}, {"stdout piped", true, false}}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			e := newFindEnv(t)
			clitest.FakeTerminal(t, &stdinIsTerminal, tt.stdin)
			stdoutIsTerminal = func() bool { return tt.stdout }
			_, _, err := runCLILines(t, "", "otak", "find")
			if !errors.Is(err, errFindNotInteractive) {
				t.Fatalf("err = %v", err)
			}
			if len(e.api.Calls()) != 0 {
				t.Errorf("calls = %v", e.api.Calls())
			}
		})
	}
}

func TestFindNoIcps(t *testing.T) {
	e := newFindEnv(t)
	e.api.Respond("GET /otak/icps", 200, `{"data":[],"pagination":{"currentPage":1,"last":true}}`)
	_, _, err := runCLILines(t, "", "otak", "find")
	if err == nil || !strings.Contains(err.Error(), "Acme has no ICPs") {
		t.Fatalf("err = %v", err)
	}
	if e.runs != 0 {
		t.Error("claude ran")
	}
}

func TestFindPicksIcpByNumberAcrossPages(t *testing.T) {
	e := newFindEnv(t)
	out, _, err := runCLITyped(t, clitest.Typed("2\n", clitest.CtrlD, "q\n"), "otak", "find")
	if err != nil {
		t.Fatal(err)
	}
	if got := strings.Join(e.icpPages, ","); got != "1/50,2/50" {
		t.Errorf("icp pages = %s", got)
	}
	for _, want := range []string{
		"Find leads with Claude: search the web for companies that fit an ICP.\n\nStep 1 of 4 · Choose an ICP\n",
		"  1. Alpha  Software   NL   any  any\n",
		"  2. Beta   Logistics  any  any  any\n",
		"Nothing created.",
	} {
		if !strings.Contains(out, want) {
			t.Errorf("out lacks %q:\n%s", want, out)
		}
	}
	if !strings.Contains(e.brief, "ICP: Beta") || strings.Contains(e.brief, "SaaS for clinics") {
		t.Errorf("brief = %s", e.brief)
	}
}

func TestFindIcpSelection(t *testing.T) {
	tests := []struct {
		name, flag, wantIcp string
	}{
		{"by id", icpBetaID, "ICP: Beta"},
		{"by name ignoring case", "aLpHa", "ICP: Alpha"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			e := newFindEnv(t)
			_, _, err := runCLILines(t, "\n", "otak", "find", "--icp", tt.flag)
			if err != nil {
				t.Fatal(err)
			}
			if !strings.Contains(e.brief, tt.wantIcp) {
				t.Errorf("brief = %s", e.brief)
			}
			for _, c := range e.api.Calls() {
				if c == "GET /otak/icps/"+icpAlphaID && tt.flag == icpBetaID {
					t.Errorf("read the wrong ICP")
				}
			}
		})
	}
}

func TestFindUnknownIcp(t *testing.T) {
	e := newFindEnv(t)
	_, _, err := runCLILines(t, "", "otak", "find", "--icp", "nope")
	if err == nil || !strings.Contains(err.Error(), `no ICP matches "nope"`) {
		t.Fatalf("err = %v", err)
	}
	if e.runs != 0 {
		t.Error("claude ran")
	}
}

func TestFindIcpPromptCancelAndRetry(t *testing.T) {
	e := newFindEnv(t)
	out, _, err := runCLILines(t, "9\nx\n\n", "otak", "find")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Enter a number from 1 to 2.") || !strings.Contains(out, "Cancelled.") || e.runs != 0 {
		t.Errorf("out = %s runs %d", out, e.runs)
	}
}

func TestFindQueriesKnownRelationsAndBuildsBrief(t *testing.T) {
	e := newFindEnv(t)
	_, _, err := runCLILines(t, "\n", "otak", "find", "--icp", "alpha")
	if err != nil {
		t.Fatal(err)
	}
	if len(e.relQueries) != 3 {
		t.Fatalf("relation queries = %v", e.relQueries)
	}
	var types []string
	for _, q := range e.relQueries {
		types = append(types, q.Get("type"))
		if q.Get("amount") != "50" || q.Get("page") != "1" {
			t.Errorf("paging = %v", q)
		}
		if q.Get("type") == "LEAD" {
			if got := strings.Join(q["stage"], ","); got != "NEW,QUALIFIED,CONTACTED,WARM,PROPOSAL,CLOSED,LOST,PARKED,DIRECT" {
				t.Errorf("lead stages = %s", got)
			}
		} else if len(q["stage"]) != 0 {
			t.Errorf("stage on %s", q.Get("type"))
		}
	}
	if strings.Join(types, ",") != "LEAD,CUSTOMER,SUPPLIER" {
		t.Errorf("types = %v", types)
	}
	for _, want := range []string{"- Known Lead - https://known-lead.nl", "- Lost Co - no website", "- Big Customer - https://www.bigcustomer.com/", "SaaS for clinics"} {
		if !strings.Contains(e.brief, want) {
			t.Errorf("brief lacks %q:\n%s", want, e.brief)
		}
	}
	var schema struct {
		Properties struct {
			Candidates struct {
				Items struct {
					Properties struct {
						Country struct {
							Enum []string `json:"enum"`
						} `json:"country"`
					} `json:"properties"`
				} `json:"items"`
			} `json:"candidates"`
		} `json:"properties"`
	}
	if err := json.Unmarshal([]byte(e.schema), &schema); err != nil {
		t.Fatal(err)
	}
	if got := strings.Join(schema.Properties.Candidates.Items.Properties.Country.Enum, ","); got != "NL,DE,BE" {
		t.Errorf("schema countries = %s", got)
	}
}

func TestFindMarksSkippedAndRefusesThem(t *testing.T) {
	e := newFindEnv(t)
	e.createOK()
	// 2 is skipped by name, 3 by host; 1,2 is refused, then 1,4 is accepted and confirmed.
	out, _, err := runCLILines(t, "1,2\n3\n1,4\ny\n", "otak", "find", "--icp", "alpha", "--focus", "")
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{
		"  Searching: clinic software",
		"  2. known lead  other.example    already in Otak as Known Lead\n",
		"  3. Hostmatch   bigcustomer.com  already in Otak as Big Customer\n",
		"  1. Newco       newco.io\n",
		"2 is already in Otak and cannot be picked",
		"3 is already in Otak and cannot be picked",
		"Create 2 leads in Acme? (y/n)",
		`Created 2 leads. They are in Otak as LEAD, stage NEW, linked to "Alpha".`,
	} {
		if !strings.Contains(out, want) {
			t.Errorf("out lacks %q:\n%s", want, out)
		}
	}
	if len(e.posts()) != 2 {
		t.Errorf("posts = %v", e.posts())
	}
}

func TestFindEveryCandidateSkipped(t *testing.T) {
	e := newFindEnv(t)
	e.candidates = e.candidates[1:3]
	out, _, err := runCLILines(t, "", "otak", "find", "--icp", "alpha")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Claude found no new companies.") || !strings.Contains(out, "Nothing created.") || len(e.posts()) != 0 {
		t.Errorf("out = %s", out)
	}
}

func TestFindNoCandidates(t *testing.T) {
	e := newFindEnv(t)
	e.candidates = nil
	out, _, err := runCLILines(t, "", "otak", "find", "--icp", "alpha")
	if err != nil || !strings.Contains(out, "Claude found no new companies.") || !strings.Contains(out, "Nothing created.") {
		t.Fatalf("err %v out %s", err, out)
	}
}

func TestFindCancelsCreateNothing(t *testing.T) {
	tests := []struct{ name, in string }{
		{"empty picks", "\n"},
		{"confirmation no", "1\nn\n"},
		{"confirmation empty", "1\n\n"},
		{"end of input at picks", ""},
		{"end of input at confirmation", "1\n"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			e := newFindEnv(t)
			out, _, err := runCLILines(t, tt.in, "otak", "find", "--icp", "alpha", "--focus", "")
			if err != nil {
				t.Fatal(err)
			}
			if !strings.Contains(out, "Nothing created.") {
				t.Errorf("out = %s", out)
			}
			if len(e.posts()) != 0 {
				t.Errorf("posts = %v", e.posts())
			}
		})
	}
}

func TestFindCreateBodies(t *testing.T) {
	e := newFindEnv(t)
	e.createOK()
	out, _, err := runCLILines(t, "all\nyes\n", "otak", "find", "--icp", icpAlphaID, "--focus", "")
	if err != nil {
		t.Fatal(err)
	}
	posts := e.posts()
	if len(posts) != 2 {
		t.Fatalf("posts = %v", posts)
	}
	if posts[0] != `{"name":"Newco","type":"LEAD","stage":"NEW","source":"RESEARCH","website":"https://newco.io","country":"NL","icpId":"`+icpAlphaID+`","notes":"Found by oba otak find: Builds clinic software"}` {
		t.Errorf("first body = %s", posts[0])
	}
	// Invalid website and unknown country are dropped, the lead is still created.
	if posts[1] != `{"name":"Oddco","type":"LEAD","stage":"NEW","source":"RESEARCH","icpId":"`+icpAlphaID+`","notes":"Found by oba otak find: bad fields"}` {
		t.Errorf("second body = %s", posts[1])
	}
	if !strings.Contains(out, "  ✓ Newco (new-1)\n") {
		t.Errorf("out = %s", out)
	}
	if got := e.api.Request("POST /otak/relations").Org; got != clitest.OrgIDAcme {
		t.Errorf("org header = %q", got)
	}
}

func TestFindFailedCreateContinuesAndExitsWithError(t *testing.T) {
	e := newFindEnv(t)
	e.candidates = []find.Candidate{
		{Name: "One", Website: "https://one.io", Country: "NL", Reason: "r1"},
		{Name: "Two", Website: "https://two.io", Country: "NL", Reason: "r2"},
		{Name: "Three", Website: "https://three.io", Country: "NL", Reason: "r3"},
	}
	var n int
	e.api.Handle("POST /otak/relations", func(w http.ResponseWriter, r *http.Request) {
		n++
		if n == 2 {
			w.WriteHeader(http.StatusBadRequest)
			clitest.WriteJSON(w, `{"message":"invalid"}`)
			return
		}
		w.WriteHeader(http.StatusCreated)
		clitest.WriteJSON(w, `{"data":{"id":"ok-`+strconv.Itoa(n)+`","name":"x","type":"LEAD"}}`)
	})
	out, _, err := runCLILines(t, "all\ny\n", "otak", "find", "--icp", "alpha", "--focus", "")
	if err == nil || err.Error() != "1 of 3 leads failed; see the lines above" {
		t.Fatalf("err = %v", err)
	}
	if client.ExitCode(err) != 1 {
		t.Errorf("exit code = %d", client.ExitCode(err))
	}
	if len(e.posts()) != 3 {
		t.Errorf("posts = %d", len(e.posts()))
	}
	for _, want := range []string{"  ✓ One (ok-1)\n", "  ✗ Two: ", "  ✓ Three (ok-3)\n", "Created 2 of 3 leads. They are in Otak as LEAD"} {
		if !strings.Contains(out, want) {
			t.Errorf("out lacks %q:\n%s", want, out)
		}
	}
}

func TestFindRunnerErrors(t *testing.T) {
	t.Run("failure is returned and nothing is created", func(t *testing.T) {
		e := newFindEnv(t)
		e.runErr = errors.New("claude exited with an error: boom")
		e.candidates = nil
		_, _, err := runCLILines(t, "", "otak", "find", "--icp", "alpha")
		if err == nil || !strings.Contains(err.Error(), "boom") || len(e.posts()) != 0 {
			t.Fatalf("err = %v", err)
		}
	})
	t.Run("interrupt", func(t *testing.T) {
		e := newFindEnv(t)
		e.runErr = context.Canceled
		e.candidates = nil
		_, _, err := runCLILines(t, "", "otak", "find", "--icp", "alpha")
		if err == nil || err.Error() != "stopped; nothing was created" {
			t.Fatalf("err = %v", err)
		}
	})
}

func TestFindFieldListWithoutCountryEnum(t *testing.T) {
	e := newFindEnv(t)
	e.api.ServeAPIDocs()
	_, _, err := runCLILines(t, "", "otak", "find", "--icp", "alpha")
	if err == nil || !strings.Contains(err.Error(), "lists no country codes or no stages") || e.runs != 0 {
		t.Fatalf("err = %v", err)
	}
}

func TestFindRejectsArguments(t *testing.T) {
	newFindEnv(t)
	if _, _, err := runCLILines(t, "", "otak", "find", "extra"); err == nil {
		t.Error("extra argument accepted")
	}
}

// Lines that arrive in one read, as a paste or keys typed while Claude Code searched, answer
// only the question they were read for: the rest is discarded before the next question, the
// focus text included.
func TestFindDiscardsInputTypedAhead(t *testing.T) {
	e := newFindEnv(t)
	e.createOK()
	out, _, err := runCLITyped(t, clitest.Typed("1\nall\ny\n", clitest.CtrlD, "maybe\nall\n", "all\n", "y es\n", "n\n", "q\n"), "otak", "find")
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{
		"> \nStep 2 of 4 · Focus the search (optional)",
		"Press Ctrl-D right away to skip.\n> \n\nStep 3 of 4",
		"\"maybe\"",
		"Create 2 leads in Acme? (y/n) Please answer y or n.\nCreate 2 leads in Acme? (y/n) Not created.",
		"Nothing created.",
	} {
		if !strings.Contains(out, want) {
			t.Errorf("out lacks %q:\n%s", want, out)
		}
	}
	if strings.Contains(e.brief, "Focus from the user") {
		t.Errorf("typed-ahead input became the focus:\n%s", e.brief)
	}
	if len(e.posts()) != 0 {
		t.Errorf("posts = %v", e.posts())
	}
}

// recordBriefs wraps the stubbed runner so that every run's brief is kept, oldest first.
func (e *findEnv) recordBriefs() *[]string {
	var briefs []string
	inner := runClaude
	runClaude = func(ctx context.Context, brief, schema string, onProgress claude.ProgressFunc) (json.RawMessage, error) {
		e.mu.Lock()
		briefs = append(briefs, brief)
		e.mu.Unlock()
		return inner(ctx, brief, schema, onProgress)
	}
	return &briefs
}

func TestFindHappyPathStepLines(t *testing.T) {
	e := newFindEnv(t)
	e.createOK()
	out, _, err := runCLITyped(t, clitest.Typed("1\n", clitest.CtrlD, "1\n", "y\n"), "otak", "find")
	if err != nil {
		t.Fatal(err)
	}
	want := []string{
		"Find leads with Claude: search the web for companies that fit an ICP.\n\n",
		"Step 1 of 4 · Choose an ICP\n",
		"  1. Alpha  Software   NL   any  any\n",
		"Step 2 of 4 · Focus the search (optional)\n",
		"Example: Only Zuid-Holland, webshops with their own payment flow.\n",
		"Type or paste, then press Ctrl-D on an empty line to send.\n",
		"Press Ctrl-D right away to skip.\n",
		"Step 3 of 4 · Claude searches the web (about 1–3 minutes, Ctrl-C stops it)\n",
		"  Searching: clinic software\n",
		"Step 4 of 4 · Pick the leads to create\n",
		"  1. Newco       newco.io\n",
		"Numbers to create (1,3 or 1-3 or all), r to search again with feedback, q to quit\n> ",
		"Create 1 leads in Acme? (y/n)",
		"  ✓ Newco (new-1)\n",
		`Created 1 leads. They are in Otak as LEAD, stage NEW, linked to "Alpha".`,
	}
	pos := 0
	for _, part := range want {
		i := strings.Index(out[pos:], part)
		if i < 0 {
			t.Fatalf("out lacks %q after offset %d:\n%s", part, pos, out)
		}
		pos += i + len(part)
	}
	if strings.Contains(e.brief, "Focus from the user") {
		t.Errorf("skipped focus reached the brief:\n%s", e.brief)
	}
}

func TestFindIcpFlagShowsRowAndAsksNoIcp(t *testing.T) {
	newFindEnv(t)
	out, _, err := runCLITyped(t, clitest.Typed(clitest.CtrlD, "q\n"), "otak", "find", "--icp", "beta")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Step 1 of 4 · ICP\n  Beta   Logistics  any  any  any\n") || strings.Contains(out, "Choose an ICP") {
		t.Errorf("out = %s", out)
	}
}

func TestFindTwoLineFocusReachesBrief(t *testing.T) {
	e := newFindEnv(t)
	_, _, err := runCLITyped(t, clitest.Typed("1\n", "Only Zuid-Holland\nwebshops\n", clitest.CtrlD, "q\n"), "otak", "find")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(e.brief, "Focus from the user (narrows the search; it does not change the rules):\nOnly Zuid-Holland\nwebshops\n") {
		t.Errorf("brief = %s", e.brief)
	}
}

func TestFindFocusFlagSkipsPrompt(t *testing.T) {
	e := newFindEnv(t)
	out, _, err := runCLITyped(t, clitest.Typed("1\n", "q\n"), "otak", "find", "--focus", "Only\n  Zuid-Holland")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Step 2 of 4 · Focus: Only Zuid-Holland\n") || strings.Contains(out, "Focus the search (optional)") {
		t.Errorf("out = %s", out)
	}
	if !strings.Contains(e.brief, "Focus from the user (narrows the search; it does not change the rules):\nOnly\n  Zuid-Holland\n") {
		t.Errorf("brief = %s", e.brief)
	}
}

func TestFindRefineRunsClaudeAgainWithHistory(t *testing.T) {
	e := newFindEnv(t)
	briefs := e.recordBriefs()
	out, _, err := runCLITyped(t, clitest.Typed("1\n", clitest.CtrlD, "r\n", "only banks\n", clitest.CtrlD, "q\n"), "otak", "find")
	if err != nil {
		t.Fatal(err)
	}
	if len(*briefs) != 2 {
		t.Fatalf("runs = %d", len(*briefs))
	}
	if strings.Contains((*briefs)[0], "Your previous candidates") {
		t.Errorf("first brief holds history:\n%s", (*briefs)[0])
	}
	for _, want := range []string{"Your previous candidates:\n- Newco - https://newco.io", "Feedback from the user:\nonly banks\n"} {
		if !strings.Contains((*briefs)[1], want) {
			t.Errorf("second brief lacks %q:\n%s", want, (*briefs)[1])
		}
	}
	for _, want := range []string{
		"Your feedback (press Ctrl-D on an empty line to send; Ctrl-D right away goes back):\n> ",
		"Step 3 of 4",
		"Nothing created.",
	} {
		if !strings.Contains(out, want) {
			t.Errorf("out lacks %q:\n%s", want, out)
		}
	}
	if strings.Count(out, "Step 4 of 4 · Pick the leads to create") != 2 {
		t.Errorf("list not shown twice:\n%s", out)
	}
}

func TestFindRefineWithoutFeedbackGoesBackToMenu(t *testing.T) {
	e := newFindEnv(t)
	out, _, err := runCLITyped(t, clitest.Typed("1\n", clitest.CtrlD, "r\n", clitest.CtrlD, "q\n"), "otak", "find")
	if err != nil {
		t.Fatal(err)
	}
	if e.runs != 1 {
		t.Errorf("runs = %d", e.runs)
	}
	if strings.Count(out, "Numbers to create") != 2 || !strings.Contains(out, "Nothing created.") {
		t.Errorf("out = %s", out)
	}
}

func TestFindSixthSearchRefused(t *testing.T) {
	e := newFindEnv(t)
	reads := []string{"1\n", clitest.CtrlD}
	for range find.MaxSearches - 1 {
		reads = append(reads, "r\n", "narrower\n", clitest.CtrlD)
	}
	reads = append(reads, "r\n", "q\n")
	out, _, err := runCLITyped(t, clitest.Typed(reads...), "otak", "find")
	if err != nil {
		t.Fatal(err)
	}
	if e.runs != find.MaxSearches {
		t.Errorf("runs = %d", e.runs)
	}
	if !strings.Contains(out, "This run has reached its limit of 5 searches; pick from this list or run 'oba otak find' again.\n") {
		t.Errorf("out = %s", out)
	}
}

func TestFindNoNewCandidatesOffersOnlyRefineAndQuit(t *testing.T) {
	tests := []struct {
		name       string
		candidates []find.Candidate
	}{
		{"no candidates", nil},
		{"every candidate known", []find.Candidate{
			{Name: "known lead", Website: "https://other.example", Country: "DE", Reason: "same name"},
		}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			e := newFindEnv(t)
			e.candidates = tt.candidates
			out, _, err := runCLITyped(t, clitest.Typed("1\n", clitest.CtrlD, "1\n", "all\n", "r\n", "wider\n", clitest.CtrlD, "q\n"), "otak", "find")
			if err != nil {
				t.Fatal(err)
			}
			if !strings.Contains(out, "Claude found no new companies.\nr to search again with feedback, q to quit\n> ") || strings.Contains(out, "Numbers to create") {
				t.Errorf("out = %s", out)
			}
			if strings.Count(out, "enter r or q") != 2 {
				t.Errorf("numbers not refused:\n%s", out)
			}
			if e.runs != 2 || len(e.posts()) != 0 {
				t.Errorf("runs %d posts %v", e.runs, e.posts())
			}
		})
	}
}

func TestFindQuitAndEndOfInputAtMenu(t *testing.T) {
	tests := []struct{ name, in string }{{"q", "q\n"}, {"upper case Q", "Q\n"}, {"Ctrl-D", clitest.CtrlD}}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			e := newFindEnv(t)
			out, _, err := runCLITyped(t, clitest.Typed("1\n", clitest.CtrlD, tt.in), "otak", "find")
			if err != nil || !strings.Contains(out, "Nothing created.") || len(e.posts()) != 0 {
				t.Fatalf("err %v out %s posts %v", err, out, e.posts())
			}
		})
	}
}

func TestFindConfirmNoReturnsToMenu(t *testing.T) {
	e := newFindEnv(t)
	e.createOK()
	out, _, err := runCLITyped(t, clitest.Typed("1\n", clitest.CtrlD, "1\n", "n\n", "1\n", "y\n"), "otak", "find")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Not created.\n") || strings.Count(out, "Create 1 leads in Acme? (y/n)") != 2 || len(e.posts()) != 1 {
		t.Errorf("posts %v out %s", e.posts(), out)
	}
	if e.runs != 1 {
		t.Errorf("runs = %d", e.runs)
	}
}

func TestFindCreateBodyHasCityAndSize(t *testing.T) {
	e := newFindEnv(t)
	e.createOK()
	city, size := " Rotterdam ", "SIZE_11_50"
	e.candidates = []find.Candidate{
		{Name: "Newco", Website: "https://newco.io", Country: "NL", Reason: "fits", City: &city, Size: &size},
		{Name: "Cityless", Website: "https://cityless.io", Country: "NL", Reason: "fits", Size: strPtrTest("HUGE")},
	}
	_, _, err := runCLILines(t, "all\ny\n", "otak", "find", "--icp", icpAlphaID, "--focus", "")
	if err != nil {
		t.Fatal(err)
	}
	posts := e.posts()
	if len(posts) != 2 {
		t.Fatalf("posts = %v", posts)
	}
	if posts[0] != `{"name":"Newco","type":"LEAD","stage":"NEW","source":"RESEARCH","website":"https://newco.io","country":"NL","city":"Rotterdam","size":"SIZE_11_50","icpId":"`+icpAlphaID+`","notes":"Found by oba otak find: fits"}` {
		t.Errorf("first body = %s", posts[0])
	}
	if strings.Contains(posts[1], `"city"`) || strings.Contains(posts[1], `"size"`) {
		t.Errorf("unknown size or missing city sent: %s", posts[1])
	}
}

func strPtrTest(s string) *string { return &s }

func TestFindCreatedButResponseUnreadableCountsAsCreated(t *testing.T) {
	e := newFindEnv(t)
	e.candidates = e.candidates[:1]
	e.candidates[0].Name = "Acme BV"
	e.api.Handle("POST /otak/relations", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusCreated)
		clitest.WriteJSON(w, `this is not json`)
	})
	out, _, err := runCLILines(t, "1\ny\n", "otak", "find", "--icp", "alpha", "--focus", "")
	if err != nil {
		t.Fatalf("err = %v", err)
	}
	for _, want := range []string{"  ✓ Acme BV (created, but the response could not be read: ", `Created 1 leads. They are in Otak as LEAD, stage NEW, linked to "Alpha".`} {
		if !strings.Contains(out, want) {
			t.Errorf("out lacks %q:\n%s", want, out)
		}
	}
	if strings.Contains(out, "✗") || strings.Contains(out, "failed") {
		t.Errorf("counted as failed:\n%s", out)
	}
}

func TestFindFailedCreateLineHasAPIError(t *testing.T) {
	e := newFindEnv(t)
	e.candidates = e.candidates[:1]
	e.api.Respond("POST /otak/relations", http.StatusUnprocessableEntity, `{"message":"name is taken"}`)
	out, _, err := runCLILines(t, "1\ny\n", "otak", "find", "--icp", "alpha", "--focus", "")
	if err == nil || err.Error() != "1 of 1 leads failed; see the lines above" || client.ExitCode(err) != 1 {
		t.Fatalf("err = %v", err)
	}
	if !strings.Contains(out, "  ✗ Newco: ") || !strings.Contains(out, "Created 0 of 1 leads.") {
		t.Errorf("out = %s", out)
	}
}

func contactCandidate() find.Candidate {
	role, email := "Director", "jan@acme.nl"
	return find.Candidate{
		Name: "Acme BV", Website: "https://acme.nl", Country: "NL", Reason: "fits",
		Contacts: []contact.FoundContact{
			{Name: "Jan", Role: &role, Email: &email, SourceURL: "https://acme.nl/team"},
			{Name: "Els", SourceURL: "https://acme.nl/contact"},
		},
	}
}

// handleLeadAndContacts creates leads with ids "lead-N" and contacts with ids "contact-N"; a
// contact named failName answers 422.
func (e *findEnv) handleLeadAndContacts(failName string) {
	var n int
	e.api.Handle("POST /otak/relations", func(w http.ResponseWriter, _ *http.Request) {
		e.mu.Lock()
		n++
		id := "lead-" + strconv.Itoa(n)
		e.mu.Unlock()
		w.WriteHeader(http.StatusCreated)
		clitest.WriteJSON(w, `{"data":{"id":"`+id+`","name":"x","type":"LEAD"}}`)
	})
	for _, id := range []string{"lead-1", "lead-2"} {
		path := "POST /otak/relations/" + id + "/contacts"
		e.api.Handle(path, func(w http.ResponseWriter, _ *http.Request) {
			// The mock API has read the request body already; take it from its record.
			reqs := e.api.Requests()
			raw := []byte(reqs[len(reqs)-1].Body)
			var body struct {
				Name string `json:"name"`
			}
			if err := json.Unmarshal(raw, &body); err != nil {
				w.WriteHeader(http.StatusBadRequest)
				return
			}
			if failName != "" && body.Name == failName {
				w.WriteHeader(http.StatusUnprocessableEntity)
				clitest.WriteJSON(w, `{"message":"sourceUrl is invalid"}`)
				return
			}
			w.WriteHeader(http.StatusCreated)
			clitest.WriteJSON(w, `{"data":{"id":"contact-`+strings.ToLower(body.Name)+`"}}`)
		})
	}
}

func TestFindShowsContactLinesAndNoContactFound(t *testing.T) {
	e := newFindEnv(t)
	e.candidates = []find.Candidate{contactCandidate(), {Name: "Bare", Website: "https://bare.io", Country: "NL", Reason: "r"}}
	out, _, err := runCLILines(t, "\n", "otak", "find", "--icp", "alpha", "--focus", "")
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{
		"       contact: Jan · Director · jan@acme.nl\n       source: https://acme.nl/team\n",
		"       contact: Els\n       source: https://acme.nl/contact\n",
		"       no contact found\n",
	} {
		if !strings.Contains(out, want) {
			t.Errorf("out lacks %q:\n%s", want, out)
		}
	}
}

func TestFindConfirmCountsContactsAndPostsRelationThenContacts(t *testing.T) {
	e := newFindEnv(t)
	e.candidates = []find.Candidate{contactCandidate()}
	e.handleLeadAndContacts("")
	out, _, err := runCLILines(t, "1\ny\n", "otak", "find", "--icp", "alpha", "--focus", "")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Create 1 leads and 2 contacts in Acme?") {
		t.Errorf("confirmation missing:\n%s", out)
	}
	var posts []string
	for _, r := range e.api.Requests() {
		if r.Method == "POST" {
			posts = append(posts, r.Path)
		}
	}
	want := []string{"/otak/relations", "/otak/relations/lead-1/contacts", "/otak/relations/lead-1/contacts"}
	if strings.Join(posts, " ") != strings.Join(want, " ") {
		t.Fatalf("posts = %v", posts)
	}
	first := e.api.Request("POST /otak/relations/lead-1/contacts").Body
	const wantBody = `{"name":"Jan","role":"Director","email":"jan@acme.nl","phone":null,"sourceUrl":"https://acme.nl/team","linkedinUrl":null,"decisionMaker":false,"kind":"PERSON","mainPersonContact":false,"mainBusinessContact":false}`
	if first != wantBody {
		t.Errorf("contact body = %s\nwant %s", first, wantBody)
	}
	for _, want := range []string{"    + contact Jan (contact-jan)\n", "Created 1 leads and 2 contacts."} {
		if !strings.Contains(out, want) {
			t.Errorf("out lacks %q:\n%s", want, out)
		}
	}
	if strings.Contains(out, "Add the missing contacts") {
		t.Errorf("recovery hint on success:\n%s", out)
	}
}

func TestFindContactFailureKeepsLeadAndRunsNextPick(t *testing.T) {
	e := newFindEnv(t)
	second := contactCandidate()
	second.Name, second.Website = "Beta BV", "https://beta.nl"
	second.Contacts = second.Contacts[:1]
	second.Contacts[0].Name = "Kees"
	e.candidates = []find.Candidate{contactCandidate(), second}
	e.handleLeadAndContacts("Jan")
	out, _, err := runCLILines(t, "all\ny\n", "otak", "find", "--icp", "alpha", "--focus", "")
	if err == nil || err.Error() != "1 of 3 contacts failed; see the lines above" || client.ExitCode(err) != 1 {
		t.Fatalf("err = %v", err)
	}
	for _, want := range []string{
		"    ✗ contact Jan: ", "    + contact Els (", "  ✓ Beta BV (lead-2)\n", "    + contact Kees (",
		"Created 2 leads and 2 of 3 contacts.", "Add the missing contacts with 'oba otak contact create <relationId>'.",
	} {
		if !strings.Contains(out, want) {
			t.Errorf("out lacks %q:\n%s", want, out)
		}
	}
	if len(e.posts()) != 2 {
		t.Errorf("lead posts = %d", len(e.posts()))
	}
}

func TestFindUnreadableRelationResponseSkipsContactsAndCountsThemFailed(t *testing.T) {
	e := newFindEnv(t)
	e.candidates = []find.Candidate{contactCandidate()}
	e.api.Handle("POST /otak/relations", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusCreated)
		clitest.WriteJSON(w, `not json`)
	})
	out, _, err := runCLILines(t, "1\ny\n", "otak", "find", "--icp", "alpha", "--focus", "")
	if err == nil || err.Error() != "2 of 2 contacts failed; see the lines above" {
		t.Fatalf("err = %v", err)
	}
	if !strings.Contains(out, "    ✗ 2 contacts not created: the lead id is unknown\n") {
		t.Errorf("out = %s", out)
	}
	for _, r := range e.api.Requests() {
		if strings.HasSuffix(r.Path, "/contacts") {
			t.Errorf("contact posted: %s", r.Path)
		}
	}
}

func TestFindRelationFailurePostsNoContacts(t *testing.T) {
	e := newFindEnv(t)
	e.candidates = []find.Candidate{contactCandidate()}
	e.api.Respond("POST /otak/relations", http.StatusUnprocessableEntity, `{"message":"name is taken"}`)
	out, _, err := runCLILines(t, "1\ny\n", "otak", "find", "--icp", "alpha", "--focus", "")
	if err == nil || err.Error() != "1 of 1 leads failed; see the lines above" {
		t.Fatalf("err = %v", err)
	}
	for _, r := range e.api.Requests() {
		if strings.HasSuffix(r.Path, "/contacts") {
			t.Errorf("contact posted: %s", r.Path)
		}
	}
	if strings.Contains(out, "contacts not created") || strings.Contains(out, "Add the missing contacts") {
		t.Errorf("contacts counted for a failed lead:\n%s", out)
	}
}
