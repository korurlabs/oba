package ticket

import (
	"encoding/json"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

// bodyOf decodes the body of the first request on route into a map.
func bodyOf(t *testing.T, api *clitest.MockAPI, route string) map[string]any {
	t.Helper()
	var got map[string]any
	if err := json.Unmarshal([]byte(api.Request(route).Body), &got); err != nil {
		t.Fatalf("body of %s: %v", route, err)
	}
	return got
}

// captureQuery answers route with body and keeps the query string of its last request.
func captureQuery(api *clitest.MockAPI, route, body string, query *string) {
	api.Handle(route, func(w http.ResponseWriter, r *http.Request) {
		*query = r.URL.RawQuery
		clitest.WriteJSON(w, body)
	})
}

func TestListWithoutArgumentsListsTheBacklog(t *testing.T) {
	api := newTicketAPI(t)
	var query string
	captureQuery(api, "GET /kazan/boards/"+idOps+"/tickets",
		`{"data":[`+ticketJSON(idT42, "OPS-42", "Login crash", idOps, "OPS")+`]}`, &query)
	out, _, err := runCLI(t, "", "kazan", "ticket", "list", "--status", "todo,in_progress")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(out, "Backlog\n\n") || !strings.Contains(out, "OPS-42") || !strings.Contains(out, "Showing 1 tickets") {
		t.Errorf("out =\n%s", out)
	}
	if !strings.Contains(query, "backlogId="+idBacklog) || !strings.Contains(query, "statuses=TODO&statuses=IN_PROGRESS") {
		t.Errorf("query = %s", query)
	}
}

// withSubTickets is ticketJSON with sub-tickets.
func withSubTickets(id, key, title string, subTickets ...string) string {
	return strings.TrimSuffix(ticketJSON(id, key, title, idOps, "OPS"), "}") +
		`,"subTickets":[` + strings.Join(subTickets, ",") + `]}`
}

func TestAllWithSearchShowsTheMatchingSubTicketsUnderTheirTicket(t *testing.T) {
	api := newTicketAPI(t)
	grandchild := withSubTickets("g1", "OPS-52", "Locked out of the org")
	child := withSubTickets("c1", "OPS-51", "Approval flow", grandchild)
	sibling := withSubTickets("c2", "OPS-53", "Unrelated sibling")
	direct := strings.Replace(withSubTickets("c3", "OPS-54", "LOCKED OUT on sign-in"),
		`"sprint":{"id":"`+idSprint3+`","name":"Sprint 3"}`, `"sprint":null`, 1)
	parent := withSubTickets("p1", "OPS-50", "Go-live", child, sibling, direct)
	var query string
	captureQuery(api, "GET /kazan/tickets", `{"data":[`+parent+`]}`, &query)

	out, _, err := runCLI(t, "", "kazan", "ticket", "all", "--search", "locked out")
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{"OPS-50", "└ OPS-51", "  └ OPS-52", "└ OPS-54",
		"Showing 1 tickets and 3 sub-tickets that match the search"} {
		if !strings.Contains(out, want) {
			t.Errorf("out lacks %q:\n%s", want, out)
		}
	}
	if !regexp.MustCompile(`└ OPS-54 .*Backlog`).MatchString(out) {
		t.Errorf("a sub-ticket without a sprint is not shown in the backlog:\n%s", out)
	}
	if strings.Contains(out, "OPS-53") {
		t.Errorf("out lists the sibling that does not match:\n%s", out)
	}
	if !strings.Contains(query, "search=locked+out") {
		t.Errorf("query = %s", query)
	}
}

func TestListWithoutSearchShowsNoSubTickets(t *testing.T) {
	api := newTicketAPI(t)
	parent := strings.TrimSuffix(ticketJSON("p1", "OPS-50", "Go-live", idOps, "OPS"), "}") +
		`,"subTickets":[` + ticketJSON("c1", "OPS-51", "Approval flow", idOps, "OPS") + `]}`
	api.Respond("GET /kazan/tickets", http.StatusOK, `{"data":[`+parent+`]}`)

	out, _, err := runCLI(t, "", "kazan", "ticket", "all")
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(out, "OPS-51") || !strings.Contains(out, "Showing 1 tickets\n") {
		t.Errorf("out =\n%s", out)
	}
}

func TestListSprintByName(t *testing.T) {
	api := newTicketAPI(t)
	var query string
	captureQuery(api, "GET /kazan/boards/"+idOps+"/tickets", `{"data":[]}`, &query)
	out, _, err := runCLI(t, "", "kazan", "ticket", "list", "Sprint", "3")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Sprint: Sprint 3 (ACTIVE)\n\nNo tickets found.\n" {
		t.Errorf("out = %q", out)
	}
	if !strings.Contains(query, "sprintId="+idSprint3) {
		t.Errorf("query = %s", query)
	}
}

