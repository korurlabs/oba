package backlog

import (
	"bytes"
	"errors"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/board"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
)

const ticketID = "0c000000-0000-4000-8000-000000000001"

func TestBacklogGetPrintsTheTickets(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET /kazan/boards/"+idOps+"/backlog", http.StatusOK,
		`{"data":{"id":"b1","boardId":"`+idOps+`","tickets":[{"key":"OPS-1","title":"Fix\nlogin","status":"TODO","priority":"HIGH","type":"BUG"}]}}`)
	out, _, err := runCLI(t, "", "kazan", "backlog", "get")
	if err != nil {
		t.Fatal(err)
	}
	if out != "KEY    TITLE      STATUS  PRIORITY  TYPE\nOPS-1  Fix login  TODO    HIGH      BUG\n" {
		t.Errorf("out =\n%q", out)
	}
	if api.Request("GET /kazan/boards/"+idOps+"/backlog").Org != clitest.OrgIDAcme {
		t.Error("organization header missing")
	}
}

func TestBacklogGetEmpty(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET /kazan/boards/"+idOps+"/backlog", http.StatusOK, `{"data":{"id":"b1","tickets":[]}}`)
	out, _, err := runCLI(t, "", "kazan", "backlog", "get")
	if err != nil || out != "Backlog is empty.\n" {
		t.Errorf("out %q err %v", out, err)
	}
}

// --board by prefix resolves through the board tree, which stays out of the --json result.
func TestBacklogGetBoardFlagAndJSON(t *testing.T) {
	api := newAPI(t)
	if err := config.Save(config.File{}); err != nil {
		t.Fatal(err)
	}
	api.Respond("GET /kazan/boards/"+idOps+"/backlog", http.StatusOK, `{"data":{"id":"b1","extra":1,"tickets":[]}}`)
	var buf bytes.Buffer
	jsonout.Start(&buf)
	t.Cleanup(jsonout.Stop)
	if _, _, err := runCLI(t, "", "kazan", "--board", "ops", "backlog", "get"); err != nil {
		t.Fatal(err)
	}
	if err := jsonout.Flush(); err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(buf.String(), `"extra": 1`) || strings.Contains(buf.String(), "Operations") {
		t.Errorf("json = %s", buf.String())
	}
}

func TestBacklogWithoutBoard(t *testing.T) {
	newAPI(t)
	if err := config.Save(config.File{}); err != nil {
		t.Fatal(err)
	}
	if _, _, err := runCLI(t, "", "kazan", "backlog", "get"); !errors.Is(err, board.ErrNoBoard) {
		t.Errorf("err = %v", err)
	}
}

func TestBacklogAddTicket(t *testing.T) {
	api := newAPI(t)
	route := "POST /kazan/boards/" + idOps + "/backlog/tickets/" + ticketID
	api.Respond(route, http.StatusOK, `{"data":null}`)
	out, _, err := runCLI(t, "", "kazan", "backlog", "add-ticket", ticketID)
	if err != nil || out != "Ticket "+ticketID+" added to backlog.\n" {
		t.Errorf("out %q err %v", out, err)
	}
	api.Request(route)
}

func TestBacklogAddTicketNotFoundExitsTwo(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST /kazan/boards/"+idOps+"/backlog/tickets/"+ticketID, http.StatusNotFound, `{"message":"Ticket not found"}`)
	_, _, err := runCLI(t, "", "kazan", "backlog", "add-ticket", ticketID)
	if client.ExitCode(err) != 2 {
		t.Errorf("err %v exit %d", err, client.ExitCode(err))
	}
}

func TestBacklogNeedsASignIn(t *testing.T) {
	clitest.SetupHome(t)
	_, _, err := runCLI(t, "", "kazan", "backlog", "get")
	if err == nil || err.Error() != "You are not logged in. Run 'oba login'." {
		t.Errorf("err = %v", err)
	}
}
