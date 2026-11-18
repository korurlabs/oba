package icp

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"strings"
	"sync"
	"testing"

	"github.com/korurlabs/oba-cli/internal/connect/claude"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

const (
	icpDocsFile = "testdata/api-docs-icp.json"
	icpID       = "dddd4444-4444-4444-8444-444444444444"
	icpPath     = "/otak/icps/" + icpID

	icpDetailJSON = `{"data":{"id":"` + icpID + `","name":"Dutch textile SMEs","industry":"MANUFACTURING",` +
		`"country":"NL","companySizeMin":"SIZE_11_50","companySizeMax":null,"targetPersonRole":"CEO",` +
		`"description":"Family-owned producers.","updatedAt":"2026-11-09T09:30:00Z","createdAt":"2026-11-09T09:00:00Z"}}`
	icpCreatedJSON = `{"data":{"id":"` + icpID + `","name":"Dutch textile SMEs","industry":"MANUFACTURING"}}`
)

// icpEnv is a signed-in run against the mock API with Claude Code stubbed, stdin and stdout a
// terminal unless a test says otherwise.
type icpEnv struct {
	api    *clitest.MockAPI
	mu     sync.Mutex
	runs   int
	brief  string
	schema string
	draft  string
	runErr error
}

func newIcpEnv(t *testing.T) *icpEnv {
	t.Helper()
	clitest.SetupHome(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	oldOut, oldInstalled, oldRun := stdoutIsTerminal, claudeInstalled, runClaude
	stdoutIsTerminal = func() bool { return true }
	claudeInstalled = func() error { return nil }
	t.Cleanup(func() { stdoutIsTerminal, claudeInstalled, runClaude = oldOut, oldInstalled, oldRun })

	e := &icpEnv{
		api:   clitest.NewMockAPI(t),
		draft: `{"name":"Dutch textile SMEs","industry":"MANUFACTURING","country":"NL","companySizeMin":"SIZE_11_50","companySizeMax":null,"targetPersonRole":null,"description":"Family-owned producers.\nNo security officer."}`,
	}
	e.api.ServeDocs(icpDocsFile)
	clitest.LoginTo(t, e.api.URL(), clitest.OrgIDAcme)
	runClaude = func(_ context.Context, brief, schema string, onProgress claude.ProgressFunc) (json.RawMessage, error) {
		e.mu.Lock()
		defer e.mu.Unlock()
		e.runs++
		e.brief, e.schema = brief, schema
		if onProgress != nil {
			onProgress("Searching: textile producers")
		}
		if e.runErr != nil {
			return nil, e.runErr
		}
		return json.RawMessage(e.draft), nil
	}
	return e
}

func TestIcpList(t *testing.T) {
	e := newIcpEnv(t)
	var pages []string
	e.api.Handle("GET /otak/icps", func(w http.ResponseWriter, r *http.Request) {
		pages = append(pages, r.URL.Query().Get("page")+"/"+r.URL.Query().Get("amount"))
		if r.URL.Query().Get("page") == "1" {
			clitest.WriteJSON(w, `{"data":[{"id":"i1","name":"Alpha","industry":"SOFTWARE","country":"NL","companySizeMin":"SIZE_11_50","companySizeMax":"SIZE_51_200","targetPersonRole":"CTO"}],"pagination":{"currentPage":1,"last":false}}`)
			return
		}
		clitest.WriteJSON(w, `{"data":[{"id":"i2","name":"Beta","industry":"LOGISTICS","country":null,"companySizeMin":null,"companySizeMax":"SIZE_11_50","targetPersonRole":null}],"pagination":{"currentPage":2,"last":true}}`)
	})
	out, _, err := runCLI(t, "", "otak", "icp", "list")
	if err != nil {
		t.Fatal(err)
	}
	if strings.Join(pages, ",") != "1/50,2/50" {
		t.Errorf("pages = %v", pages)
	}
	want := "NAME   INDUSTRY  COUNTRY  SIZE                     TARGET ROLE  ID\n" +
		"Alpha  SOFTWARE  NL       SIZE_11_50 to SIZE_51_200  CTO          i1\n" +
		"Beta   LOGISTICS any      at most SIZE_11_50       any          i2\n"
	if normalizeSpaces(out) != normalizeSpaces(want) {
		t.Errorf("out =\n%s", out)
	}
	if e.api.Request("GET /otak/icps").Org != clitest.OrgIDAcme {
		t.Error("organization header missing")
	}
}

func TestIcpListEmpty(t *testing.T) {
	e := newIcpEnv(t)
	e.api.Respond("GET /otak/icps", 200, `{"data":[],"pagination":{"currentPage":1,"last":true}}`)
	out, _, err := runCLI(t, "", "otak", "icp", "list")
	if err != nil || out != "No ICPs.\n" {
		t.Fatalf("out = %q, err = %v", out, err)
	}
}

func TestIcpCreatePiped(t *testing.T) {
	e := newIcpEnv(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	e.api.Respond("POST /otak/icps", http.StatusCreated, icpCreatedJSON)
	in := `{"name": "Dutch textile SMEs", "industry": "MANUFACTURING", "country": null, "companySizeMin": "size_11_50"}`
	out, _, err := runCLI(t, in, "otak", "icp", "create")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Created ICP Dutch textile SMEs ("+icpID+")\n" {
		t.Errorf("out = %q", out)
	}
	// Sent as given, values untouched, only compacted.
	want := `{"name":"Dutch textile SMEs","industry":"MANUFACTURING","country":null,"companySizeMin":"size_11_50"}`
	if got := e.api.Request("POST /otak/icps").Body; got != want {
		t.Errorf("body = %s", got)
	}
	if e.runs != 0 {
		t.Error("claude ran for piped input")
	}
}

func TestIcpCreatePipedUnknownKey(t *testing.T) {
	e := newIcpEnv(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	_, _, err := runCLI(t, `{"name":"x","industry":"OTHER","contry":"NL"}`, "otak", "icp", "create")
	if err == nil || err.Error() != `unknown field "contry". Did you mean "country"?` {
		t.Fatalf("err = %v", err)
	}
	if len(e.api.Calls()) != 0 {
		t.Errorf("calls = %v", e.api.Calls())
	}
}

func TestIcpCreatePipedInvalidInput(t *testing.T) {
	e := newIcpEnv(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	_, _, err := runCLI(t, `[1]`, "otak", "icp", "create")
	if err == nil || !strings.Contains(err.Error(), "not an array") {
		t.Fatalf("err = %v", err)
	}
	if len(e.api.Calls()) != 0 {
		t.Errorf("calls = %v", e.api.Calls())
	}
}

func TestIcpCreateNameTaken(t *testing.T) {
	e := newIcpEnv(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	e.api.Respond("POST /otak/icps", http.StatusConflict,
		`{"error":{"code":"OTK_STA_005","message":"An ICP with this name already exists"}}`)
	_, _, err := runCLI(t, `{"name":"Dutch textile SMEs","industry":"OTHER"}`, "otak", "icp", "create")
	if err == nil || err.Error() != "OTK_STA_005: An ICP with this name already exists" {
		t.Fatalf("err = %v", err)
	}
	if client.ExitCode(err) != 1 {
		t.Errorf("exit code = %d", client.ExitCode(err))
	}
}

func TestIcpDraftCreates(t *testing.T) {
	e := newIcpEnv(t)
	e.api.Respond("POST /otak/icps", http.StatusCreated, icpCreatedJSON)
	out, _, err := runCLITyped(t, clitest.Typed("Dutch textile makers with their own factory\n", clitest.CtrlD, "y\n"), "otak", "icp", "create")
	if err != nil {
		t.Fatal(err)
	}
	if e.runs != 1 || !strings.Contains(e.brief, "Dutch textile makers with their own factory") ||
		!strings.Contains(e.brief, `"industry": "SOFTWARE" | "MANUFACTURING" | "LOGISTICS" | "OTHER",  (required)`) {
		t.Errorf("runs %d brief:\n%s", e.runs, e.brief)
	}
	var schema map[string]any
	if err := json.Unmarshal([]byte(e.schema), &schema); err != nil {
		t.Fatal(err)
	}
	props := schema["properties"].(map[string]any)
	country := props["country"].(map[string]any)
	if schema["additionalProperties"] != false || len(props) != 7 ||
		strings.Join(toStrings(country["enum"]), ",") != "NL,DE,BE,<nil>" {
		t.Errorf("schema = %s", e.schema)
	}

	for _, part := range []string{
		"Describe your ideal customer. Several lines are fine; finish with Ctrl-D on a line of its own,\n" +
			"or press Ctrl-D at once to cancel.\n",
		"  Searching: textile producers\n",
		"Draft ICP:\n",
		"  name:              Dutch textile SMEs\n",
		"  companySizeMax:    -\n",
		"  description:       Family-owned producers.\n                     No security officer.\n",
		"Create this ICP? (y/n) Created ICP Dutch textile SMEs (" + icpID + ")\n",
	} {
		if !strings.Contains(out, part) {
			t.Errorf("out lacks %q:\n%s", part, out)
		}
	}
	var sent, drafted map[string]any
	if err := json.Unmarshal([]byte(e.api.Request("POST /otak/icps").Body), &sent); err != nil {
		t.Fatal(err)
	}
	if err := json.Unmarshal([]byte(e.draft), &drafted); err != nil {
		t.Fatal(err)
	}
	if !jsonEqual(sent, drafted) {
		t.Errorf("sent %v, want the draft %v", sent, drafted)
	}
}

func TestIcpDraftDeclined(t *testing.T) {
	e := newIcpEnv(t)
	out, _, err := runCLITyped(t, clitest.Typed("textile makers\n", clitest.CtrlD, "n\n"), "otak", "icp", "create")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasSuffix(out, "Create this ICP? (y/n) Cancelled.\n") {
		t.Errorf("out = %s", out)
	}
	if len(e.api.Calls()) != 0 {
		t.Errorf("calls = %v", e.api.Calls())
	}
}

func TestIcpDraftEmptyDescriptionCancels(t *testing.T) {
	e := newIcpEnv(t)
	out, _, err := runCLI(t, "\n", "otak", "icp", "create")
	if err != nil || !strings.HasSuffix(out, "Cancelled.\n") {
		t.Fatalf("out = %q, err = %v", out, err)
	}
	if e.runs != 0 || len(e.api.Calls()) != 0 {
		t.Errorf("runs %d calls %v", e.runs, e.api.Calls())
	}
}

func TestIcpDraftCtrlDAtOnceCancels(t *testing.T) {
	e := newIcpEnv(t)
	out, _, err := runCLITyped(t, clitest.Typed(clitest.CtrlD), "otak", "icp", "create")
	if err != nil || !strings.HasSuffix(out, "Cancelled.\n") {
		t.Fatalf("out = %q, err = %v", out, err)
	}
	if e.runs != 0 || len(e.api.Calls()) != 0 {
		t.Errorf("runs %d calls %v", e.runs, e.api.Calls())
	}
}

// A description pasted over several paragraphs is read whole up to Ctrl-D: none of its lines
// answers the confirmation.
func TestIcpDraftMultilineDescription(t *testing.T) {
	e := newIcpEnv(t)
	e.api.Respond("POST /otak/icps", http.StatusCreated, icpCreatedJSON)
	out, _, err := runCLITyped(t, clitest.Typed(
		"Dutch textile makers\n", "with their own factory.\n", "\n", "\n",
		"y\n", "They lack a security officer.\n", clitest.CtrlD, "yes\n",
	), "otak", "icp", "create")
	if err != nil {
		t.Fatal(err)
	}
	want := "Description, as the user typed it:\nDutch textile makers\nwith their own factory.\n\n\ny\n" +
		"They lack a security officer.\n\nReturn one JSON object"
	if e.runs != 1 || !strings.Contains(e.brief, want) {
		t.Errorf("brief:\n%s", e.brief)
	}
	if !strings.HasSuffix(out, "Create this ICP? (y/n) Created ICP Dutch textile SMEs ("+icpID+")\n") {
		t.Errorf("out = %s", out)
	}
}

func TestIcpDraftConfirmAsksAgainOnJunk(t *testing.T) {
	e := newIcpEnv(t)
	e.api.Respond("POST /otak/icps", http.StatusCreated, icpCreatedJSON)
	out, _, err := runCLITyped(t, clitest.Typed("x\n", clitest.CtrlD, "maybe\n", "\n", "y es\n", "YES\n"), "otak", "icp", "create")
	if err != nil {
		t.Fatal(err)
	}
	again := "Create this ICP? (y/n) Please answer y or n.\n"
	if strings.Count(out, again) != 3 || !strings.HasSuffix(out, "Create this ICP? (y/n) Created ICP Dutch textile SMEs ("+icpID+")\n") {
		t.Errorf("out = %s", out)
	}
	if got := strings.Join(e.api.Calls(), ","); got != "POST /otak/icps" {
		t.Errorf("calls = %v", e.api.Calls())
	}
}

func TestIcpDraftConfirmEndOfInputCancels(t *testing.T) {
	e := newIcpEnv(t)
	out, _, err := runCLITyped(t, clitest.Typed("x\n", clitest.CtrlD, "maybe\n", clitest.CtrlD), "otak", "icp", "create")
	if err != nil || !strings.HasSuffix(out, "Please answer y or n.\nCreate this ICP? (y/n) \nCancelled.\n") {
		t.Fatalf("out = %q, err = %v", out, err)
	}
	for _, call := range e.api.Calls() {
		if strings.HasPrefix(call, "POST") {
			t.Errorf("calls = %v", e.api.Calls())
		}
	}
}

func TestIcpDraftUnknownKeyRefused(t *testing.T) {
	e := newIcpEnv(t)
	e.draft = `{"name":"x","industry":"OTHER","website":"https://x.nl"}`
	_, _, err := runCLI(t, "x\ny\n", "otak", "icp", "create")
	if err == nil || !strings.Contains(err.Error(), `field the API does not accept: unknown field "website"`) {
		t.Fatalf("err = %v", err)
	}
	if len(e.api.Calls()) != 0 {
		t.Errorf("calls = %v", e.api.Calls())
	}
}

func TestIcpDraftNotAnObject(t *testing.T) {
	e := newIcpEnv(t)
	e.draft = `["x"]`
	_, _, err := runCLI(t, "x\ny\n", "otak", "icp", "create")
	if err == nil || !strings.Contains(err.Error(), "not one JSON object") {
		t.Fatalf("err = %v", err)
	}
	if len(e.api.Calls()) != 0 {
		t.Errorf("calls = %v", e.api.Calls())
	}
}

func TestIcpDraftStopped(t *testing.T) {
	e := newIcpEnv(t)
	e.runErr = context.Canceled
	_, _, err := runCLI(t, "x\n", "otak", "icp", "create")
	if err == nil || err.Error() != "stopped; nothing was created" {
		t.Fatalf("err = %v", err)
	}
	if len(e.api.Calls()) != 0 {
		t.Errorf("calls = %v", e.api.Calls())
	}
}

func TestIcpDraftNeedsClaude(t *testing.T) {
	e := newIcpEnv(t)
	claudeInstalled = func() error { return claude.ErrNotInstalled }
	_, _, err := runCLI(t, "x\n", "otak", "icp", "create")
	if !errors.Is(err, claude.ErrNotInstalled) {
		t.Fatalf("err = %v", err)
	}
	if e.runs != 0 || len(e.api.Calls()) != 0 {
		t.Errorf("runs %d calls %v", e.runs, e.api.Calls())
	}
}

func TestIcpDraftNeedsTerminalStdout(t *testing.T) {
	e := newIcpEnv(t)
	stdoutIsTerminal = func() bool { return false }
	_, _, err := runCLI(t, "x\n", "otak", "icp", "create")
	if !errors.Is(err, errIcpCreateNotInteractive) {
		t.Fatalf("err = %v", err)
	}
	if e.runs != 0 {
		t.Error("claude ran")
	}
}

func TestIcpUpdateMerges(t *testing.T) {
	e := newIcpEnv(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	e.api.Respond("GET "+icpPath, 200, icpDetailJSON)
	e.api.Respond("PUT "+icpPath, 200, icpDetailJSON)
	out, _, err := runCLI(t, `{"country": null, "targetPersonRole": "CISO"}`, "otak", "icp", "update", icpID)
	if err != nil {
		t.Fatal(err)
	}
	if out != "Updated ICP "+icpID+"\n" {
		t.Errorf("out = %q", out)
	}
	if got := strings.Join(e.api.Calls(), ","); got != "GET "+icpPath+",PUT "+icpPath {
		t.Errorf("calls = %s", got)
	}
	var body map[string]any
	if err := json.Unmarshal([]byte(e.api.Request("PUT "+icpPath).Body), &body); err != nil {
		t.Fatal(err)
	}
	want := map[string]any{
		"name": "Dutch textile SMEs", "industry": "MANUFACTURING", "country": nil,
		"companySizeMin": "SIZE_11_50", "companySizeMax": nil, "targetPersonRole": "CISO",
		"description": "Family-owned producers.",
	}
	if !jsonEqual(body, want) {
		t.Errorf("body = %v", body)
	}
}

func TestIcpUpdateRefusals(t *testing.T) {
	tests := []struct {
		name, stdin, want string
		terminal          bool
	}{
		{"unknown key", `{"nme":"x"}`, `unknown field "nme". Did you mean "name"?`, false},
		{"empty object", `{}`, "Nothing to update.", false},
		{"no stdin", ``, "no JSON on stdin; pass one JSON object, as 'oba otak icp update --help' shows", true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			e := newIcpEnv(t)
			clitest.FakeTerminal(t, &stdinIsTerminal, tt.terminal)
			_, _, err := runCLI(t, tt.stdin, "otak", "icp", "update", icpID)
			if err == nil || err.Error() != tt.want {
				t.Fatalf("err = %v, want %q", err, tt.want)
			}
			if len(e.api.Calls()) != 0 {
				t.Errorf("calls = %v", e.api.Calls())
			}
		})
	}
}

func TestIcpUpdateNotFound(t *testing.T) {
	e := newIcpEnv(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	e.api.Respond("GET "+icpPath, http.StatusNotFound, `{"error":{"code":"OTK_NOT_004","message":"ICP not found"}}`)
	_, _, err := runCLI(t, `{"name":"x"}`, "otak", "icp", "update", icpID)
	if err == nil || err.Error() != "OTK_NOT_004: ICP not found" || client.ExitCode(err) != 2 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
	if got := e.api.Calls(); len(got) != 1 {
		t.Errorf("calls = %v", got)
	}
}

func TestIcpDelete(t *testing.T) {
	tests := []struct {
		name, stdin string
		args        []string
		terminal    bool
		wantDelete  bool
		wantOut     string
		wantErr     error
	}{
		{"--yes", "", []string{"--yes"}, false, true, "Deleted ICP " + icpID + "\n", nil},
		{"confirmed", "y\n", nil, true, true, "Delete ICP " + icpID + "? (y/n) Deleted ICP " + icpID + "\n", nil},
		{"declined", "n\n", nil, true, false, "Delete ICP " + icpID + "? (y/n) Cancelled.\n", nil},
		{"junk asks again", "sure\ny\n", nil, true, true, "Delete ICP " + icpID + "? (y/n) Please answer y or n.\nDelete ICP " + icpID + "? (y/n) Deleted ICP " + icpID + "\n", nil},
		{"end of input", "", nil, true, false, "Delete ICP " + icpID + "? (y/n) \nCancelled.\n", nil},
		{"no terminal", "", nil, false, false, "", terminal.ErrDeleteNeedsYes},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			e := newIcpEnv(t)
			clitest.FakeTerminal(t, &stdinIsTerminal, tt.terminal)
			e.api.Respond("DELETE "+icpPath, 200, `{"message":"ok"}`)
			out, _, err := runCLILines(t, tt.stdin, append([]string{"otak", "icp", "delete", icpID}, tt.args...)...)
			if !errors.Is(err, tt.wantErr) {
				t.Fatalf("err = %v, want %v", err, tt.wantErr)
			}
			if out != tt.wantOut {
				t.Errorf("out = %q", out)
			}
			deleted := len(e.api.Calls()) == 1 && e.api.Calls()[0] == "DELETE "+icpPath
			if deleted != tt.wantDelete {
				t.Errorf("calls = %v", e.api.Calls())
			}
		})
	}
}

func TestIcpDeleteForbidden(t *testing.T) {
	e := newIcpEnv(t)
	e.api.Respond("DELETE "+icpPath, http.StatusForbidden, `{"error":{"code":"ORG_SEC_003","message":"Insufficient role"}}`)
	_, _, err := runCLI(t, "", "otak", "icp", "delete", icpID, "--yes")
	if err == nil || err.Error() != "ORG_SEC_003: Insufficient role" || client.ExitCode(err) != 3 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
}

func TestIcpHelpListsLiveFields(t *testing.T) {
	newIcpEnv(t)
	out, _, err := runCLI(t, "", "otak", "icp", "create", "--help")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, `"name": string,  (required)`) || !strings.Contains(out, `"country": "NL" | "DE" | "BE" | null`) {
		t.Errorf("out = %s", out)
	}
	out, _, err = runCLI(t, "", "otak", "icp", "update", "--help")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "give only the ones to change.") || strings.Contains(out, "(required)") {
		t.Errorf("out = %s", out)
	}
}

func normalizeSpaces(s string) string {
	lines := strings.Split(s, "\n")
	for i, line := range lines {
		lines[i] = strings.Join(strings.Fields(line), " ")
	}
	return strings.Join(lines, "\n")
}

func toStrings(values any) []string {
	list, _ := values.([]any)
	out := make([]string, 0, len(list))
	for _, v := range list {
		if v == nil {
			out = append(out, "<nil>")
			continue
		}
		out = append(out, v.(string))
	}
	return out
}

func jsonEqual(a, b map[string]any) bool {
	ja, errA := json.Marshal(a)
	jb, errB := json.Marshal(b)
	return errA == nil && errB == nil && string(ja) == string(jb)
}
