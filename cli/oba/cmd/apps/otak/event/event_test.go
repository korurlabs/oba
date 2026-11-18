package event

import (
	"encoding/json"
	"errors"
	"net/http"
	"net/url"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

const (
	fixedToday = "2026-11-14"
	eventID    = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
	eventPath  = "/otak/events/" + eventID

	eventDetailJSON = `{"data":{"id":"` + eventID + `","name":"IGEF 2026","type":"EXPO","startDate":"2026-11-04",` +
		`"endDate":"2026-11-06","city":"Ankara","country":"TR","website":"https://igef.com.tr/","notes":"Hall 3.",` +
		`"createdAt":"2026-11-14T09:00:00Z","updatedAt":"2026-11-14T09:30:00Z"}}`
	eventCreatedJSON = `{"data":{"id":"` + eventID + `","name":"IGEF 2026","type":"EXPO","startDate":"2026-11-04"}}`
)

// recordList serves GET /otak/events from pages (one JSON body per page) and returns the
// queries it received.
func recordList(api *clitest.MockAPI, pages ...string) *[]url.Values {
	var queries []url.Values
	api.Handle("GET /otak/events", func(w http.ResponseWriter, r *http.Request) {
		queries = append(queries, r.URL.Query())
		page := len(queries)
		if page > len(pages) {
			clitest.WriteJSON(w, `{"data":[],"pagination":{"currentPage":1,"last":true}}`)
			return
		}
		clitest.WriteJSON(w, pages[page-1])
	})
	return &queries
}

func TestEventListUpcomingSendsPeriodAndToday(t *testing.T) {
	api := newEventAPI(t)
	queries := recordList(api,
		`{"data":[{"id":"e1","name":"IGEF 2026","type":"EXPO","startDate":"2026-11-14","endDate":"2026-11-16","city":"Ankara","country":"TR","website":null}],"pagination":{"currentPage":1,"last":false}}`,
		`{"data":[{"id":"e2","name":"Go meetup","type":"MEETUP","startDate":"2026-12-01","endDate":null,"city":null,"country":"NL","website":null},`+
			`{"id":"e3","name":"Webinar","type":"WEBINAR","startDate":"2026-12-02","endDate":null,"city":null,"country":null,"website":null}],"pagination":{"currentPage":2,"last":true}}`)
	out, _, err := runCLI(t, "", "otak", "event", "list", "--period", "Upcoming")
	if err != nil {
		t.Fatal(err)
	}
	if len(*queries) != 2 {
		t.Fatalf("queries = %v", *queries)
	}
	q := (*queries)[0]
	if q.Get("period") != "UPCOMING" || q.Get("today") != fixedToday || q.Get("page") != "1" || q.Get("amount") != "50" {
		t.Errorf("query = %v", q)
	}
	want := "NAME       TYPE     START       END         PLACE          ID\n" +
		"IGEF 2026  EXPO     2026-11-14  2026-11-16  Ankara, TR     e1\n" +
		"Go meetup  MEETUP   2026-12-01  -           NL             e2\n" +
		"Webinar    WEBINAR  2026-12-02  -           -              e3\n"
	if normalizeSpaces(out) != normalizeSpaces(want) {
		t.Errorf("out =\n%s", out)
	}
	if api.Request("GET /otak/events").Org != clitest.OrgIDAcme {
		t.Error("organization header missing")
	}
}

func TestEventListPastAndSearch(t *testing.T) {
	api := newEventAPI(t)
	queries := recordList(api)
	out, _, err := runCLI(t, "", "otak", "event", "list", "--period", "past", "--search", " igef ")
	if err != nil {
		t.Fatal(err)
	}
	q := (*queries)[0]
	if q.Get("period") != "PAST" || q.Get("today") != fixedToday || q.Get("search") != "igef" {
		t.Errorf("query = %v", q)
	}
	if out != "No events.\n" {
		t.Errorf("out = %q", out)
	}
}

func TestEventListWithoutPeriodSendsNoToday(t *testing.T) {
	api := newEventAPI(t)
	queries := recordList(api)
	if _, _, err := runCLI(t, "", "otak", "event", "list"); err != nil {
		t.Fatal(err)
	}
	q := (*queries)[0]
	if _, ok := q["period"]; ok {
		t.Errorf("query = %v", q)
	}
	if _, ok := q["today"]; ok {
		t.Errorf("query = %v", q)
	}
}

func TestEventListUnknownPeriodBeforeAnyCall(t *testing.T) {
	api := newEventAPI(t)
	_, _, err := runCLI(t, "", "otak", "event", "list", "--period", "soon")
	if err == nil || err.Error() != `unknown period "soon"; allowed: upcoming, past` {
		t.Fatalf("err = %v", err)
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}

func TestEventListSanitizesCells(t *testing.T) {
	api := newEventAPI(t)
	recordList(api, `{"data":[{"id":"e1","name":"Evil\u001b[31m\tExpo","type":"EXPO","startDate":"2026-11-14","endDate":null,"city":"An\nkara","country":null,"website":null}],"pagination":{"currentPage":1,"last":true}}`)
	out, _, err := runCLI(t, "", "otak", "event", "list")
	if err != nil {
		t.Fatal(err)
	}
	if strings.ContainsRune(out, 0x1b) || strings.Contains(out, "\t") || len(strings.Split(strings.TrimSpace(out), "\n")) != 2 {
		t.Errorf("out = %q", out)
	}
}

func TestEventCreatePiped(t *testing.T) {
	api := newEventAPI(t)
	api.Respond("POST /otak/events", http.StatusCreated, eventCreatedJSON)
	in := `{"name": "IGEF 2026", "type": "EXPO",  "startDate": "2026-11-04", "country": "TR"}`
	out, _, err := runCLI(t, in, "otak", "event", "create")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Created event IGEF 2026 ("+eventID+")\n" {
		t.Errorf("out = %q", out)
	}
	want := `{"name":"IGEF 2026","type":"EXPO","startDate":"2026-11-04","country":"TR"}`
	if got := api.Request("POST /otak/events").Body; got != want {
		t.Errorf("body = %s", got)
	}
}

func TestEventCreateRefusals(t *testing.T) {
	tests := []struct {
		name, stdin, want string
		terminal          bool
	}{
		{"unknown key", `{"name":"x","type":"EXPO","startDate":"2026-11-04","stratDate":"x"}`, `unknown field "stratDate". Did you mean "startDate"?`, false},
		{"not an object", `[1]`, "not an array", false},
		{"no stdin", ``, "no JSON on stdin; pass one JSON object, as 'oba otak event create --help' shows", true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api := newEventAPI(t)
			clitest.FakeTerminal(t, &stdinIsTerminal, tt.terminal)
			_, _, err := runCLI(t, tt.stdin, "otak", "event", "create")
			if err == nil || !strings.Contains(err.Error(), tt.want) {
				t.Fatalf("err = %v, want %q", err, tt.want)
			}
			if got := api.Calls(); len(got) != 0 {
				t.Errorf("calls = %v", got)
			}
		})
	}
}