func TestListBadStatusMakesNoCall(t *testing.T) {
	api := newTicketAPI(t)
	_, _, err := runCLI(t, "", "kazan", "ticket", "list", "--status", "NOPE")
	if err == nil || !strings.Contains(err.Error(), "--status") {
		t.Fatalf("err = %v", err)
	}
	if len(api.Calls()) != 0 {
		t.Errorf("calls = %v", api.Calls())
	}
}

func TestAllNarrowsToBoards(t *testing.T) {
	api := newTicketAPI(t)
	var query string
	captureQuery(api, "GET /kazan/tickets", `{"data":[]}`, &query)
	out, _, err := runCLI(t, "", "kazan", "ticket", "all", "--boards", "kagi")
	if err != nil || out != "No tickets found.\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	if !strings.Contains(query, "boardIds="+idKagi) {
		t.Errorf("query = %s", query)
	}
}

func TestMineFiltersOnTheSignedInUser(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("GET /users/me", http.StatusOK, clitest.UserMeJSON)
	var query string
	captureQuery(api, "GET /kazan/tickets", `{"data":[]}`, &query)
	out, _, err := runCLI(t, "", "kazan", "ticket", "mine")
	if err != nil || out != "No tickets assigned to you.\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	if !strings.Contains(query, "assigneeIds=u1") {
		t.Errorf("query = %s", query)
	}
}

func TestGetByKey(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("GET /kazan/boards/"+idOps+"/tickets/"+idT42, http.StatusOK,
		`{"data":`+strings.TrimSuffix(ticketJSON(idT42, "OPS-42", "Login crash", idOps, "OPS"), "}")+`,"description":"line one\nline two"}}`)
	out, _, err := runCLI(t, "", "kazan", "ticket", "get", "ops-42")
	if err != nil {
		t.Fatal(err)
	}
	for _, part := range []string{"OPS-42", "Login crash", "line one\nline two", "Ada Lovelace"} {
		if !strings.Contains(out, part) {
			t.Errorf("out missing %q:\n%s", part, out)
		}
	}
}

func TestGetUnknownKeyExitsTwo(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("GET /kazan/tickets/by-key/OPS-99", http.StatusNotFound, `{"message":"not found"}`)
	_, _, err := runCLI(t, "", "kazan", "ticket", "get", "OPS-99")
	if err == nil || err.Error() != `ticket "OPS-99" not found` || client.ExitCode(err) != 2 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
}

func TestGetUnknownPrefixExitsTwo(t *testing.T) {
	newTicketAPI(t)
	_, _, err := runCLI(t, "", "kazan", "ticket", "get", "ZZZ-1")
	if err == nil || !strings.Contains(err.Error(), `no board with prefix "ZZZ"`) || client.ExitCode(err) != 2 {
		t.Fatalf("err = %v, exit = %d", err, client.ExitCode(err))
	}
}

func TestGetWarnsWhenBoardDiffers(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("GET /kazan/boards/"+idKagi+"/tickets/"+idK7, http.StatusOK,
		`{"data":`+ticketJSON(idK7, "KAGI-7", "Vault", idKagi, "KAGI")+`}`)
	_, errOut, err := runCLI(t, "", "kazan", "--board", "ops", "ticket", "get", "KAGI-7")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(errOut, "Warning: KAGI-7 is on board") {
		t.Errorf("stderr = %q", errOut)
	}
}

