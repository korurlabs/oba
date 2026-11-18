package updates

import (
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

func TestCreateSendsTheBodyOfTheFlags(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST "+updatesPath, http.StatusCreated, `{"data":{"id":"`+idUpdate+`","summaryText":"SUMMARY TEXT\n",`+
		`"items":[{"ticketKey":"OPS-7","percentage":40,"progressSynced":true}]}}`)

	out, errOut, err := run(t, "", "create", "--tickets", "OPS-7,KAGI-2", "--percent", "OPS-7=40", "--percent", "kagi-2=20",
		"--note", "KAGI-2=a, b", "--focus", " Menu ", "--yes")
	if err != nil {
		t.Fatal(err)
	}
	ws := writes(api)
	if len(ws) != 1 || ws[0].Path != updatesPath {
		t.Fatalf("writes = %+v", ws)
	}
	want := `{"tickets":[{"ticketId":"` + idTicket1 + `","percentage":40},{"ticketId":"` + idTicket2 + `","percentage":20,"note":"a, b"}],"focus":"Menu"}`
	if ws[0].Body != want {
		t.Errorf("body = %s\nwant   %s", ws[0].Body, want)
	}
	for _, call := range api.Calls() {
		if strings.Contains(call, "/tickets/"+idTicket1) || strings.Contains(call, "/tickets/"+idTicket2) {
			t.Errorf("a ticket named by key was read twice: %s", call)
		}
	}
	if out != "SUMMARY TEXT\n" {
		t.Errorf("out = %q, want only the summary text", out)
	}
	if !strings.Contains(errOut, "Posted update "+idUpdate) || !strings.Contains(errOut, "Ticket progress set: OPS-7 40%") {
		t.Errorf("stderr = %q", errOut)
	}
}

func TestCreateWithoutTicketsUsesTheSuggestions(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET "+updatesPath+"/suggested-tickets", http.StatusOK, `{"data":[`+
		`{"id":"`+idTicket1+`","key":"OPS-7","title":"T","percentage":30},`+
		`{"id":"`+idTicket2+`","key":"KAGI-2","title":"U","percentage":55}]}`)
	api.Respond("POST "+updatesPath, http.StatusCreated, `{"data":{"id":"`+idUpdate+`","summaryText":"OK"}}`)

	if _, _, err := run(t, "", "create", "--focus", "Menu"); err != nil {
		t.Fatal(err)
	}
	ws := writes(api)
	want := `{"tickets":[{"ticketId":"` + idTicket1 + `","percentage":30},{"ticketId":"` + idTicket2 + `","percentage":55}],"focus":"Menu"}`
	if len(ws) != 1 || ws[0].Path != updatesPath || ws[0].Body != want {
		t.Fatalf("writes = %+v", ws)
	}
}

func TestCreateWithoutTicketsFailsWhenNoneIsSuggested(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET "+updatesPath+"/suggested-tickets", http.StatusOK, `{"data":[]}`)
	_, _, err := run(t, "", "create", "--focus", "Menu")
	if err == nil || !strings.Contains(err.Error(), "--tickets") {
		t.Fatalf("err = %v, want one that names --tickets", err)
	}
	if len(writes(api)) != 0 {
		t.Error("nothing may be posted when no ticket is suggested")
	}
}

func TestPreviewSendsTheBodyAndSavesNothing(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST "+updatesPath+"/preview", http.StatusOK, `{"data":{"summaryText":"PREVIEW",`+
		`"items":[{"ticketKey":"OPS-7","percentage":10,"progressSynced":true}]}}`)

	out, errOut, err := run(t, "", "preview", "--tickets", "OPS-7", "--percent", "OPS-7=10", "--questions", "Q?")
	if err != nil {
		t.Fatal(err)
	}
	ws := writes(api)
	want := `{"tickets":[{"ticketId":"` + idTicket1 + `","percentage":10}],"questions":"Q?"}`
	if len(ws) != 1 || ws[0].Path != updatesPath+"/preview" || ws[0].Body != want {
		t.Fatalf("writes = %+v", ws)
	}
	if out != "PREVIEW\n" || !strings.Contains(errOut, "Would set ticket progress: OPS-7 10%") {
		t.Errorf("out %q stderr %q", out, errOut)
	}
}