func TestEventCreateNameTaken(t *testing.T) {
	api := newEventAPI(t)
	api.Respond("POST /otak/events", http.StatusConflict,
		`{"error":{"code":"OTK_STA_013","message":"An event with this name already exists"}}`)
	_, _, err := runCLI(t, `{"name":"IGEF 2026","type":"EXPO","startDate":"2026-11-04"}`, "otak", "event", "create")
	if err == nil || err.Error() != "OTK_STA_013: An event with this name already exists" || client.ExitCode(err) != 1 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
}

func TestEventUpdateMerges(t *testing.T) {
	api := newEventAPI(t)
	api.Respond("GET "+eventPath, http.StatusOK, eventDetailJSON)
	api.Respond("PUT "+eventPath, http.StatusOK, eventDetailJSON)
	out, _, err := runCLI(t, `{"endDate": "2026-11-07", "notes": null}`, "otak", "event", "update", eventID)
	if err != nil {
		t.Fatal(err)
	}
	if out != "Updated event "+eventID+"\n" {
		t.Errorf("out = %q", out)
	}
	if got := strings.Join(api.Calls(), ","); got != "GET "+eventPath+",PUT "+eventPath {
		t.Errorf("calls = %s", got)
	}
	var body map[string]any
	if err := json.Unmarshal([]byte(api.Request("PUT "+eventPath).Body), &body); err != nil {
		t.Fatal(err)
	}
	want := map[string]any{
		"name": "IGEF 2026", "type": "EXPO", "startDate": "2026-11-04", "endDate": "2026-11-07",
		"city": "Ankara", "country": "TR", "website": "https://igef.com.tr/", "notes": nil,
	}
	if !jsonEqual(body, want) {
		t.Errorf("body = %v", body)
	}
}

