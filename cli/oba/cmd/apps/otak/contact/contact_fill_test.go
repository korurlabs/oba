package contact

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"testing"

	"github.com/korurlabs/oba-cli/internal/connect/claude"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

// fillEnv is a signed-in contact fill run against the mock API with the runner stubbed.
type fillEnv struct {
	api        *clitest.MockAPI
	mu         sync.Mutex
	briefs     []string
	relQueries []url.Values
	// found answers each run: relation id to the contacts Claude Code returns for it.
	found  map[string][]map[string]any
	runErr error
}

const (
	fillLeadsPage = `{"data":[` +
		`{"id":"l1","name":"Globex","type":"LEAD","stage":"NEW","website":"https://www.globex.example/"},` +
		`{"id":"l2","name":"No Site","type":"LEAD","stage":"NEW","website":null},` +
		`{"id":"l4","name":"Lost Co","type":"LEAD","stage":"LOST","website":"https://lost.nl"},` +
		`{"id":"l5","name":"Initech","type":"LEAD","stage":"CONTACTED","website":"https://initech.example"}` +
		`],"pagination":{"currentPage":1,"last":true}}`
	emptyPage = `{"data":[],"pagination":{"currentPage":1,"last":true}}`
)

func newFillEnv(t *testing.T) *fillEnv {
	t.Helper()
	clitest.SetupHome(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	oldOut, oldInstalled, oldRun := stdoutIsTerminal, claudeInstalled, runClaude
	stdoutIsTerminal = func() bool { return true }
	claudeInstalled = func() error { return nil }
	t.Cleanup(func() { stdoutIsTerminal, claudeInstalled, runClaude = oldOut, oldInstalled, oldRun })

	e := &fillEnv{api: clitest.NewMockAPI(t)}
	e.api.ServeAPIDocs()
	e.api.Respond("GET /users/me", http.StatusOK, clitest.UserMeJSON)
	e.api.Handle("GET /otak/relations", func(w http.ResponseWriter, r *http.Request) {
		q := r.URL.Query()
		e.mu.Lock()
		e.relQueries = append(e.relQueries, q)
		e.mu.Unlock()
		if q.Get("type") == "LEAD" {
			clitest.WriteJSON(w, fillLeadsPage)
			return
		}
		clitest.WriteJSON(w, emptyPage)
	})
	e.api.Handle("POST /otak/relations/l1/contacts", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusCreated)
		clitest.WriteJSON(w, `{"data":{"id":"c-new"}}`)
	})
	e.api.Handle("POST /otak/relations/l5/contacts", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusCreated)
		clitest.WriteJSON(w, `{"data":{"id":"c-new-5"}}`)
	})
	clitest.LoginTo(t, e.api.URL(), clitest.OrgIDAcme)

	e.found = map[string][]map[string]any{
		"l1": {
			{"name": "Anna Jansen", "role": "Owner", "email": "anna@globex.example", "phone": "+31 55 123 4567", "sourceUrl": "https://www.globex.example/over-ons"},
			{"name": "Private Pete", "role": "Bookkeeper", "email": "pete@gmail.com", "phone": nil, "sourceUrl": "https://www.globex.example/team"},
		},
		"l5": {
			{"name": "Piet Smit", "role": "Director", "email": nil, "phone": nil, "sourceUrl": "https://initech.example/contact"},
		},
		"unknown": {
			{"name": "Ghost", "role": nil, "email": nil, "phone": nil, "sourceUrl": "https://ghost.nl"},
		},
	}
	runClaude = func(_ context.Context, brief, _ string, onProgress claude.ProgressFunc) (json.RawMessage, error) {
		e.mu.Lock()
		defer e.mu.Unlock()
		e.briefs = append(e.briefs, brief)
		if onProgress != nil {
			onProgress("Fetching: https://www.globex.example/over-ons")
		}
		if e.runErr != nil {
			return nil, e.runErr
		}
		var relations []map[string]any
		for id, contacts := range e.found {
			relations = append(relations, map[string]any{"relationId": id, "contacts": contacts})
		}
		result, err := json.Marshal(map[string]any{"relations": relations})
		if err != nil {
			t.Fatal(err)
		}
		return result, nil
	}
	return e
}

func (e *fillEnv) contactPosts() []string {
	var out []string
	for _, r := range e.api.Requests() {
		if r.Method == "POST" && strings.HasSuffix(r.Path, "/contacts") {
			out = append(out, r.Path+" "+r.Body)
		}
	}
	return out
}

