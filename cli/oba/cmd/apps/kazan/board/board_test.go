package board

import (
	"errors"
	"net/http"
	"net/url"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

func normalize(s string) string {
	lines := strings.Split(s, "\n")
	for i, l := range lines {
		lines[i] = strings.Join(strings.Fields(l), " ")
	}
	return strings.Join(lines, "\n")
}

func TestBoardListPrintsTheTree(t *testing.T) {
	api := newBoardAPI(t)
	out, _, err := runCLI(t, "", "kazan", "board", "list")
	if err != nil {
		t.Fatal(err)
	}
	want := "# NAME KEY DESCRIPTION\n1 Operations OPS Day to day work\n2 └─ Infra INF\n3 Kagi KAGI Secrets\n"
	if normalize(out) != want {
		t.Errorf("out =\n%s", out)
	}
	if api.Request("GET /kaizen/boards").Org != clitest.OrgIDAcme {
		t.Error("organization header missing")
	}
}

func TestBoardListSearch(t *testing.T) {
	newBoardAPI(t)
	out, _, err := runCLI(t, "", "kazan", "board", "list", "--search", "inf")
	if err != nil {
		t.Fatal(err)
	}
	if normalize(out) != "# NAME KEY DESCRIPTION\n1 Operations OPS Day to day work\n2 └─ Infra INF\n" {
		t.Errorf("out =\n%s", out)
	}
	out, _, err = runCLI(t, "", "kazan", "board", "list", "--search", "zzz")
	if err != nil || out != "No boards found.\n" {
		t.Errorf("out %q err %v", out, err)
	}
}

func TestBoardGetShowsParent(t *testing.T) {
	api := newBoardAPI(t)
	api.Respond("GET /kaizen/boards/"+idInfra, http.StatusOK, infraJSON)
	out, _, err := runCLI(t, "", "kazan", "board", "get", "inf")
	if err != nil {
		t.Fatal(err)
	}
	for _, part := range []string{"Name:        Infra\n", "ID:          " + idInfra, "Description: Servers\n", "Parent:      Operations (OPS)\n"} {
		if !strings.Contains(out, part) {
			t.Errorf("out missing %q:\n%s", part, out)
		}
	}
}

func TestBoardGetUnknownBoardMakesNoFurtherCall(t *testing.T) {
	api := newBoardAPI(t)
	_, _, err := runCLI(t, "", "kazan", "board", "get", "nope")
	if err == nil || !strings.Contains(err.Error(), `no board of yours matches "nope"`) {
		t.Fatalf("err = %v", err)
	}
	if strings.Join(api.Calls(), ",") != "GET /kaizen/boards" {
		t.Errorf("calls = %v", api.Calls())
	}
}

func TestBoardGetNotFoundExitsTwo(t *testing.T) {
	api := newBoardAPI(t)
	api.Respond("GET /kaizen/boards/"+idKagi, http.StatusNotFound, `{"error":{"code":"KAI_NOT_001","message":"Board not found"}}`)
	_, _, err := runCLI(t, "", "kazan", "board", "get", idKagi)
	if client.ExitCode(err) != 2 {
		t.Errorf("err = %v", err)
	}
}

func TestBoardCreateSendsMembersAndParent(t *testing.T) {
	api := newBoardAPI(t)
	api.Respond("GET /organizations/"+clitest.OrgIDAcme+"/members", http.StatusOK,
		`{"data":[{"id":"m1","userId":"u-a","email":"a@b.c","role":"ADMIN","status":"ACTIVE"}]}`)
	api.Respond("POST /kaizen/boards", http.StatusCreated, `{"data":{"id":"new","name":"Web","prefix":"WEB"}}`)
	out, _, err := runCLI(t, "", "kazan", "board", "create", "--name", "Web", "--key", "WEB", "--color", "",
		"--parent", "ops", "--member", "A@b.c:admin")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Board created: Web (new)\n" {
		t.Errorf("out = %q", out)
	}
	body := api.Request("POST /kaizen/boards").Body
	want := `{"name":"Web","prefix":"WEB","color":"","parentBoardId":"` + idOps + `","members":[{"userId":"u-a","role":"ADMIN"}]}`
	if body != want {
		t.Errorf("body = %s", body)
	}
}

func TestBoardCreateNeedsNameAndKeyBeforeAnyCall(t *testing.T) {
	api := newBoardAPI(t)
	_, _, err := runCLI(t, "", "kazan", "board", "create", "--name", "Web")
	if err == nil || err.Error() != "--name and --key are required" {
		t.Fatalf("err = %v", err)
	}
	if len(api.Calls()) != 0 {
		t.Errorf("calls = %v", api.Calls())
	}
}

func TestBoardUpdateSendsOnlyGivenFields(t *testing.T) {
	api := newBoardAPI(t)
	api.Respond("PUT /kaizen/boards/"+idKagi, http.StatusOK, `{"data":{"id":"`+idKagi+`","name":"Kagi 2"}}`)
	out, _, err := runCLI(t, "", "kazan", "board", "update", "KAGI", "--name", "Kagi 2", "--description", "")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Board KAGI updated.\n" || api.Request("PUT /kaizen/boards/"+idKagi).Body != `{"name":"Kagi 2","description":""}` {
		t.Errorf("out %q body %s", out, api.Request("PUT /kaizen/boards/"+idKagi).Body)
	}
	if _, _, err := runCLI(t, "", "kazan", "board", "update", "KAGI"); !errors.Is(err, terminal.ErrNothingToUpdate) {
		t.Errorf("err = %v", err)
	}
}