func TestEventUpdateRefusals(t *testing.T) {
	tests := []struct {
		name, stdin, want string
		terminal          bool
	}{
		{"unknown key", `{"nme":"x"}`, `unknown field "nme". Did you mean "name"?`, false},
		{"empty object", `{}`, "Nothing to update.", false},
		{"no stdin", ``, "no JSON on stdin; pass one JSON object, as 'oba otak event update --help' shows", true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api := newEventAPI(t)
			clitest.FakeTerminal(t, &stdinIsTerminal, tt.terminal)
			_, _, err := runCLI(t, tt.stdin, "otak", "event", "update", eventID)
			if err == nil || err.Error() != tt.want {
				t.Fatalf("err = %v, want %q", err, tt.want)
			}
			if got := api.Calls(); len(got) != 0 {
				t.Errorf("calls = %v", got)
			}
		})
	}
}

func TestEventUpdateNotFound(t *testing.T) {
	api := newEventAPI(t)
	api.Respond("GET "+eventPath, http.StatusNotFound, `{"error":{"code":"OTK_NOT_009","message":"Event not found"}}`)
	_, _, err := runCLI(t, `{"name":"x"}`, "otak", "event", "update", eventID)
	if err == nil || err.Error() != "OTK_NOT_009: Event not found" || client.ExitCode(err) != 2 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
	if got := api.Calls(); len(got) != 1 {
		t.Errorf("calls = %v", got)
	}
}

func TestEventDelete(t *testing.T) {
	tests := []struct {
		name, stdin string
		args        []string
		terminal    bool
		wantDelete  bool
		wantOut     string
		wantErr     error
	}{
		{"--yes", "", []string{"--yes"}, false, true, "Deleted event " + eventID + "\n", nil},
		{"confirmed", "y\n", nil, true, true, "Delete event " + eventID + "? (y/n) Deleted event " + eventID + "\n", nil},
		{"declined", "n\n", nil, true, false, "Delete event " + eventID + "? (y/n) Cancelled.\n", nil},
		{"no terminal", "", nil, false, false, "", terminal.ErrDeleteNeedsYes},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			api := newEventAPI(t)
			clitest.FakeTerminal(t, &stdinIsTerminal, tt.terminal)
			api.Respond("DELETE "+eventPath, http.StatusOK, `{"message":"ok"}`)
			out, _, err := runCLILines(t, tt.stdin, append([]string{"otak", "event", "delete", eventID}, tt.args...)...)
			if !errors.Is(err, tt.wantErr) {
				t.Fatalf("err = %v, want %v", err, tt.wantErr)
			}
			if out != tt.wantOut {
				t.Errorf("out = %q", out)
			}
			deleted := len(api.Calls()) == 1 && api.Calls()[0] == "DELETE "+eventPath
			if deleted != tt.wantDelete {
				t.Errorf("calls = %v", api.Calls())
			}
		})
	}
}

func TestEventDeleteForbidden(t *testing.T) {
	api := newEventAPI(t)
	api.Respond("DELETE "+eventPath, http.StatusForbidden, `{"error":{"code":"ORG_SEC_003","message":"Insufficient role"}}`)
	_, _, err := runCLI(t, "", "otak", "event", "delete", eventID, "--yes")
	if err == nil || err.Error() != "ORG_SEC_003: Insufficient role" || client.ExitCode(err) != 3 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
}

func TestEventHelpListsLiveFields(t *testing.T) {
	newEventAPI(t)
	out, _, err := runCLI(t, "", "otak", "event", "create", "--help")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, `"startDate": string (date),  (required)`) || !strings.Contains(out, `"type": "EXPO" | "CONFERENCE"`) {
		t.Errorf("out = %s", out)
	}
	out, _, err = runCLI(t, "", "otak", "event", "update", "--help")
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

func jsonEqual(a, b map[string]any) bool {
	ja, errA := json.Marshal(a)
	jb, errB := json.Marshal(b)
	return errA == nil && errB == nil && string(ja) == string(jb)
}