func TestCreateSendsTheFieldsToTheSprint(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("POST /kazan/boards/"+idOps+"/tickets", http.StatusCreated,
		`{"data":`+ticketJSON(idT43, "OPS-43", "Logout", idOps, "OPS")+`}`)
	out, errOut, err := runCLI(t, "", "kazan", "ticket", "create", "--title", "Logout", "--type", "task",
		"--priority", "high", "--status", "todo", "--mission-rank", "s", "--sprint", "sprint", "--story-points", "3")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Created ticket OPS-43: Logout\n" || !strings.Contains(errOut, "type TASK is now FEATURE") {
		t.Errorf("out %q stderr %q", out, errOut)
	}
	got := bodyOf(t, api, "POST /kazan/boards/"+idOps+"/tickets")
	if got["type"] != "FEATURE" || got["sprintId"] != idSprint3 || got["missionRank"] != "S" || got["weight"] != float64(3) {
		t.Errorf("body = %v", got)
	}
}

func TestCreateWithoutTitleMakesNoCall(t *testing.T) {
	api := newTicketAPI(t)
	_, _, err := runCLI(t, "", "kazan", "ticket", "create", "--type", "BUG")
	if err == nil || err.Error() != "--title is required" {
		t.Fatalf("err = %v", err)
	}
	if len(api.Calls()) != 0 {
		t.Errorf("calls = %v", api.Calls())
	}
}

func TestCreateWithAttachmentJSON(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("POST /kazan/boards/"+idOps+"/tickets", http.StatusCreated,
		`{"data":`+ticketJSON(idT43, "OPS-43", "Logout", idOps, "OPS")+`}`)
	api.Respond("POST /storage/attachments/exists", http.StatusOK, `{"data":{"exists":false}}`)
	api.Respond("POST /storage/attachments", http.StatusOK, `{"data":[{"id":"a1","fileName":"log.txt"}]}`)
	file := filepath.Join(t.TempDir(), "log.txt")
	if err := os.WriteFile(file, []byte("boom"), 0o600); err != nil {
		t.Fatal(err)
	}
	buf := withJSON(t)
	_, _, err := runCLI(t, "", "kazan", "ticket", "create", "--title", "Logout", "--type", "BUG",
		"--priority", "HIGH", "--status", "TODO", "--mission-rank", "S", "-a", file)
	if err != nil {
		t.Fatal(err)
	}
	var got struct {
		Ticket      map[string]any   `json:"ticket"`
		Attachments []map[string]any `json:"attachments"`
	}
	if err := json.Unmarshal([]byte(flush(t, buf)), &got); err != nil {
		t.Fatal(err)
	}
	if got.Ticket["key"] != "OPS-43" || len(got.Attachments) != 1 || got.Attachments[0]["fileName"] != "log.txt" {
		t.Errorf("result = %s", buf.String())
	}
}

func TestCreateUploadFailureSaysTheTicketExists(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("POST /kazan/boards/"+idOps+"/tickets", http.StatusCreated,
		`{"data":`+ticketJSON(idT43, "OPS-43", "Logout", idOps, "OPS")+`}`)
	api.Respond("POST /storage/attachments/exists", http.StatusOK, `{"data":{"exists":false}}`)
	api.Respond("POST /storage/attachments", http.StatusInternalServerError, `{"message":"disk full"}`)
	file := filepath.Join(t.TempDir(), "log.txt")
	if err := os.WriteFile(file, []byte("boom"), 0o600); err != nil {
		t.Fatal(err)
	}
	out, errOut, err := runCLI(t, "", "kazan", "ticket", "create", "--title", "Logout", "--type", "BUG",
		"--priority", "HIGH", "--status", "TODO", "--mission-rank", "S", "--attach", file)
	if err == nil || !strings.Contains(err.Error(), "ticket OPS-43 created, but uploading its attachments failed") {
		t.Fatalf("err = %v", err)
	}
	if !strings.Contains(out, "Created ticket OPS-43") || !strings.Contains(errOut, "WAS created") {
		t.Errorf("out %q stderr %q", out, errOut)
	}
}