func TestCreateAndEditFlagErrorsComeBeforeAnyRequest(t *testing.T) {
	cases := []struct {
		name    string
		args    []string
		wantErr string
	}{
		{"percent without equals", []string{"create", "--tickets", "OPS-7", "--percent", "OPS-7"}, "KEY=VALUE"},
		{"percent not a number", []string{"create", "--tickets", "OPS-7", "--percent", "OPS-7=abc"}, "whole number"},
		{"percent above 100", []string{"create", "--tickets", "OPS-7", "--percent", "OPS-7=101"}, "100"},
		{"percent below 0", []string{"create", "--tickets", "OPS-7", "--percent", "OPS-7=-1"}, "whole number"},
		{"same percent key twice", []string{"create", "--tickets", "OPS-7", "--percent", "OPS-7=1", "--percent", "ops-7=2"}, "twice"},
		{"note without equals", []string{"create", "--tickets", "OPS-7", "--note", "just text"}, "KEY=VALUE"},
		{"percent key not in tickets", []string{"preview", "--tickets", "OPS-7", "--percent", "KAGI-2=5"}, "KAGI-2"},
		{"tickets names none", []string{"preview", "--tickets", " , "}, "--tickets names no ticket"},
		{"create without focus", []string{"create", "--tickets", "OPS-7", "--yes"}, "--focus is required: what you will focus on today"},
		{"create with blank focus", []string{"create", "--tickets", "OPS-7", "--focus", "  ", "--yes"}, "--focus is required"},
		{"create from suggestions without focus", []string{"create"}, "--focus is required"},
		{"edit percent out of range", []string{"edit", idUpdate, "--percent", "OPS-7=150"}, "100"},
		{"edit note without equals", []string{"edit", idUpdate, "--note", "x"}, "KEY=VALUE"},
		{"edit with empty focus", []string{"edit", idUpdate, "--focus", ""}, "--focus cannot be empty"},
		{"edit with blank focus", []string{"edit", idUpdate, "--percent", "OPS-7=60", "--focus", "  "}, "--focus cannot be empty"},
		{"edit without changes", []string{"edit", idUpdate}, "no changes specified"},
		{"edit of a non-id", []string{"edit", "OPS-7", "--focus", "x"}, "invalid update ID"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			api := newAPI(t)
			_, _, err := run(t, "", tc.args...)
			if err == nil || !strings.Contains(err.Error(), tc.wantErr) {
				t.Fatalf("err = %v, want it to contain %q", err, tc.wantErr)
			}
			if client.ExitCode(err) != 1 {
				t.Errorf("exit code %d, want 1", client.ExitCode(err))
			}
			if calls := api.Calls(); len(calls) != 0 {
				t.Errorf("requests were sent: %v", calls)
			}
		})
	}
}

func TestCreateOnATerminalAsksAndShowsThePreview(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	api.Respond("POST "+updatesPath+"/preview", http.StatusOK, `{"data":{"summaryText":"PREVIEW"}}`)
	api.Respond("POST "+updatesPath, http.StatusCreated, `{"data":{"id":"`+idUpdate+`","summaryText":"POSTED"}}`)

	// Percentage of OPS-7 (Enter keeps 30), its note, the focus, the questions, then yes.
	out, _, err := run(t, "\nWaiting\nShip it\n\ny\n", "create", "--tickets", "OPS-7")
	if err != nil {
		t.Fatal(err)
	}
	ws := writes(api)
	want := `{"tickets":[{"ticketId":"` + idTicket1 + `","percentage":30,"note":"Waiting"}],"focus":"Ship it"}`
	if len(ws) != 2 || ws[0].Path != updatesPath+"/preview" || ws[1].Path != updatesPath || ws[1].Body != want {
		t.Fatalf("writes = %+v", ws)
	}
	if !strings.Contains(out, "PREVIEW") || !strings.Contains(out, "Post this update? (y/n)") || !strings.HasSuffix(out, "POSTED\n") {
		t.Errorf("out = %q", out)
	}
}

