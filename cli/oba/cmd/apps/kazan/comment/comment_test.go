package comment

import (
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

func run(t *testing.T, stdin string, args ...string) (string, string, error) {
	t.Helper()
	return clitest.RunCLILines(t, newRoot(), stdin, append([]string{"kazan", "comment"}, args...)...)
}

func TestCommentListPrintsTheTable(t *testing.T) {
	api := newAPI(t)
	out, _, err := run(t, "", "list", "ops-12")
	if err != nil {
		t.Fatal(err)
	}
	for _, part := range []string{"ID", "AUTHOR", "CONTENT", idComment, "Ada Lovelace", "First line", "b@x.io"} {
		if !strings.Contains(out, part) {
			t.Errorf("out missing %q:\n%s", part, out)
		}
	}
	want := "GET /kazan/boards,GET /kazan/tickets/by-key/OPS-12,GET " + commentsPath
	if strings.Join(api.Calls(), ",") != want {
		t.Errorf("calls = %v", api.Calls())
	}
}

func TestCommentListJSONIsTheComments(t *testing.T) {
	newAPI(t)
	flush := captureJSON(t)
	if _, _, err := run(t, "", "list", "OPS-12"); err != nil {
		t.Fatal(err)
	}
	got := flush()
	if !strings.Contains(got, `"authorFirstName": "Ada"`) || strings.Contains(got, `"key": "OPS-12"`) {
		t.Errorf("json =\n%s", got)
	}
}

func TestCommentListNotFoundExitsTwo(t *testing.T) {
	api := newAPI(t)
	api.Respond("GET "+commentsPath, http.StatusNotFound, `{"error":{"code":"KAI_NF","message":"Ticket not found"}}`)
	_, _, err := run(t, "", "list", "OPS-12")
	if client.ExitCode(err) != 2 {
		t.Fatalf("err = %v", err)
	}
}

func TestCommentAddSendsTheContent(t *testing.T) {
	api := newAPI(t)
	api.Respond("POST "+commentsPath, http.StatusCreated, `{"data":{"id":"new1","content":"Done"}}`)
	out, _, err := run(t, "", "add", "OPS-12", "--content", "Done")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Added comment new1\n" || api.Request("POST "+commentsPath).Body != `{"content":"Done"}` {
		t.Errorf("out %q body %s", out, api.Request("POST "+commentsPath).Body)
	}
}

func TestCommentAddAsksForTheContentOnATerminal(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	api.Respond("POST "+commentsPath, http.StatusCreated, `{"data":{"id":"new1"}}`)
	out, _, err := run(t, "\nTyped text\n", "add", "OPS-12")
	if err != nil {
		t.Fatal(err)
	}
	if strings.Count(out, "Comment: ") != 2 || api.Request("POST "+commentsPath).Body != `{"content":"Typed text"}` {
		t.Errorf("out %q body %s", out, api.Request("POST "+commentsPath).Body)
	}
}

func TestCommentAddWithoutContentOrTerminalFails(t *testing.T) {
	api := newAPI(t)
	_, _, err := run(t, "", "add", "OPS-12")
	if err == nil || err.Error() != "--content is required" {
		t.Fatalf("err = %v", err)
	}
	for _, call := range api.Calls() {
		if strings.HasPrefix(call, "POST") {
			t.Errorf("unexpected %s", call)
		}
	}
}

func TestCommentUpdateByID(t *testing.T) {
	api := newAPI(t)
	route := "PUT " + commentsPath + "/" + idComment
	api.Respond(route, http.StatusOK, `{"data":{"id":"`+idComment+`","content":"New"}}`)
	out, _, err := run(t, "", "update", "OPS-12", "--comment-id", idComment, "--content", "New")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Updated comment "+idComment+"\n" || api.Request(route).Body != `{"content":"New"}` {
		t.Errorf("out %q body %s", out, api.Request(route).Body)
	}
}

func TestCommentUpdateWithoutIDOrTerminalFails(t *testing.T) {
	newAPI(t)
	_, _, err := run(t, "", "update", "OPS-12", "--content", "x")
	if err == nil || !strings.Contains(err.Error(), "--comment-id is required without a terminal") {
		t.Fatalf("err = %v", err)
	}
}

