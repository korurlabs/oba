package pr

import (
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

const createdShareJSON = `{"data":{"id":"` + idShare + `","summaryText":"CARD\n","relatedTickets":[],"channelId":"ch1","messageId":"msg1"}}`

func TestShareFromPresetStatusSendsTheBody(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST "+sharesPath, http.StatusCreated, createdShareJSON)
	out, errOut, err := run(t, "", "share", "https://github.com/senseylabs/village/pull/688")
	if err != nil {
		t.Fatal(err)
	}
	want := `{"githubRepositoryId":101,"number":688,"status":"OPEN","reviewerIds":[],"relatedTicketIds":[]}`
	if body := api.Request("POST " + sharesPath).Body; body != want {
		t.Errorf("body = %s", body)
	}
	if out != "CARD\n" {
		t.Errorf("out = %q", out)
	}
	for _, part := range []string{"Shared " + idShare, "Tickets: none", "Kazan: https://kazan.example.com/pull-requests?jump=msg1"} {
		if !strings.Contains(errOut, part) {
			t.Errorf("stderr missing %q:\n%s", part, errOut)
		}
	}
}

func TestShareWithNoteReviewersAndTickets(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST "+sharesPath, http.StatusCreated, createdShareJSON)
	_, _, err := run(t, "", "share", "688", "--repo", "village", "--status", "in-review",
		"--note", " Ready ", "--reviewers", "ada@x.io,"+idBob, "--tickets", "ops-12,"+idTicket2)
	if err != nil {
		t.Fatal(err)
	}
	want := `{"githubRepositoryId":101,"number":688,"status":"IN_REVIEW","note":"Ready",` +
		`"reviewerIds":["` + idAda + `","` + idBob + `"],"relatedTicketIds":["` + idTicket + `","` + idTicket2 + `"]}`
	if body := api.Request("POST " + sharesPath).Body; body != want {
		t.Errorf("body = %s", body)
	}
	for _, call := range api.Calls() {
		if call == "GET "+pullsPath+"/688" {
			t.Error("read the pull request although --status was given")
		}
	}
}

func TestShareClosedPullRequestNeedsStatus(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET "+pullsPath+"/688", http.StatusOK, pullJSON("CLOSED"))
	_, _, err := run(t, "", "share", "village#688")
	if err == nil || !strings.Contains(err.Error(), "is closed on GitHub, so no status is preset: pass --status") {
		t.Fatalf("err = %v", err)
	}
	if w := writes(api); len(w) != 0 {
		t.Errorf("writes = %v", w)
	}
}

func TestShareRefusals(t *testing.T) {
	cases := []struct {
		name string
		args []string
		want string
		code int
	}{
		{"no pull request without a terminal", nil, "a pull request is required", 1},
		{"bad status", []string{"village#688", "--status", "closed"}, "--status: invalid status", 1},
		{"long note", []string{"village#688", "--note", strings.Repeat("x", 501)}, "--note must be at most 500", 1},
		{"reviewer twice", []string{"village#688", "--reviewers", "ada@x.io,ADA@x.io"}, "names ADA@x.io twice", 1},
		{"bad ticket", []string{"village#688", "--tickets", "nokey"}, "--tickets: invalid ticket", 1},
		{"reviewer is you", []string{"village#688", "--reviewers", "a@b.c"}, "--reviewers: reviewer a@b.c is you", 1},
		{"same reviewer twice", []string{"village#688", "--reviewers", "ada@x.io," + idAda}, "are the same user", 1},
		{"unknown reviewer", []string{"village#688", "--reviewers", "nobody@x.io"}, "no member of", 2},
		{"disabled reviewer", []string{"village#688", "--reviewers", "gone@x.io"}, "no member of", 2},
		{"same ticket twice", []string{"village#688", "--tickets", "OPS-12," + idTicket}, "are the same ticket", 1},
		{"unknown repository", []string{"nosuch#1"}, "cannot read a repository \"nosuch\"", 2},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			api := newAPI(t)
			_, _, err := run(t, "", append([]string{"share"}, tc.args...)...)
			if err == nil || !strings.Contains(err.Error(), tc.want) || client.ExitCode(err) != tc.code {
				t.Fatalf("err = %v (exit %d), want %q (exit %d)", err, client.ExitCode(err), tc.want, tc.code)
			}
			if w := writes(api); len(w) != 0 {
				t.Errorf("writes = %v", w)
			}
		})
	}
}

