package attachment

import (
	"bytes"
	"io"
	"mime"
	"mime/multipart"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/cmd/apps/kazan/boardflag"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
)

const (
	idOps    = "0a000000-0000-4000-8000-000000000001"
	idTicket = "0b000000-0000-4000-8000-000000000001"

	treeJSON = `{"data":[{"id":"` + idOps + `","name":"Operations","prefix":"OPS","childBoards":[]}],` +
		`"pagination":{"currentPage":1,"last":true}}`
	ticketJSON      = `{"data":{"id":"` + idTicket + `","key":"OPS-12","board":{"id":"` + idOps + `","name":"Operations","prefix":"OPS"}}}`
	attachmentsJSON = `{"data":[{"id":"a1","fileName":"shot.png","contentType":"image/png","fileSize":3,` +
		`"entityType":"TICKET","entityId":"` + idTicket + `","createdAt":"2026-11-18T09:00:00Z"}]}`
)

func newRoot() *cobra.Command {
	group := &cobra.Command{Use: "kazan"}
	boardflag.Add(group)
	group.AddCommand(NewCmd())
	root := &cobra.Command{Use: "oba", SilenceUsage: true, SilenceErrors: true}
	root.AddCommand(group)
	return root
}

func run(t *testing.T, stdin string, args ...string) (string, string, error) {
	t.Helper()
	return clitest.RunCLILines(t, newRoot(), stdin, append([]string{"kazan", "attachment"}, args...)...)
}

func newAPI(t *testing.T) *clitest.MockAPI {
	t.Helper()
	clitest.SetupHome(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, false)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /kazan/boards", http.StatusOK, treeJSON)
	api.Respond("GET /kazan/tickets/by-key/OPS-12", http.StatusOK, ticketJSON)
	api.Respond("GET /storage/attachments", http.StatusOK, attachmentsJSON)
	clitest.LoginTo(t, api.URL(), clitest.OrgIDAcme)
	return api
}

func captureJSON(t *testing.T) func() string {
	t.Helper()
	var buf bytes.Buffer
	jsonout.Start(&buf)
	t.Cleanup(jsonout.Stop)
	return func() string {
		if err := jsonout.Flush(); err != nil {
			t.Fatal(err)
		}
		return buf.String()
	}
}

func TestAttachmentListPrintsTheTable(t *testing.T) {
	api := newAPI(t)
	out, _, err := run(t, "", "list", "OPS-12")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "NAME") || !strings.Contains(out, "shot.png") || !strings.Contains(out, "a1") {
		t.Errorf("out =\n%s", out)
	}
	if strings.Join(api.Calls(), ",") != "GET /kazan/boards,GET /kazan/tickets/by-key/OPS-12,GET /storage/attachments" {
		t.Errorf("calls = %v", api.Calls())
	}
}

func TestAttachmentListJSONIsTheAttachmentsOfTheTicket(t *testing.T) {
	api := newAPI(t)
	var query string
	api.Handle("GET /storage/attachments", func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.RawQuery
		clitest.WriteJSON(w, attachmentsJSON)
	})
	flush := captureJSON(t)
	if _, _, err := run(t, "", "list", "OPS-12"); err != nil {
		t.Fatal(err)
	}
	if query != "entityId="+idTicket+"&entityType=TICKET" {
		t.Errorf("query = %s", query)
	}
	if got := flush(); !strings.Contains(got, `"fileName": "shot.png"`) || strings.Contains(got, `"key"`) {
		t.Errorf("json =\n%s", got)
	}
}