func TestUpdateSendsOnlyGivenFields(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("PUT /kazan/boards/"+idOps+"/tickets/"+idT42, http.StatusOK,
		`{"data":`+ticketJSON(idT42, "OPS-42", "Crash", idOps, "OPS")+`}`)
	out, _, err := runCLI(t, "", "kazan", "ticket", "update", "OPS-42", "--title", "Crash", "--milestone", "", "--parent", "")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Updated ticket OPS-42: Crash\n" {
		t.Errorf("out = %q", out)
	}
	got := bodyOf(t, api, "PUT /kazan/boards/"+idOps+"/tickets/"+idT42)
	if len(got) != 3 || got["title"] != "Crash" || got["clearMilestone"] != true || got["clearParent"] != true {
		t.Errorf("body = %v", got)
	}
}

const (
	idAgent = "0e000000-0000-4000-8000-000000000001"
	// agentReviewerRefused is the API's refusal of an agent named as a ticket reviewer.
	agentReviewerRefused = `{"error":{"code":"AGT_VAL_001","message":"This action does not apply to an agent.",` +
		`"errorValues":{"action":"TICKET_REVIEWER","userId":"` + idAgent + `"}}}`
	// agentAssignee is an assignee of kind AGENT.
	agentAssignee = `{"id":"m9","userId":"` + idAgent + `","email":"agent-1@agents.oba.invalid","firstName":"Scout",` +
		`"lastName":"","kind":"AGENT","agentRole":"CYBERSECURITY_ENGINEER","agentFlow":"ROUTINE","agentProvider":"ANTHROPIC"}`
)

// withAgentAssignee is ticketJSON with the agent as its only assignee.
func withAgentAssignee(id, key, title string) string {
	return strings.Replace(ticketJSON(id, key, title, idOps, "OPS"),
		`"assignees":[{"id":"m1","userId":"u1","email":"a@b.c","firstName":"Ada","lastName":"Lovelace"}]`,
		`"assignees":[`+agentAssignee+`]`, 1)
}

func TestUpdateWithAgentReviewerSaysWhatToDo(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("PUT /kazan/boards/"+idOps+"/tickets/"+idT42, http.StatusUnprocessableEntity, agentReviewerRefused)
	_, _, err := runCLI(t, "", "kazan", "ticket", "update", "OPS-42", "--reviewer", idAgent)
	if err == nil {
		t.Fatal("update with an agent reviewer succeeded")
	}
	for _, part := range []string{"failed to update ticket", "user " + idAgent + " is an agent",
		"Name a person with --reviewer", "AGT_VAL_001"} {
		if !strings.Contains(err.Error(), part) {
			t.Errorf("err lacks %q: %v", part, err)
		}
	}
	if code := client.ExitCode(err); code != 1 {
		t.Errorf("exit code = %d", code)
	}
}

func TestCreateWithAgentReviewerSaysWhatToDo(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("POST /kazan/boards/"+idOps+"/tickets", http.StatusUnprocessableEntity, agentReviewerRefused)
	_, _, err := runCLI(t, "", "kazan", "ticket", "create", "--title", "Scan", "--type", "task",
		"--priority", "high", "--status", "todo", "--mission-rank", "s", "--sprint", "sprint", "--reviewer", idAgent)
	if err == nil || !strings.Contains(err.Error(), "failed to create ticket: user "+idAgent+" is an agent") {
		t.Fatalf("err = %v", err)
	}
}

func TestGetMarksAnAgentAssignee(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("GET /kazan/boards/"+idOps+"/tickets/"+idT42, http.StatusOK,
		`{"data":`+withAgentAssignee(idT42, "OPS-42", "Login crash")+`}`)
	out, _, err := runCLI(t, "", "kazan", "ticket", "get", "OPS-42")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Assignees: Scout (agent)") {
		t.Errorf("out =\n%s", out)
	}
}