func TestShareServerErrorKeepsItsExitCode(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST "+sharesPath, http.StatusNotFound, `{"error":{"code":"KAI_GH","message":"GitHub is not connected for this organization."}}`)
	_, _, err := run(t, "", "share", "village#688")
	if client.ExitCode(err) != 2 || !strings.Contains(err.Error(), "GitHub is not connected") {
		t.Fatalf("err = %v", err)
	}
}

func TestShareJSONIsTheShare(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST "+sharesPath, http.StatusCreated, createdShareJSON)
	flush := captureJSON(t)
	if _, _, err := run(t, "", "share", "village#688", "--reviewers", "ada@x.io"); err != nil {
		t.Fatal(err)
	}
	got := flush()
	if !strings.Contains(got, `"id": "`+idShare+`"`) || strings.Contains(got, `"email"`) {
		t.Errorf("json =\n%s", got)
	}
}

func TestShareOnATerminalShowsTheCardAndAsks(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	api.Respond("POST "+sharesPath+"/preview", http.StatusOK, `{"data":{"summaryText":"PREVIEW CARD"}}`)
	api.Respond("POST "+sharesPath, http.StatusCreated, createdShareJSON)
	out, _, err := run(t, "y\n", "share", "village#688", "--status", "open", "--note", "n", "--reviewers", "")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "PREVIEW CARD") || !strings.Contains(out, "Share this pull request? (y/n)") {
		t.Errorf("out = %q", out)
	}
	if strings.Join(writes(api), ",") != "POST "+sharesPath+"/preview,POST "+sharesPath {
		t.Errorf("writes = %v", writes(api))
	}
}

func TestShareOnATerminalAsksForWhatTheFlagsLeaveOpen(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	api.Respond("POST "+sharesPath+"/preview", http.StatusOK, `{"data":{"summaryText":"PREVIEW"}}`)
	api.Respond("POST "+sharesPath, http.StatusCreated, createdShareJSON)
	// Status: 3 (In Review); note: typed; reviewers: 1 (Ada); confirm: y.
	out, _, err := run(t, "3\nLooks good\n1\ny\n", "share", "village#688")
	if err != nil {
		t.Fatal(err)
	}
	want := `{"githubRepositoryId":101,"number":688,"status":"IN_REVIEW","note":"Looks good",` +
		`"reviewerIds":["` + idAda + `"],"relatedTicketIds":[]}`
	if body := api.Request("POST " + sharesPath).Body; body != want {
		t.Errorf("body = %s\nout = %s", body, out)
	}
	if !strings.Contains(out, "Open (from GitHub)") || strings.Contains(out, "guest@x.io") || strings.Contains(out, "<a@b.c>") {
		t.Errorf("out = %s", out)
	}
}

func TestShareOnATerminalCancelled(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	api.Respond("POST "+sharesPath+"/preview", http.StatusOK, `{"data":{"summaryText":"PREVIEW"}}`)
	out, _, err := run(t, "n\n", "share", "village#688", "--status", "open", "--note", "", "--reviewers", "")
	if err != nil || !strings.HasSuffix(out, "Cancelled.\n") {
		t.Fatalf("err = %v, out = %q", err, out)
	}
	if strings.Join(writes(api), ",") != "POST "+sharesPath+"/preview" {
		t.Errorf("writes = %v", writes(api))
	}
}

func TestPreviewPostsToPreviewOnly(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST "+sharesPath+"/preview", http.StatusOK, `{"data":{"summaryText":"PREVIEW CARD\n","pullRequest":{"number":688}}}`)
	flush := captureJSON(t)
	out, _, err := run(t, "", "preview", "village#688", "--status", "discuss")
	if err != nil {
		t.Fatal(err)
	}
	if out != "PREVIEW CARD\n" || strings.Join(writes(api), ",") != "POST "+sharesPath+"/preview" {
		t.Errorf("out %q writes %v", out, writes(api))
	}
	if got := flush(); !strings.Contains(got, `"summaryText": "PREVIEW CARD\n"`) {
		t.Errorf("json =\n%s", got)
	}
}