func TestAttachmentAddChecksThenUploads(t *testing.T) {
	api := newAPI(t)
	dir := t.TempDir()
	path := filepath.Join(dir, "log.txt")
	if err := os.WriteFile(path, []byte("log"), 0o600); err != nil {
		t.Fatal(err)
	}
	api.Respond("POST /storage/attachments/exists", http.StatusOK, `{"data":{"exists":false}}`)
	var metadata string
	api.Handle("POST /storage/attachments", func(w http.ResponseWriter, r *http.Request) {
		_, params, _ := mime.ParseMediaType(r.Header.Get("Content-Type"))
		reader := multipart.NewReader(r.Body, params["boundary"])
		for {
			part, err := reader.NextPart()
			if err != nil {
				break
			}
			if part.FormName() == "metadata" {
				body, _ := io.ReadAll(part)
				metadata = string(body)
			}
		}
		w.WriteHeader(http.StatusCreated)
		clitest.WriteJSON(w, `{"data":[{"id":"a9","fileName":"log.txt"}]}`)
	})
	out, _, err := run(t, "", "add", "OPS-12", "--file", path)
	if err != nil {
		t.Fatal(err)
	}
	if out != "Attached 1 file(s) to OPS-12: log.txt\n" {
		t.Errorf("out = %q", out)
	}
	if metadata != `[{"fileName":"log.txt","entityType":"TICKET","entityId":"`+idTicket+`"}]` {
		t.Errorf("metadata = %s", metadata)
	}
	want := "GET /kazan/boards,GET /kazan/tickets/by-key/OPS-12,POST /storage/attachments/exists,POST /storage/attachments"
	if strings.Join(api.Calls(), ",") != want {
		t.Errorf("calls = %v", api.Calls())
	}
}

func TestAttachmentAddRefusesADuplicateBeforeTheUpload(t *testing.T) {
	api := newAPI(t)
	path := filepath.Join(t.TempDir(), "log.txt")
	if err := os.WriteFile(path, []byte("log"), 0o600); err != nil {
		t.Fatal(err)
	}
	api.Respond("POST /storage/attachments/exists", http.StatusOK, `{"data":{"exists":true}}`)
	_, _, err := run(t, "", "add", "OPS-12", "--file", path)
	if err == nil || !strings.Contains(err.Error(), `"log.txt" has the same content`) {
		t.Fatalf("err = %v", err)
	}
}

func TestAttachmentAddChecksTheFilesFirst(t *testing.T) {
	api := newAPI(t)
	_, _, err := run(t, "", "add", "OPS-12")
	if err == nil || !strings.Contains(err.Error(), "--file is required") {
		t.Fatalf("err = %v", err)
	}
	if len(api.Calls()) != 0 {
		t.Errorf("calls = %v", api.Calls())
	}
}

func TestAttachmentDeleteChecksTheTicketFirst(t *testing.T) {
	api := newAPI(t)
	_, _, err := run(t, "", "delete", "OPS-12", "--id", "zz")
	if client.ExitCode(err) != 2 || !strings.Contains(err.Error(), "attachment zz is not attached to this ticket") {
		t.Fatalf("err = %v", err)
	}
	api.Respond("DELETE /storage/attachments/a1", http.StatusOK, `{"data":null}`)
	out, _, err := run(t, "", "delete", "OPS-12", "--id", "A1")
	if err != nil || out != "Deleted attachment shot.png\n" {
		t.Fatalf("out %q err %v", out, err)
	}
}

func TestAttachmentDeleteAsksOnATerminal(t *testing.T) {
	api := newAPI(t)
	clitest.FakeTerminal(t, &stdinIsTerminal, true)
	flush := captureJSON(t)
	out, _, err := run(t, "n\n", "delete", "OPS-12", "--id", "a1")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Delete attachment 'shot.png'?") || !strings.HasSuffix(out, "Cancelled.\n") {
		t.Errorf("out = %q", out)
	}
	if got := strings.TrimSpace(flush()); got != "null" {
		t.Errorf("json = %s", got)
	}
	for _, call := range api.Calls() {
		if strings.HasPrefix(call, "DELETE") {
			t.Errorf("unexpected %s", call)
		}
	}
}

func TestAttachmentDeleteNeedsTheID(t *testing.T) {
	api := newAPI(t)
	_, _, err := run(t, "", "delete", "OPS-12")
	if err == nil || !strings.Contains(err.Error(), "--id is required") || len(api.Calls()) != 0 {
		t.Fatalf("err = %v, calls = %v", err, api.Calls())
	}
}
