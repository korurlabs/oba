package cmd

import (
	"encoding/json"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

const (
	kazanIDOps   = "0a000000-0000-4000-8000-000000000001"
	kazanIDInfra = "0a000000-0000-4000-8000-000000000002"

	kazanTreeJSON = `{"data":[` +
		`{"id":"` + kazanIDOps + `","name":"Operations","description":"","prefix":"OPS","slug":"operations","extra":1.10,"childBoards":[` +
		`{"id":"` + kazanIDInfra + `","name":"Infra","description":"","prefix":"INF","slug":"infra","childBoards":[]}]},` +
		`{"id":"0a000000-0000-4000-8000-000000000003","name":"Kagi","description":"","prefix":"KAGI","slug":"kagi","childBoards":[]}` +
		`],"pagination":{"currentPage":1,"last":true}}`
	kazanInfraJSON = `{"data":{"id":"` + kazanIDInfra + `","name":"Infra","prefix":"INF","slug":"infra","extra":2.50}}`
)

func kazanAPI(t *testing.T) {
	t.Helper()
	api := signedIn(t)
	api.Respond("GET /kazan/boards", http.StatusOK, kazanTreeJSON)
	api.Respond("GET /kazan/boards/"+kazanIDInfra, http.StatusOK, kazanInfraJSON)
}

func TestJSONKazanBoardListIsTheRawTree(t *testing.T) {
	kazanAPI(t)
	out, _, err := run(t, "kazan", "board", "list", "--json")
	if err != nil {
		t.Fatal(err)
	}
	var items []map[string]json.RawMessage
	if err := json.Unmarshal([]byte(out), &items); err != nil {
		t.Fatalf("stdout is not a JSON array: %v\n%s", err, out)
	}
	if len(items) != 2 || string(items[0]["extra"]) != "1.10" || !strings.Contains(string(items[0]["childBoards"]), "Infra") {
		t.Errorf("out = %s", out)
	}
}

func TestJSONKazanBoardListSearchIsTheFilteredTree(t *testing.T) {
	kazanAPI(t)
	out, _, err := run(t, "kazan", "board", "list", "--search", "inf", "--json")
	if err != nil {
		t.Fatal(err)
	}
	var items []map[string]json.RawMessage
	if err := json.Unmarshal([]byte(out), &items); err != nil {
		t.Fatalf("stdout is not a JSON array: %v\n%s", err, out)
	}
	if len(items) != 1 || string(items[0]["prefix"]) != `"OPS"` || string(items[0]["extra"]) != "1.10" {
		t.Errorf("out = %s", out)
	}
}

func TestJSONKazanBoardGetAndUseAreTheBoard(t *testing.T) {
	for _, args := range [][]string{{"kazan", "board", "get", "inf"}, {"kazan", "board", "use", "INF"}} {
		t.Run(args[2], func(t *testing.T) {
			kazanAPI(t)
			out, _, err := run(t, append(args, "--json")...)
			if err != nil {
				t.Fatal(err)
			}
			var b map[string]json.RawMessage
			if err := json.Unmarshal([]byte(out), &b); err != nil {
				t.Fatalf("stdout is not a JSON object: %v\n%s", err, out)
			}
			if string(b["id"]) != `"`+kazanIDInfra+`"` || string(b["extra"]) != "2.50" {
				t.Errorf("out = %s", out)
			}
		})
	}
}

func TestKazanBoardFlagIsParsedBeforeTheSubcommand(t *testing.T) {
	kazanAPI(t)
	if _, _, err := run(t, "kazan", "--board", "OPS", "board", "list"); err != nil {
		t.Fatal(err)
	}
}

func TestWhoami(t *testing.T) {
	api := signedIn(t)
	api.Respond("GET /users/me", http.StatusOK, strings.Replace(clitest.UserMeJSON, `"email":"a@b.c",`,
		`"email":"a@b.c","profile":{"firstName":"Ada","lastName":"Lovelace"},`, 1))
	out, _, err := run(t, "whoami")
	if err != nil {
		t.Fatal(err)
	}
	for _, part := range []string{"Email: a@b.c\n", "Name:  Ada Lovelace\n", "ID:    u1\n", "Org:   Acme (", "  - Beta (", "role: MEMBER"} {
		if !strings.Contains(out, part) {
			t.Errorf("out missing %q:\n%s", part, out)
		}
	}
}

func TestJSONWhoamiIsTheUser(t *testing.T) {
	signedIn(t)
	out, _, err := run(t, "whoami", "--json")
	if err != nil {
		t.Fatal(err)
	}
	var u map[string]json.RawMessage
	if err := json.Unmarshal([]byte(out), &u); err != nil || string(u["email"]) != `"a@b.c"` {
		t.Errorf("out = %s (%v)", out, err)
	}
}

const (
	kazanIDT42     = "0b000000-0000-4000-8000-000000000042"
	kazanIDT43     = "0b000000-0000-4000-8000-000000000043"
	kazanIDBacklog = "0c000000-0000-4000-8000-000000000001"
)

func kazanTicketJSON(id, key string) string {
	return `{"id":"` + id + `","key":"` + key + `","title":"Logout","status":"TODO","priority":"HIGH","type":"BUG",` +
		`"board":{"id":"` + kazanIDOps + `","name":"Operations","prefix":"OPS"}}`
}

// The run fails, yet the result it kept lands on stdout as the only output there, so a script
// gets both the exit status and what was done.
func TestJSONKazanBulkDeletePartialFailurePrintsTheResults(t *testing.T) {
	api := signedIn(t)
	api.Respond("GET /kazan/boards", http.StatusOK, kazanTreeJSON)
	api.Respond("GET /kazan/tickets/by-key/OPS-42", http.StatusOK, `{"data":`+kazanTicketJSON(kazanIDT42, "OPS-42")+`}`)
	api.Respond("GET /kazan/tickets/by-key/OPS-43", http.StatusOK, `{"data":`+kazanTicketJSON(kazanIDT43, "OPS-43")+`}`)
	api.Respond("DELETE /kazan/boards/"+kazanIDOps+"/tickets/"+kazanIDT42, http.StatusOK, `{"data":null}`)
	api.Respond("DELETE /kazan/boards/"+kazanIDOps+"/tickets/"+kazanIDT43, http.StatusForbidden, `{"message":"no access"}`)

	out, errOut, err := run(t, "kazan", "ticket", "bulk-delete", "--tickets", "OPS-42,OPS-43", "--yes", "--json")
	if err == nil || err.Error() != "1 of 2 tickets could not be deleted" {
		t.Fatalf("err = %v", err)
	}
	var got []map[string]any
	if err := json.Unmarshal([]byte(out), &got); err != nil {
		t.Fatalf("stdout is not a JSON array: %v\n%s", err, out)
	}
	if len(got) != 2 || got[0]["key"] != "OPS-42" || got[0]["ok"] != true || got[1]["ok"] != false || got[1]["error"] == "" {
		t.Errorf("out = %s", out)
	}
	if !strings.Contains(errOut, "Deleted ticket OPS-42") || !strings.Contains(errOut, "Failed to delete ticket OPS-43") {
		t.Errorf("stderr = %q", errOut)
	}
}

func TestJSONKazanCreateUploadFailurePrintsTheTicket(t *testing.T) {
	api := signedIn(t)
	api.Respond("GET /kazan/boards", http.StatusOK, kazanTreeJSON)
	api.Respond("GET /kazan/boards/"+kazanIDOps+"/backlog", http.StatusOK, `{"data":{"id":"`+kazanIDBacklog+`"}}`)
	api.Respond("POST /kazan/boards/"+kazanIDOps+"/tickets", http.StatusCreated, `{"data":`+kazanTicketJSON(kazanIDT43, "OPS-43")+`}`)
	api.Respond("POST /storage/attachments/exists", http.StatusOK, `{"data":{"exists":false}}`)
	api.Respond("POST /storage/attachments", http.StatusInternalServerError, `{"message":"disk full"}`)
	file := filepath.Join(t.TempDir(), "log.txt")
	if err := os.WriteFile(file, []byte("boom"), 0o600); err != nil {
		t.Fatal(err)
	}

	out, errOut, err := run(t, "kazan", "--board", "OPS", "ticket", "create", "--title", "Logout", "--type", "BUG",
		"--priority", "HIGH", "--status", "TODO", "--mission-rank", "S", "--attach", file, "--json")
	if err == nil || !strings.Contains(err.Error(), "ticket OPS-43 created, but uploading its attachments failed") {
		t.Fatalf("err = %v", err)
	}
	var got struct {
		Ticket          map[string]any   `json:"ticket"`
		Attachments     []map[string]any `json:"attachments"`
		AttachmentError *struct {
			Message string   `json:"message"`
			Files   []string `json:"files"`
		} `json:"attachmentError"`
	}
	if err := json.Unmarshal([]byte(out), &got); err != nil {
		t.Fatalf("stdout is not a JSON object: %v\n%s", err, out)
	}
	if got.Ticket["key"] != "OPS-43" || got.Attachments == nil || len(got.Attachments) != 0 ||
		got.AttachmentError == nil || len(got.AttachmentError.Files) != 1 {
		t.Errorf("out = %s", out)
	}
	if !strings.Contains(errOut, "WAS created") {
		t.Errorf("stderr = %q", errOut)
	}
}

// A kept result belongs to its run only: the next failed run in the same process prints nothing.
func TestJSONKeptResultDoesNotLeakIntoTheNextFailedRun(t *testing.T) {
	TestJSONKazanBulkDeletePartialFailurePrintsTheResults(t)
	api := signedIn(t)
	api.Respond("GET /otak/events", http.StatusForbidden, `{"error":{"code":"ORG_SEC_003","message":"Insufficient role"}}`)
	out, _, err := run(t, "otak", "event", "list", "--json")
	if err == nil || out != "" {
		t.Errorf("err = %v, stdout = %q", err, out)
	}
}