// --json passes the assignee's kind and agent fields on as the API sent them.
func TestListMarksAnAgentAssigneeAndKeepsItsKindInJSON(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("GET /kazan/boards/"+idOps+"/tickets", http.StatusOK,
		`{"data":[`+withAgentAssignee(idT42, "OPS-42", "Login crash")+`]}`)
	out, _, err := runCLI(t, "", "kazan", "ticket", "list")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Scout (agent)") {
		t.Errorf("out =\n%s", out)
	}

	buf := withJSON(t)
	if _, _, err := runCLI(t, "", "kazan", "ticket", "list"); err != nil {
		t.Fatal(err)
	}
	got := flush(t, buf)
	for _, want := range []string{`"kind": "AGENT"`, `"agentRole": "CYBERSECURITY_ENGINEER"`,
		`"agentFlow": "ROUTINE"`, `"agentProvider": "ANTHROPIC"`} {
		if !strings.Contains(got, want) {
			t.Errorf("json lacks %s:\n%s", want, got)
		}
	}
}

const (
	idScout = "0e000000-0000-4000-8000-000000000002"
	// opsBoardMembers is a person and two agents, one of whose names differs from the other only in case.
	opsBoardMembers = `{"data":[` +
		`{"id":"bm1","userId":"u-ada","email":"ada@example.com","firstName":"Ada","lastName":"Lovelace","role":"MEMBER","kind":"HUMAN"},` +
		`{"id":"bm2","userId":"` + idScout + `","email":"agent-2@agents.oba.invalid","firstName":"Scout","lastName":"","role":"MEMBER","kind":"AGENT"},` +
		`{"id":"bm3","userId":"` + idAgent + `","email":"agent-1@agents.oba.invalid","firstName":"Ranger","lastName":"","role":"MEMBER","kind":"AGENT"}]}`
)

func TestUpdateAssignsAnAgentByNameNextToAPerson(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("GET /organizations/"+clitest.OrgIDAcme+"/members", http.StatusOK,
		`{"data":[{"id":"m1","userId":"u-ada","email":"ada@example.com","role":"MEMBER","status":"ACTIVE"}]}`)
	api.Respond("GET /kazan/boards/"+idOps+"/members", http.StatusOK, opsBoardMembers)
	api.Respond("PUT /kazan/boards/"+idOps+"/tickets/"+idT42, http.StatusOK, `{"data":`+withAgentAssignee(idT42, "OPS-42", "Crash")+`}`)
	_, _, err := runCLI(t, "", "kazan", "ticket", "update", "OPS-42", "--assignee", "scout,ADA@example.com")
	if err != nil {
		t.Fatal(err)
	}
	got := bodyOf(t, api, "PUT /kazan/boards/"+idOps+"/tickets/"+idT42)
	ids, _ := got["assigneeIds"].([]any)
	if len(ids) != 2 || ids[0] != idScout || ids[1] != "u-ada" {
		t.Errorf("assigneeIds = %v", got["assigneeIds"])
	}
}

func TestUpdateAssigneeByEmailReadsNoBoardMembers(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("GET /organizations/"+clitest.OrgIDAcme+"/members", http.StatusOK,
		`{"data":[{"id":"m1","userId":"u-ada","email":"ada@example.com","role":"MEMBER","status":"ACTIVE"}]}`)
	api.Respond("PUT /kazan/boards/"+idOps+"/tickets/"+idT42, http.StatusOK, `{"data":`+ticketJSON(idT42, "OPS-42", "Crash", idOps, "OPS")+`}`)
	if _, _, err := runCLI(t, "", "kazan", "ticket", "update", "OPS-42", "--assignee", "ada@example.com,"+idAgent); err != nil {
		t.Fatal(err)
	}
	for _, call := range api.Calls() {
		if strings.Contains(call, "/kazan/boards/"+idOps+"/members") {
			t.Errorf("an email or id read the board members: %v", api.Calls())
		}
	}
	ids, _ := bodyOf(t, api, "PUT /kazan/boards/"+idOps+"/tickets/"+idT42)["assigneeIds"].([]any)
	if len(ids) != 2 || ids[0] != "u-ada" || ids[1] != idAgent {
		t.Errorf("assigneeIds = %v", ids)
	}
}