func TestCommentUpdatePicksTheCommentOnATerminal(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	api.Respond("PUT "+commentsPath+"/c2", http.StatusOK, `{"data":{"id":"c2"}}`)
	flush := captureJSON(t)
	out, _, err := run(t, "7\n2\nChanged\n", "update", "OPS-12")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Invalid selection. Enter a number between 1 and 2.") || !strings.HasSuffix(out, "Updated comment c2\n") {
		t.Errorf("out =\n%s", out)
	}
	if api.Request("PUT "+commentsPath+"/c2").Body != `{"content":"Changed"}` {
		t.Errorf("body = %s", api.Request("PUT "+commentsPath+"/c2").Body)
	}
	// The comment list read for the pick is a lookup, not the result.
	if got := flush(); strings.Contains(got, "Lovelace") || !strings.Contains(got, `"id": "c2"`) {
		t.Errorf("json =\n%s", got)
	}
}

func TestCommentUpdatePickQuitChangesNothing(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	flush := captureJSON(t)
	if _, _, err := run(t, "q\n", "update", "OPS-12"); err != nil {
		t.Fatal(err)
	}
	if got := strings.TrimSpace(flush()); got != "null" {
		t.Errorf("json = %s", got)
	}
	for _, call := range api.Calls() {
		if strings.HasPrefix(call, "PUT") {
			t.Errorf("unexpected %s", call)
		}
	}
}

func TestCommentDeleteWithoutTerminalDeletesAtOnce(t *testing.T) {
	api := newAPI(t)
	route := "DELETE " + commentsPath + "/" + idComment
	api.Respond(route, http.StatusOK, `{"data":null}`)
	out, _, err := run(t, "", "delete", "OPS-12", "--comment-id", idComment)
	if err != nil {
		t.Fatal(err)
	}
	if out != "Deleted comment "+idComment+"\n" || api.Request(route).Method != http.MethodDelete {
		t.Errorf("out %q calls %v", out, api.Calls())
	}
}

func TestCommentDeleteAsksOnATerminal(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	flush := captureJSON(t)
	out, _, err := run(t, "n\n", "delete", "OPS-12", "--comment-id", idComment)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasSuffix(out, "Cancelled.\n") || strings.TrimSpace(flush()) != "null" {
		t.Errorf("out = %q", out)
	}
	for _, call := range api.Calls() {
		if strings.HasPrefix(call, "DELETE") {
			t.Errorf("unexpected %s", call)
		}
	}
	route := "DELETE " + commentsPath + "/" + idComment
	api.Respond(route, http.StatusOK, `{"data":null}`)
	if out, _, err := run(t, "", "delete", "OPS-12", "--comment-id", idComment, "--yes"); err != nil || out != "Deleted comment "+idComment+"\n" {
		t.Errorf("--yes: out %q err %v", out, err)
	}
}

func TestCommentTicketIDUsesTheBoardFlag(t *testing.T) {
	api := newAPI(t)
	if _, _, err := run(t, "", "list", idTicket, "--board", "OPS"); err != nil {
		t.Fatal(err)
	}
	if strings.Join(api.Calls(), ",") != "GET /kazan/boards,GET "+commentsPath {
		t.Errorf("calls = %v", api.Calls())
	}
	_, _, err := run(t, "", "list", idTicket)
	if err == nil || err.Error() != "No board selected. Run 'oba kazan board use <board>'." {
		t.Errorf("err = %v", err)
	}
}

func TestCommentNeedsASignIn(t *testing.T) {
	clitest.SetupHome(t)
	_, _, err := run(t, "", "list", "OPS-12")
	if err == nil || !strings.Contains(err.Error(), "You are not logged in. Run 'oba login'.") {
		t.Fatalf("err = %v", err)
	}
}

func TestCommentNeedsTheTicket(t *testing.T) {
	newAPI(t)
	if _, _, err := run(t, "", "list"); err == nil {
		t.Fatal("expected an error without a ticket")
	}
}