func TestReposTableSortedByName(t *testing.T) {
	newAPI(t)
	out, _, err := run(t, "", "repos")
	if err != nil {
		t.Fatal(err)
	}
	agents, village := strings.Index(out, "senseylabs/agents"), strings.Index(out, "senseylabs/village")
	if !strings.HasPrefix(out, "REPOSITORY") || agents < 0 || village < agents || !strings.Contains(out, "101  yes") {
		t.Errorf("out =\n%s", out)
	}
}

func TestPullsQueryAndOutput(t *testing.T) {
	api := newAPI(t)
	query := captureQuery(api, "GET "+pullsPath, `{"data":{"items":[{"number":688,"title":"Add sidebar","state":"OPEN",`+
		`"authorLogin":"ghopper","updatedAt":"2026-10-27T09:30:00Z"}],"page":2,"hasMore":true,"resultsLimited":true}}`)
	flush := captureJSON(t)
	out, _, err := run(t, "", "pulls", "--repo", "village", "--search", "side", "--state", "open,merged",
		"--author", "ghopper", "--page", "2")
	if err != nil {
		t.Fatal(err)
	}
	if *query != "authors=ghopper&page=2&search=side&states=OPEN&states=MERGED" {
		t.Errorf("query = %s", *query)
	}
	for _, part := range []string{"#688", "ghopper", "Add sidebar", "Use --page 3.", "first 1000 results"} {
		if !strings.Contains(out, part) {
			t.Errorf("out missing %q:\n%s", part, out)
		}
	}
	if got := flush(); !strings.Contains(got, `"hasMore": true`) || strings.Contains(got, "fullName") {
		t.Errorf("json =\n%s", got)
	}
}

func TestPullsRefusals(t *testing.T) {
	cases := map[string][]string{
		"--repo is required":        {"pulls"},
		"--page must be 1 or more":  {"pulls", "--repo", "village", "--page", "0"},
		"invalid --state value":     {"pulls", "--repo", "village", "--state", "in_review"},
		"invalid GitHub login":      {"pulls", "--repo", "village", "--author", "a@b.c"},
		"--search must be at most":  {"pulls", "--repo", "village", "--search", strings.Repeat("s", 101)},
		"cannot read a repository ": {"pulls", "--repo", "nosuch"},
	}
	for want, args := range cases {
		api := newAPI(t)
		_, _, err := run(t, "", args...)
		if err == nil || !strings.Contains(err.Error(), want) {
			t.Errorf("%v: err = %v, want %q", args, err, want)
		}
		for _, call := range api.Calls() {
			if strings.HasPrefix(call, "GET "+pullsPath) {
				t.Errorf("%v: unexpected %s", args, call)
			}
		}
	}
}

func TestListQueryAndBlocks(t *testing.T) {
	api := newAPI(t)
	query := captureQuery(api, "GET "+sharesPath, `{"data":[`+strings.TrimSuffix(strings.TrimPrefix(shareJSON, `{"data":`), "}")+`],`+
		`"pagination":{"currentPage":1,"totalPages":3,"totalElements":55}}`)
	out, _, err := run(t, "", "list", "--status", "in-review,discuss", "--author", "me,bob@x.io", "--pending", "--amount", "10")
	if err != nil {
		t.Fatal(err)
	}
	want := "amount=10&authorIds=u1&authorIds=" + idBob + "&awaitingMyReview=true&page=1&statuses=IN_REVIEW&statuses=DISCUSS"
	if *query != want {
		t.Errorf("query = %s", *query)
	}
	for _, part := range []string{
		"senseylabs/village#688  In Review  Grace Hopper", "Add sidebar",
		"Reviewers: Ada Lovelace ✓, Bob Stone (pending)", "Tickets: OPS-12 Sidebar",
		"GitHub: https://github.com/senseylabs/village/pull/688",
		"Kazan: https://kazan.example.com/pull-requests?jump=msg1", "ID: " + idShare,
		"Page 1 of 3 (55 shares). Use --page for more.",
	} {
		if !strings.Contains(out, part) {
			t.Errorf("out missing %q:\n%s", part, out)
		}
	}
}

func TestListRefusalsSendNothing(t *testing.T) {
	for want, args := range map[string][]string{
		"--page must be 1 or more": {"list", "--page", "0"},
		"--amount must be from 1":  {"list", "--amount", "51"},
		"--status: invalid status": {"list", "--status", "closed"},
	} {
		api := newAPI(t)
		if _, _, err := run(t, "", args...); err == nil || !strings.Contains(err.Error(), want) {
			t.Errorf("%v: err = %v", args, err)
		}
		if len(api.Calls()) != 0 {
			t.Errorf("%v: calls = %v", args, api.Calls())
		}
	}
}