func TestUpdateUnknownAgentNameListsTheAgentsAndSendsNothing(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("GET /kazan/boards/"+idOps+"/members", http.StatusOK, opsBoardMembers)
	_, _, err := runCLI(t, "", "kazan", "ticket", "update", "OPS-42", "--assignee", "Ada Lovelace")
	if err == nil {
		t.Fatal("update with an unknown agent name succeeded")
	}
	want := `--assignee: no agent named "Ada Lovelace"; agents on the board: Scout (` + idScout + `), Ranger (` + idAgent + `)`
	if !strings.Contains(err.Error(), want) {
		t.Errorf("err = %v", err)
	}
	if code := client.ExitCode(err); code != 2 {
		t.Errorf("exit code = %d", code)
	}
	for _, call := range api.Calls() {
		if strings.HasPrefix(call, "PUT ") {
			t.Errorf("the ticket was updated: %v", api.Calls())
		}
	}
}

func TestCreateWithASharedAgentNameListsTheMatches(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("GET /kazan/boards/"+idOps+"/members", http.StatusOK, strings.Replace(opsBoardMembers, `"Ranger"`, `"SCOUT"`, 1))
	_, _, err := runCLI(t, "", "kazan", "ticket", "create", "--title", "Scan", "--type", "bug",
		"--priority", "high", "--status", "todo", "--mission-rank", "s", "--sprint", "sprint", "--assignee", "Scout")
	if err == nil {
		t.Fatal("create with a shared agent name succeeded")
	}
	want := `--assignee: 2 agents on the board are named "Scout": Scout (` + idScout + `), SCOUT (` + idAgent + `); name one by its user id`
	if !strings.Contains(err.Error(), want) {
		t.Errorf("err = %v", err)
	}
	for _, call := range api.Calls() {
		if strings.HasPrefix(call, "POST ") {
			t.Errorf("the ticket was created: %v", api.Calls())
		}
	}
}

func TestCreateAssignsAnAgentByName(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("GET /kazan/boards/"+idOps+"/members", http.StatusOK, opsBoardMembers)
	api.Respond("POST /kazan/boards/"+idOps+"/tickets", http.StatusCreated, `{"data":`+withAgentAssignee(idT43, "OPS-43", "Scan")+`}`)
	if _, _, err := runCLI(t, "", "kazan", "ticket", "create", "--title", "Scan", "--type", "bug",
		"--priority", "high", "--status", "todo", "--mission-rank", "s", "--sprint", "sprint", "--assignee", "ranger"); err != nil {
		t.Fatal(err)
	}
	ids, _ := bodyOf(t, api, "POST /kazan/boards/"+idOps+"/tickets")["assigneeIds"].([]any)
	if len(ids) != 1 || ids[0] != idAgent {
		t.Errorf("assigneeIds = %v", ids)
	}
}

func TestUpdateRefusesParentOfAnotherBoard(t *testing.T) {
	api := newTicketAPI(t)
	_, _, err := runCLI(t, "", "kazan", "ticket", "update", "OPS-42", "--parent", "KAGI-7")
	if err == nil || !strings.Contains(err.Error(), "on another board") {
		t.Fatalf("err = %v", err)
	}
	for _, call := range api.Calls() {
		if strings.HasPrefix(call, "PUT") {
			t.Errorf("calls = %v", api.Calls())
		}
	}
}

func TestUpdateWithoutChangesMakesNoCall(t *testing.T) {
	api := newTicketAPI(t)
	_, _, err := runCLI(t, "", "kazan", "ticket", "update", "OPS-42")
	if err != terminal.ErrNothingToUpdate {
		t.Fatalf("err = %v", err)
	}
	_, _, err = runCLI(t, "", "kazan", "ticket", "update", "OPS-42", "--sprint", "x")
	if err == nil || !strings.Contains(err.Error(), "ticket update cannot change the sprint or backlog") {
		t.Fatalf("err = %v", err)
	}
	if len(api.Calls()) != 0 {
		t.Errorf("calls = %v", api.Calls())
	}
}