func TestFillNeedsTerminal(t *testing.T) {
	e := newFillEnv(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	_, _, err := runCLILines(t, "", "otak", "contact", "fill")
	if !errors.Is(err, errFillNotInteractive) {
		t.Fatalf("err = %v", err)
	}
	if len(e.briefs) != 0 {
		t.Errorf("runs = %d", len(e.briefs))
	}
}

func TestFillAllAsksForRelationsWithoutContactsAndSkips(t *testing.T) {
	e := newFillEnv(t)
	out, _, err := runCLILines(t, "q\n", "otak", "contact", "fill", "--type", "LEAD")
	if err != nil {
		t.Fatal(err)
	}
	if len(e.relQueries) == 0 || e.relQueries[0].Get("hasContacts") != "false" {
		t.Fatalf("queries = %v", e.relQueries)
	}
	if !strings.Contains(out, "2 relations without a contact; searching 2. Skipped 1 without a website.") {
		t.Errorf("output = %q", out)
	}
	brief := e.briefs[0]
	for _, want := range []string{"l1 - Globex - https://www.globex.example/", "l5 - Initech - https://initech.example", "Do not collect"} {
		if !strings.Contains(brief, want) {
			t.Errorf("brief lacks %q:\n%s", want, brief)
		}
	}
	for _, unwanted := range []string{"No Site", "Lost Co"} {
		if strings.Contains(brief, unwanted) {
			t.Errorf("brief names %q", unwanted)
		}
	}
	if !strings.Contains(out, "Nothing created.") || len(e.contactPosts()) != 0 {
		t.Errorf("output = %q posts = %v", out, e.contactPosts())
	}
}

func TestFillShowsCleanedContactsAndCreatesPicks(t *testing.T) {
	e := newFillEnv(t)
	out, _, err := runCLILines(t, "all\ny\n", "otak", "contact", "fill", "--type", "LEAD")
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{
		"1. Anna Jansen · Owner · anna@globex.example · +31 55 123 4567",
		"2. Private Pete · Bookkeeper",
		"3. Piet Smit · Director",
		"Create 3 contacts on 2 relations in Acme?",
		"Created 3 of 3 contacts.",
	} {
		if !strings.Contains(out, want) {
			t.Errorf("output lacks %q:\n%s", want, out)
		}
	}
	if strings.Contains(out, "pete@gmail.com") || strings.Contains(out, "Ghost") {
		t.Errorf("output shows a private address or an unknown relation:\n%s", out)
	}
	posts := e.contactPosts()
	if len(posts) != 3 || !strings.Contains(posts[0], "/otak/relations/l1/contacts") || !strings.Contains(posts[0], `"sourceUrl":"https://www.globex.example/over-ons"`) ||
		!strings.Contains(posts[0], `"kind":"PERSON"`) || !strings.Contains(posts[0], `"mainPersonContact":false`) ||
		!strings.Contains(posts[0], `"mainBusinessContact":false`) {
		t.Errorf("posts = %v", posts)
	}
}

func TestFillPicksSome(t *testing.T) {
	e := newFillEnv(t)
	out, _, err := runCLILines(t, "3\ny\n", "otak", "contact", "fill", "--type", "LEAD")
	if err != nil {
		t.Fatal(err)
	}
	posts := e.contactPosts()
	if len(posts) != 1 || !strings.Contains(posts[0], "/otak/relations/l5/contacts") {
		t.Errorf("posts = %v", posts)
	}
	if !strings.Contains(out, "Create 1 contacts on 1 relations in Acme?") {
		t.Errorf("output = %q", out)
	}
}

func TestFillLimit(t *testing.T) {
	e := newFillEnv(t)
	out, _, err := runCLILines(t, "q\n", "otak", "contact", "fill", "--type", "LEAD", "--limit", "1")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "2 relations without a contact; searching 1.") || strings.Contains(e.briefs[0], "Initech") {
		t.Errorf("output = %q brief = %q", out, e.briefs[0])
	}
}

func TestFillOneRelationNamesExistingContacts(t *testing.T) {
	e := newFillEnv(t)
	e.api.Respond("GET /otak/relations/l1", 200, `{"data":{"id":"l1","name":"Globex","website":"https://www.globex.example/"}}`)
	e.api.Respond("GET /otak/relations/l1/contacts", 200, `{"data":[{"id":"c1","name":"Anna Jansen"}]}`)
	out, _, err := runCLILines(t, "q\n", "otak", "contact", "fill", "l1")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(e.briefs[0], "Already known contacts, do not propose them again: Anna Jansen") {
		t.Errorf("brief = %q", e.briefs[0])
	}
	if strings.Contains(out, "Anna Jansen ·") || !strings.Contains(out, "1. Private Pete") {
		t.Errorf("an existing contact is offered again:\n%s", out)
	}
}

func TestFillOneRelationRefused(t *testing.T) {
	tests := []struct {
		name, body, want string
	}{
		{"no website", `{"data":{"id":"l3","name":"Quiet Co","website":null}}`, "has no website"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			e := newFillEnv(t)
			e.api.Respond("GET /otak/relations/l3", 200, tt.body)
			_, _, err := runCLILines(t, "", "otak", "contact", "fill", "l3")
			if err == nil || !strings.Contains(err.Error(), tt.want) {
				t.Fatalf("err = %v", err)
			}
			if len(e.briefs) != 0 {
				t.Errorf("runs = %d", len(e.briefs))
			}
		})
	}
}

func TestFillStoppedCreatesNothing(t *testing.T) {
	e := newFillEnv(t)
	e.runErr = context.Canceled
	_, _, err := runCLILines(t, "", "otak", "contact", "fill", "--type", "LEAD")
	if err == nil || err.Error() != "stopped; nothing was created" {
		t.Fatalf("err = %v", err)
	}
	if len(e.contactPosts()) != 0 {
		t.Errorf("posts = %v", e.contactPosts())
	}
}

func TestFillCreateFailureIsReported(t *testing.T) {
	e := newFillEnv(t)
	e.api.Respond("POST /otak/relations/l5/contacts", http.StatusBadRequest, `{"message":"bad"}`)
	out, _, err := runCLILines(t, "all\ny\n", "otak", "contact", "fill", "--type", "LEAD")
	if err == nil || !strings.Contains(err.Error(), "1 of 3 contacts were not created") {
		t.Fatalf("err = %v", err)
	}
	if !strings.Contains(out, "Created 2 of 3 contacts.") {
		t.Errorf("output = %q", out)
	}
}