func TestListEmpty(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET "+sharesPath, http.StatusOK, `{"data":[],"pagination":{"currentPage":1,"totalPages":0}}`)
	out, _, err := run(t, "", "list")
	if err != nil || out != "No pull request shares found.\n" {
		t.Fatalf("err %v out %q", err, out)
	}
}

func TestGetCardOnStdoutAndBlockOnStderr(t *testing.T) {
	newAPI(t)
	t.Setenv("OBA_KAZAN_WEB_URL", "http://localhost:3002/")
	out, errOut, err := run(t, "", "get", idShare)
	if err != nil {
		t.Fatal(err)
	}
	if out != "CARD TEXT\n" || !strings.Contains(errOut, "Kazan: http://localhost:3002/pull-requests?jump=msg1") ||
		strings.Contains(errOut, "Add sidebar") {
		t.Errorf("out %q stderr:\n%s", out, errOut)
	}
}

func TestShareIDMustBeAUUIDBeforeAnyRequest(t *testing.T) {
	for _, sub := range []string{"get", "edit", "delete", "approve", "withdraw"} {
		api := newAPI(t)
		_, _, err := run(t, "", sub, "OPS-12", "--yes")
		if sub == "get" || sub == "approve" || sub == "withdraw" {
			_, _, err = run(t, "", sub, "OPS-12")
		}
		if err == nil || !strings.Contains(err.Error(), "invalid share ID") {
			t.Errorf("%s: err = %v", sub, err)
		}
		if len(api.Calls()) != 0 {
			t.Errorf("%s: calls = %v", sub, api.Calls())
		}
	}
}

func TestEditSendsTheFullBody(t *testing.T) {
	api := newAPI(t)
	api.Respond("PUT "+sharePath, http.StatusOK, `{"data":{"id":"`+idShare+`","summaryText":"EDITED"}}`)
	out, errOut, err := run(t, "", "edit", idShare, "--status", "merged", "--note", "",
		"--remove-reviewers", "bob@x.io", "--add-tickets", idTicket2, "--remove-tickets", "ops-12")
	if err != nil {
		t.Fatal(err)
	}
	want := `{"status":"MERGED","reviewerIds":["` + idAda + `"],"relatedTicketIds":["` + idTicket2 + `"]}`
	if body := api.Request("PUT " + sharePath).Body; body != want {
		t.Errorf("body = %s", body)
	}
	if out != "EDITED\n" || !strings.Contains(errOut, "Saved share "+idShare) {
		t.Errorf("out %q stderr %q", out, errOut)
	}
}

func TestEditReplacesTheReviewers(t *testing.T) {
	api := newAPI(t)
	api.Respond("PUT "+sharePath, http.StatusOK, `{"data":{"id":"`+idShare+`"}}`)
	if _, _, err := run(t, "", "edit", idShare, "--reviewers", ""); err != nil {
		t.Fatal(err)
	}
	want := `{"status":"IN_REVIEW","note":"keep me","reviewerIds":[],"relatedTicketIds":["` + idTicket + `"]}`
	if body := api.Request("PUT " + sharePath).Body; body != want {
		t.Errorf("body = %s", body)
	}
}

func TestEditWithoutDifferenceSendsNoPutAndPrintsTheShare(t *testing.T) {
	api := newAPI(t)
	flush := captureJSON(t)
	out, _, err := run(t, "", "edit", idShare, "--status", "in_review", "--tickets", "OPS-12", "--add-reviewers", "")
	if err != nil {
		t.Fatal(err)
	}
	if out != "No changes to share "+idShare+".\n" || len(writes(api)) != 0 {
		t.Errorf("out %q writes %v", out, writes(api))
	}
	if got := flush(); !strings.Contains(got, `"summaryText": "CARD TEXT\n"`) {
		t.Errorf("json =\n%s", got)
	}
}