func TestDeleteWithoutTerminalDeletes(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("DELETE /kazan/boards/"+idOps+"/tickets/"+idT42, http.StatusOK, `{"data":null}`)
	out, _, err := runCLI(t, "", "kazan", "ticket", "delete", "OPS-42")
	if err != nil || out != "Deleted ticket OPS-42\n" {
		t.Fatalf("out %q err %v", out, err)
	}
}

func TestDeleteOnTerminalCanBeCancelled(t *testing.T) {
	api := newTicketAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	out, _, err := runCLI(t, "n\n", "kazan", "ticket", "delete", "OPS-42")
	if err != nil || !strings.HasSuffix(out, "Cancelled.\n") {
		t.Fatalf("out %q err %v", out, err)
	}
	for _, call := range api.Calls() {
		if strings.HasPrefix(call, "DELETE") {
			t.Errorf("calls = %v", api.Calls())
		}
	}
}

func TestRestoreTakesAnID(t *testing.T) {
	api := newTicketAPI(t)
	_, _, err := runCLI(t, "", "kazan", "ticket", "restore", "OPS-42")
	if err == nil || !strings.Contains(err.Error(), "a deleted ticket cannot be found by its key") {
		t.Fatalf("err = %v", err)
	}
	api.Respond("POST /kazan/boards/"+idKagi+"/tickets/"+idK7+"/restore", http.StatusOK, `{"data":null}`)
	out, _, err := runCLI(t, "", "kazan", "--board", "KAGI", "ticket", "restore", idK7)
	if err != nil || out != "Restored ticket "+idK7+"\n" {
		t.Fatalf("out %q err %v", out, err)
	}
}

func TestOrder(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("PUT /kazan/boards/"+idOps+"/tickets/"+idT42+"/order", http.StatusOK, `{"data":null}`)
	out, _, err := runCLI(t, "", "kazan", "ticket", "order", "OPS-42", "--order", "2")
	if err != nil || out != "Reordered ticket "+idT42+" to position 2\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	if got := bodyOf(t, api, "PUT /kazan/boards/"+idOps+"/tickets/"+idT42+"/order"); got["order"] != float64(2) {
		t.Errorf("body = %v", got)
	}
}

func TestMoveToBacklog(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("POST /kazan/boards/"+idOps+"/tickets/"+idT42+"/move", http.StatusOK, `{"data":null}`)
	out, _, err := runCLI(t, "", "kazan", "ticket", "move", "OPS-42", "--backlog", "--sub-tickets", "none")
	if err != nil || out != "Moved ticket OPS-42 to Backlog\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	got := bodyOf(t, api, "POST /kazan/boards/"+idOps+"/tickets/"+idT42+"/move")
	if got["backlogId"] != idBacklog || got["subTicketMode"] != "NONE" {
		t.Errorf("body = %v", got)
	}
}

func TestMoveToAnotherBoardIsABulkMove(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("POST /kazan/boards/"+idKagi+"/tickets/bulk-move", http.StatusOK,
		`{"data":{"movedCount":1,"milestoneClearedTicketKeys":["OPS-42"]}}`)
	out, _, err := runCLI(t, "", "kazan", "ticket", "move", "OPS-42", "--sprint", "Sprint 3", "--target-board", "kagi")
	if err != nil {
		t.Fatal(err)
	}
	want := "Moved ticket OPS-42 to Sprint: Sprint 3 (PLANNED) on board kagi\n" +
		"Milestone cleared (the target board is not related to the milestone's board): OPS-42\n"
	if out != want {
		t.Errorf("out = %q", out)
	}
	got := bodyOf(t, api, "POST /kazan/boards/"+idKagi+"/tickets/bulk-move")
	if got["action"] != "MOVE_TO_SPRINT" || got["targetSprintId"] != idSprintK || got["targetBoardId"] != idKagi ||
		got["subTicketMode"] != "UNFINISHED" {
		t.Errorf("body = %v", got)
	}
}