func TestCreateOnATerminalAnsweredNoPostsNothing(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	api.Respond("POST "+updatesPath+"/preview", http.StatusOK, `{"data":{"summaryText":"PREVIEW"}}`)

	out, _, err := run(t, "n\n", "create", "--tickets", "OPS-7", "--percent", "OPS-7=5", "--note", "OPS-7=", "--focus", "F", "--questions", "")
	if err != nil {
		t.Fatal(err)
	}
	for _, w := range writes(api) {
		if w.Path == updatesPath {
			t.Fatal("a cancelled create must not post")
		}
	}
	if !strings.HasSuffix(out, "Cancelled.\n") {
		t.Errorf("out = %q", out)
	}
}

func TestCreateOnATerminalPicksFromMyOpenTickets(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	api.Respond("GET "+updatesPath+"/suggested-tickets", http.StatusOK, `{"data":[]}`)
	api.Respond("GET /users/me", http.StatusOK, clitest.UserMeJSON)
	var query string
	queryOf(api, "GET /kazan/tickets", `{"data":[`+
		`{"id":"`+idTicket1+`","key":"OPS-7","title":"T","status":"TODO","percentage":10},`+
		`{"id":"`+idTicket2+`","key":"KAGI-2","title":"U","status":"WAITING_FOR_THIRD_PARTY","percentage":20}]}`, &query)
	api.Respond("POST "+updatesPath, http.StatusCreated, `{"data":{"id":"`+idUpdate+`","summaryText":"POSTED"}}`)

	if _, _, err := run(t, "2\n", "create", "--percent", "KAGI-2=25", "--note", "KAGI-2=", "--focus", "F", "--questions", "", "--yes"); err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(query, "assigneeIds=u1") || !strings.Contains(query, "statuses=WAITING_FOR_THIRD_PARTY") || strings.Contains(query, "DONE") {
		t.Errorf("query = %s", query)
	}
	ws := writes(api)
	want := `{"tickets":[{"ticketId":"` + idTicket2 + `","percentage":25}],"focus":"F"}`
	if len(ws) != 1 || ws[0].Body != want {
		t.Fatalf("writes = %+v", ws)
	}
}

func TestCreateByTicketIDNeedsABoard(t *testing.T) {
	newAPI(t)
	_, _, err := run(t, "", "create", "--tickets", idTicket1, "--focus", "F")
	if err == nil || !strings.Contains(err.Error(), "pass its key instead") {
		t.Fatalf("err = %v", err)
	}
}

func TestCreateByTicketIDReadsItOnTheBoardFlag(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST "+updatesPath, http.StatusCreated, `{"data":{"id":"`+idUpdate+`","summaryText":"OK"}}`)
	if _, _, err := clitest.RunCLILines(t, newRoot(), "", "kazan", "--board", "OPS", "updates", "create",
		"--tickets", idTicket1, "--focus", "F"); err != nil {
		t.Fatal(err)
	}
	want := `{"tickets":[{"ticketId":"` + idTicket1 + `","percentage":30}],"focus":"F"}`
	if api.Request("POST "+updatesPath).Body != want {
		t.Errorf("body = %s", api.Request("POST "+updatesPath).Body)
	}
}

func TestCreateJSONIsThePostedUpdate(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST "+updatesPath, http.StatusCreated, `{"data":{"id":"`+idUpdate+`","summaryText":"S","items":[{"ticketKey":"OPS-7","canOpen":true}]}}`)
	flush := captureJSON(t)
	if _, _, err := run(t, "", "create", "--tickets", "OPS-7", "--focus", "F"); err != nil {
		t.Fatal(err)
	}
	got := flush()
	if !strings.Contains(got, `"id": "`+idUpdate+`"`) || !strings.Contains(got, `"canOpen": true`) || strings.Contains(got, `"percentage": 30`) {
		t.Errorf("json =\n%s", got)
	}
}

func TestCreateForbiddenExitsThree(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST "+updatesPath, http.StatusForbidden, `{"error":{"code":"KAI_FORBIDDEN","message":"Forbidden"}}`)
	_, _, err := run(t, "", "create", "--tickets", "OPS-7", "--focus", "F")
	if client.ExitCode(err) != 3 {
		t.Fatalf("err = %v, exit %d", err, client.ExitCode(err))
	}
}