func TestBoardDeleteMovesTicketsToASprint(t *testing.T) {
	api := newBoardAPI(t)
	api.Respond("GET /kaizen/boards/"+idKagi+"/sprints", http.StatusOK,
		`{"data":[{"id":"s1","name":"Sprint 1","status":"ACTIVE","createdAt":"2026-11-01T09:00:00Z"}]}`)
	var query url.Values
	api.Handle("DELETE /kaizen/boards/"+idOps, func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.Query()
		clitest.WriteJSON(w, `{"message":"deleted"}`)
	})
	out, _, err := runCLI(t, "", "kazan", "board", "delete", "OPS", "--tickets-to", "sprint", "--target-board", "kagi",
		"--target-sprint", "sprint", "--children", "promote")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Board OPS deleted.\n" {
		t.Errorf("out = %q", out)
	}
	want := url.Values{"ticketAction": {"MOVE_TO_SPRINT"}, "targetBoardId": {idKagi}, "targetSprintId": {"s1"}, "childBoardAction": {"PROMOTE"}}
	if query.Encode() != want.Encode() {
		t.Errorf("query = %v", query)
	}
	if n := strings.Count(strings.Join(api.Calls(), ","), "GET /kaizen/boards,"); n != 1 {
		t.Errorf("the tree must be read once: %v", api.Calls())
	}
}

func TestBoardDeleteRefusesSameTargetBoard(t *testing.T) {
	newBoardAPI(t)
	_, _, err := runCLI(t, "", "kazan", "board", "delete", "OPS", "--tickets-to", "backlog", "--target-board", "operations")
	if err == nil || !strings.Contains(err.Error(), "another board") {
		t.Errorf("err = %v", err)
	}
}

func TestBoardRestoreByID(t *testing.T) {
	api := newBoardAPI(t)
	api.Respond("POST /kaizen/boards/"+idKagi+"/restore", http.StatusOK, `{"message":"restored"}`)
	out, _, err := runCLI(t, "", "kazan", "board", "restore", idKagi)
	if err != nil || out != "Board "+idKagi+" restored.\n" {
		t.Errorf("out %q err %v", out, err)
	}
	if strings.Join(api.Calls(), ",") != "POST /kaizen/boards/"+idKagi+"/restore" {
		t.Errorf("calls = %v", api.Calls())
	}
}

func TestBoardChildrenAdd(t *testing.T) {
	api := newBoardAPI(t)
	api.Respond("PUT /kaizen/boards/"+idOps+"/children", http.StatusOK, `{"data":[]}`)
	out, _, err := runCLI(t, "", "kazan", "board", "children", "add", "OPS", "--child-ids", "kagi, INF,")
	if err != nil || out != "Added 2 child board(s) to OPS.\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	if body := api.Request("PUT /kaizen/boards/" + idOps + "/children").Body; body != `{"childBoardIds":["`+idKagi+`","`+idInfra+`"]}` {
		t.Errorf("body = %s", body)
	}
	if _, _, err := runCLI(t, "", "kazan", "board", "children", "add", "OPS"); err == nil || err.Error() != "--child-ids is required" {
		t.Errorf("err = %v", err)
	}
}

func TestBoardRelated(t *testing.T) {
	api := newBoardAPI(t)
	api.Respond("GET /kaizen/boards/"+idInfra+"/related", http.StatusOK,
		`{"data":[{"id":"`+idOps+`","name":"Operations","description":"","prefix":"OPS"}]}`)
	out, _, err := runCLI(t, "", "kazan", "board", "related", "INF")
	if err != nil || normalize(out) != "# NAME KEY DESCRIPTION\n1 Operations OPS\n" {
		t.Errorf("out %q err %v", out, err)
	}
	api.Respond("GET /kaizen/boards/"+idInfra+"/related", http.StatusOK, `{"data":[]}`)
	out, _, err = runCLI(t, "", "kazan", "board", "related", "INF")
	if err != nil || out != "No related boards found.\n" {
		t.Errorf("out %q err %v", out, err)
	}
}

func TestBoardUseStoresTheBoardOfTheOrganization(t *testing.T) {
	api := newBoardAPI(t)
	api.Respond("GET /kaizen/boards/"+idInfra, http.StatusOK, infraJSON)
	out, _, err := runCLI(t, "", "kazan", "board", "use", "Infra")
	if err != nil || out != "Default board: Infra (INF)\n" {
		t.Fatalf("out %q err %v", out, err)
	}
	f, err := config.Load()
	if err != nil {
		t.Fatal(err)
	}
	if f.KazanBoards[clitest.OrgIDAcme] != idInfra || len(f.KazanBoards) != 1 {
		t.Errorf("stored = %v", f.KazanBoards)
	}
}

func TestBoardUseWithoutBoardAndTerminalFails(t *testing.T) {
	api := newBoardAPI(t)
	_, _, err := runCLI(t, "", "kazan", "board", "use")
	if err == nil || !strings.Contains(err.Error(), "oba kazan board use <board>") {
		t.Errorf("err = %v", err)
	}
	if len(api.Calls()) != 0 {
		t.Errorf("calls = %v", api.Calls())
	}
}

func TestBoardUsePicksByNumberOnATerminal(t *testing.T) {
	api := newBoardAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	api.Respond("GET /kaizen/boards/"+idInfra, http.StatusOK, infraJSON)
	out, _, err := clitest.RunCLILines(t, newRoot(), "9\n2\n", "kazan", "board", "use")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Please give a number from 1 to 3.") || !strings.HasSuffix(out, "Default board: Infra (INF)\n") {
		t.Errorf("out =\n%s", out)
	}
}

func TestBoardCommandsNeedASignIn(t *testing.T) {
	clitest.SetupHome(t)
	_, _, err := runCLI(t, "", "kazan", "board", "list")
	if err == nil || err.Error() != "You are not logged in. Run 'oba login'." {
		t.Errorf("err = %v", err)
	}
}