func TestMoveDestinationChecksMakeNoCall(t *testing.T) {
	api := newTicketAPI(t)
	cases := [][]string{
		{"OPS-42"},
		{"OPS-42", "--backlog", "--sprint", "x"},
		{"OPS-42", "--backlog", "--target-sprint", "x"},
		{"OPS-42", "--backlog", "--target-board", "ops", "--to-board", "kagi"},
		{"OPS-42", "--backlog", "--sub-tickets", "maybe"},
	}
	for _, args := range cases {
		if _, _, err := runCLI(t, "", append([]string{"kazan", "ticket", "move"}, args...)...); err == nil {
			t.Errorf("%v: no error", args)
		}
	}
	if len(api.Calls()) != 0 {
		t.Errorf("calls = %v", api.Calls())
	}
}

func TestBulkMoveOnOneBoard(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("POST /kazan/boards/"+idOps+"/tickets/bulk-move", http.StatusOK, `{"data":{"movedCount":2}}`)
	out, _, err := runCLI(t, "", "kazan", "ticket", "bulk-move", "--tickets", "OPS-42,OPS-43", "--backlog")
	if err != nil || out != "Moved 2 tickets to Backlog\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	got := bodyOf(t, api, "POST /kazan/boards/"+idOps+"/tickets/bulk-move")
	if got["action"] != "MOVE_TO_BACKLOG" || got["targetBoardId"] != nil || len(got["ticketIds"].([]any)) != 2 {
		t.Errorf("body = %v", got)
	}
}

func TestBulkMoveAcrossBoardsNeedsATarget(t *testing.T) {
	newTicketAPI(t)
	_, _, err := runCLI(t, "", "kazan", "ticket", "bulk-move", "--tickets", "OPS-42,KAGI-7", "--backlog")
	if err == nil || !strings.Contains(err.Error(), "2 different boards (OPS-42 | KAGI-7)") {
		t.Fatalf("err = %v", err)
	}
}

func TestBulkDeleteNeedsYesWithoutTerminal(t *testing.T) {
	api := newTicketAPI(t)
	_, _, err := runCLI(t, "", "kazan", "ticket", "bulk-delete", "--tickets", "OPS-42,OPS-43")
	if err == nil || err.Error() != "ticket bulk-delete deletes 2 tickets: pass --yes to confirm in non-interactive mode" {
		t.Fatalf("err = %v", err)
	}
	if len(api.Calls()) != 0 {
		t.Errorf("calls = %v", api.Calls())
	}
}

func TestBulkDeletePartialFailureJSON(t *testing.T) {
	api := newTicketAPI(t)
	api.Respond("DELETE /kazan/boards/"+idOps+"/tickets/"+idT42, http.StatusOK, `{"data":null}`)
	api.Respond("DELETE /kazan/boards/"+idOps+"/tickets/"+idT43, http.StatusForbidden, `{"message":"no access"}`)
	buf := withJSON(t)
	out, errOut, err := runCLI(t, "", "kazan", "ticket", "bulk-delete", "--tickets", "OPS-42,OPS-43", "--yes")
	if err == nil || err.Error() != "1 of 2 tickets could not be deleted" {
		t.Fatalf("err = %v", err)
	}
	if !strings.Contains(out, "Deleted ticket OPS-42") || !strings.Contains(errOut, "Failed to delete ticket OPS-43") {
		t.Errorf("out %q stderr %q", out, errOut)
	}
	var got []map[string]any
	if err := json.Unmarshal([]byte(flush(t, buf)), &got); err != nil {
		t.Fatal(err)
	}
	if len(got) != 2 || got[0]["ok"] != true || got[1]["ok"] != false || got[1]["error"] == "" {
		t.Errorf("result = %s", buf.String())
	}
}