func TestEditRefusals(t *testing.T) {
	cases := []struct {
		args []string
		want string
	}{
		{nil, "no changes specified"},
		{[]string{"--reviewers", "ada@x.io", "--add-reviewers", "bob@x.io"}, "--reviewers replaces the list"},
		{[]string{"--tickets", "OPS-1", "--remove-tickets", "OPS-12"}, "--tickets replaces the list"},
		{[]string{"--status", "closed"}, "--status: invalid status"},
		{[]string{"--remove-reviewers", "a@b.c"}, "not a reviewer of this share"},
		{[]string{"--add-reviewers", "ada@x.io"}, "already a reviewer of this share"},
		{[]string{"--add-reviewers", "a@b.c"}, "is you"},
		{[]string{"--remove-tickets", "OPS-99"}, "not a related ticket of this share"},
		{[]string{"--add-tickets", "OPS-12"}, "already a related ticket of this share"},
		{[]string{"--tickets", "OPS-12," + idTicket}, "are the same ticket"},
	}
	for _, tc := range cases {
		api := newAPI(t)
		_, _, err := run(t, "", append([]string{"edit", idShare}, tc.args...)...)
		if err == nil || !strings.Contains(err.Error(), tc.want) {
			t.Errorf("%v: err = %v, want %q", tc.args, err, tc.want)
		}
		if len(writes(api)) != 0 {
			t.Errorf("%v: writes = %v", tc.args, writes(api))
		}
	}
}

func TestEditForbiddenExitsThree(t *testing.T) {
	api := newAPI(t)
	api.Respond("PUT "+sharePath, http.StatusForbidden, `{"error":{"code":"KAI_PRS","message":"Only the author can edit or delete this pull request share."}}`)
	_, _, err := run(t, "", "edit", idShare, "--status", "merged")
	if client.ExitCode(err) != 3 {
		t.Fatalf("err = %v", err)
	}
}

func TestEditOnATerminalListsTheChangesAndAsks(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	api.Respond("PUT "+sharePath, http.StatusOK, `{"data":{"id":"`+idShare+`"}}`)
	out, _, err := run(t, "y\n", "edit", idShare, "--status", "merged")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Changes to senseylabs/village#688:\n  Status: In Review -> Merged\n") || len(writes(api)) != 1 {
		t.Errorf("out %q writes %v", out, writes(api))
	}
}

func TestDeleteNeedsYesWithoutTerminal(t *testing.T) {
	api := newAPI(t)
	if _, _, err := run(t, "", "delete", idShare); err != terminal.ErrDeleteNeedsYes {
		t.Fatalf("err = %v", err)
	}
	if len(api.Calls()) != 0 {
		t.Errorf("calls = %v", api.Calls())
	}
	api.Respond("DELETE "+sharePath, http.StatusOK, `{"data":null}`)
	out, _, err := run(t, "", "delete", idShare, "--yes")
	if err != nil || out != "Deleted share "+idShare+"\n" {
		t.Fatalf("err %v out %q", err, out)
	}
}

func TestDeleteOnATerminalAsks(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	out, _, err := run(t, "n\n", "delete", idShare)
	if err != nil || !strings.HasSuffix(out, "Cancelled.\n") || len(api.Calls()) != 0 {
		t.Fatalf("err %v out %q calls %v", err, out, api.Calls())
	}
}

func TestApproveAndWithdraw(t *testing.T) {
	api := newAPI(t)
	api.Respond("PUT "+sharePath+"/approval", http.StatusOK, `{"data":null}`)
	api.Respond("DELETE "+sharePath+"/approval", http.StatusOK, `{"data":null}`)
	out, _, err := run(t, "", "approve", idShare)
	if err != nil || out != "Approved "+idShare+"\n" || api.Request("PUT "+sharePath+"/approval").Body != "" {
		t.Fatalf("approve: err %v out %q", err, out)
	}
	out, _, err = run(t, "", "withdraw", idShare)
	if err != nil || out != "Withdrew approval on "+idShare+"\n" {
		t.Fatalf("withdraw: err %v out %q", err, out)
	}
}

func TestApproveForbiddenExitsThree(t *testing.T) {
	api := newAPI(t)
	api.Respond("PUT "+sharePath+"/approval", http.StatusForbidden,
		`{"error":{"code":"KAI_PRS","message":"Only a reviewer of this pull request share can approve it or withdraw the approval."}}`)
	_, _, err := run(t, "", "approve", idShare)
	if client.ExitCode(err) != 3 {
		t.Fatalf("err = %v", err)
	}
}
