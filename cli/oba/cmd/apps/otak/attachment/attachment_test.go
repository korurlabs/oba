package attachment

import (
	"bytes"
	"net/http"
	"net/url"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/jsonout"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

const (
	outreachID      = "99999999-9999-4999-8999-999999999999"
	mailID          = "77777777-7777-4777-8777-777777777777"
	attachmentID    = "55555555-5555-4555-8555-555555555555"
	outreachPath    = "/otak/outreaches/" + outreachID
	attachmentsPath = "/storage/attachments"
)

func outreachJSON(outreachType, mail string) string {
	return `{"data":{"id":"` + outreachID + `","title":"First contact","type":"` + outreachType + `","status":"DRAFT",` +
		`"relationId":"` + clitest.RelationID + `","mail":` + mail + `,"createdAt":"2026-11-14T10:00:00Z"}}`
}

// serveMailOutreach serves the outreach detail of a MAIL outreach whose mail is mailID.
func serveMailOutreach(api *clitest.MockAPI) {
	api.Respond("GET "+outreachPath, http.StatusOK, outreachJSON("MAIL",
		`{"id":"`+mailID+`","mailTemplateId":null,"subject":"Hallo","body":"B","language":"NL"}`))
}

func attachmentJSON(id, name, contentType, entityType, entityID string) string {
	return `{"id":"` + id + `","url":"https://blob/x?sig=s","fileName":"` + name + `","contentType":"` + contentType +
		`","fileSize":1234,"entityType":"` + entityType + `","entityId":"` + entityID + `","createdAt":"2026-11-17T09:00:00Z",` +
		`"width":null,"height":null}`
}

// serveList answers the attachment list with items and records the query it was asked with.
func serveList(api *clitest.MockAPI, query *url.Values, items ...string) {
	api.Handle("GET "+attachmentsPath, func(w http.ResponseWriter, r *http.Request) {
		*query = r.URL.Query()
		clitest.WriteJSON(w, `{"data":[`+strings.Join(items, ",")+`]}`)
	})
}

func TestAttachmentListOfOutreachReadsTheMail(t *testing.T) {
	api := newAttachmentAPI(t, false)
	serveMailOutreach(api)
	var query url.Values
	serveList(api, &query, attachmentJSON(attachmentID, "voorstel.pdf", "application/pdf", "MAIL", mailID))

	out, _, err := runCLI(t, "", "otak", "attachment", "list", "--outreach", outreachID)
	if err != nil {
		t.Fatal(err)
	}
	if query.Get("entityType") != "MAIL" || query.Get("entityId") != mailID {
		t.Errorf("query = %v", query)
	}
	lines := strings.Split(strings.TrimSpace(out), "\n")
	if len(lines) != 2 || !strings.HasPrefix(lines[0], "NAME") {
		t.Fatalf("out =\n%s", out)
	}
	for _, want := range []string{"voorstel.pdf", "1234", "application/pdf", "2026-11-17T09:00:00Z", attachmentID} {
		if !strings.Contains(lines[1], want) {
			t.Errorf("row lacks %q: %q", want, lines[1])
		}
	}
	for _, r := range api.Requests() {
		if r.Path != "/v3/api-docs" && r.Org != clitest.OrgIDAcme {
			t.Errorf("%s %s sent X-Organization-ID %q", r.Method, r.Path, r.Org)
		}
	}
}

func TestAttachmentListOfRelation(t *testing.T) {
	api := newAttachmentAPI(t, false)
	var query url.Values
	serveList(api, &query)

	out, _, err := runCLI(t, "", "otak", "attachment", "list", "--relation", clitest.RelationID)
	if err != nil || out != "No attachments.\n" {
		t.Fatalf("out %q, err %v", out, err)
	}
	if query.Get("entityType") != "RELATION" || query.Get("entityId") != clitest.RelationID {
		t.Errorf("query = %v", query)
	}
	if got := api.Calls(); len(got) != 1 || got[0] != "GET "+attachmentsPath {
		t.Errorf("calls = %v", got)
	}
}

func TestAttachmentNeedsExactlyOneTarget(t *testing.T) {
	api := newAttachmentAPI(t, false)
	file := writeFile(t, t.TempDir(), "a.pdf", "%PDF-1.7")
	for _, args := range [][]string{
		{"otak", "attachment", "list"},
		{"otak", "attachment", "list", "--outreach", outreachID, "--relation", clitest.RelationID},
		{"otak", "attachment", "add", "--file", file},
		{"otak", "attachment", "delete", "--id", attachmentID, "--yes"},
	} {
		_, _, err := runCLI(t, "", args...)
		if err == nil || err.Error() != "pass exactly one of --outreach, --relation and --campaign" {
			t.Errorf("%v: err = %v", args, err)
		}
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}

func TestAttachmentOutreachWithoutMailIsRefused(t *testing.T) {
	api := newAttachmentAPI(t, false)
	api.Respond("GET "+outreachPath, http.StatusOK, outreachJSON("CALL", "null"))

	_, _, err := runCLI(t, "", "otak", "attachment", "list", "--outreach", outreachID)
	if err == nil || err.Error() != "outreach "+outreachID+" is a CALL outreach and has no mail to attach to" {
		t.Fatalf("err = %v", err)
	}
	if got := api.Calls(); len(got) != 1 {
		t.Errorf("calls = %v", got)
	}
}

func TestAttachmentAddFilesToOutreach(t *testing.T) {
	api := newAttachmentAPI(t, false)
	serveMailOutreach(api)
	dir := t.TempDir()
	pdf := writeFile(t, dir, "voorstel.pdf", "%PDF-1.7 a")
	notes := writeFile(t, dir, "notes", "plain text notes")
	var parts []clitest.FormPart
	api.Handle("POST "+attachmentsPath, func(w http.ResponseWriter, r *http.Request) {
		parts = clitest.ReadMultipartParts(t, r)
		w.WriteHeader(http.StatusCreated)
		clitest.WriteJSON(w, `{"data":[`+attachmentJSON("a1", "voorstel.pdf", "application/pdf", "MAIL", mailID)+","+
			attachmentJSON("a2", "notes", "text/plain; charset=utf-8", "MAIL", mailID)+`]}`)
	})

	out, _, err := runCLI(t, "", "otak", "attachment", "add", "--outreach", outreachID, "--file", pdf, "--file", notes)
	if err != nil {
		t.Fatal(err)
	}
	if out != "Added attachment a1 (voorstel.pdf) to outreach "+outreachID+"\nAdded attachment a2 (notes) to outreach "+outreachID+"\n" {
		t.Errorf("out = %q", out)
	}
	if len(parts) != 3 {
		t.Fatalf("parts = %+v", parts)
	}
	// The API matches each files part with the metadata entry at the same position by file name.
	metadata := `[{"fileName":"voorstel.pdf","entityType":"MAIL","entityId":"` + mailID + `"},` +
		`{"fileName":"notes","entityType":"MAIL","entityId":"` + mailID + `"}]`
	want := []clitest.FormPart{
		{Name: "metadata", ContentType: "application/json", Data: metadata},
		{Name: "files", FileName: "voorstel.pdf", ContentType: "application/pdf", Data: "%PDF-1.7 a"},
		{Name: "files", FileName: "notes", ContentType: "text/plain; charset=utf-8", Data: "plain text notes"},
	}
	for i := range want {
		if parts[i] != want[i] {
			t.Errorf("part %d = %+v, want %+v", i, parts[i], want[i])
		}
	}
}

func TestAttachmentAddRefusesBadInputBeforeAnyCall(t *testing.T) {
	api := newAttachmentAPI(t, false)
	dir := t.TempDir()
	a := writeFile(t, dir, "a.pdf", "%PDF same")
	b := writeFile(t, dir, "b.pdf", "%PDF same")
	empty := writeFile(t, dir, "empty.pdf", "")
	tests := []struct {
		name string
		args []string
		want string
	}{
		{"no file", nil, "pass --file with a local file to attach"},
		{"missing", []string{"--file", dir + "/nope.pdf"}, "failed to read " + dir + "/nope.pdf"},
		{"directory", []string{"--file", dir}, dir + " is not a regular file"},
		{"empty", []string{"--file", empty}, "is empty"},
		{"same content", []string{"--file", a, "--file", b}, b + " has the same content as " + a},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			args := append([]string{"otak", "attachment", "add", "--relation", clitest.RelationID}, tt.args...)
			_, _, err := runCLI(t, "", args...)
			if err == nil || !strings.Contains(err.Error(), tt.want) {
				t.Errorf("err = %v, want %q", err, tt.want)
			}
		})
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}

func TestAttachmentDeleteWithYes(t *testing.T) {
	api := newAttachmentAPI(t, false)
	serveMailOutreach(api)
	var query url.Values
	serveList(api, &query, attachmentJSON(attachmentID, "voorstel.pdf", "application/pdf", "MAIL", mailID))
	api.Respond("DELETE "+attachmentsPath+"/"+attachmentID, http.StatusOK, `{"message":"deleted"}`)

	out, _, err := runCLI(t, "", "otak", "attachment", "delete", "--outreach", outreachID,
		"--id", strings.ToUpper(attachmentID), "--yes")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Deleted attachment "+attachmentID+" (voorstel.pdf) of outreach "+outreachID+"\n" {
		t.Errorf("out = %q", out)
	}
	want := []string{"GET " + outreachPath, "GET " + attachmentsPath, "DELETE " + attachmentsPath + "/" + attachmentID}
	if got := api.Calls(); strings.Join(got, ";") != strings.Join(want, ";") {
		t.Errorf("calls = %v", got)
	}
}

func TestAttachmentDeleteRefusesAnIdOfAnotherParent(t *testing.T) {
	api := newAttachmentAPI(t, false)
	var query url.Values
	serveList(api, &query, attachmentJSON("other", "nda.pdf", "application/pdf", "RELATION", clitest.RelationID))

	_, _, err := runCLI(t, "", "otak", "attachment", "delete", "--relation", clitest.RelationID, "--id", attachmentID, "--yes")
	if err == nil || !strings.Contains(err.Error(), "attachment "+attachmentID+" is not an attachment of relation "+clitest.RelationID) {
		t.Fatalf("err = %v", err)
	}
	for _, call := range api.Calls() {
		if strings.HasPrefix(call, "DELETE") {
			t.Errorf("deleted: %v", call)
		}
	}
}

func TestAttachmentDeleteWithoutTerminalNeedsYes(t *testing.T) {
	api := newAttachmentAPI(t, false)
	_, _, err := runCLI(t, "", "otak", "attachment", "delete", "--relation", clitest.RelationID, "--id", attachmentID)
	if err != terminal.ErrDeleteNeedsYes {
		t.Fatalf("err = %v", err)
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}

func TestAttachmentDeleteAsksAndCancels(t *testing.T) {
	api := newAttachmentAPI(t, true)
	var query url.Values
	serveList(api, &query, attachmentJSON(attachmentID, "nda.pdf", "application/pdf", "RELATION", clitest.RelationID))

	out, _, err := runCLI(t, "n\n", "otak", "attachment", "delete", "--relation", clitest.RelationID, "--id", attachmentID)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Delete attachment nda.pdf of relation "+clitest.RelationID+"?") || !strings.HasSuffix(out, "Cancelled.\n") {
		t.Errorf("out = %q", out)
	}
	for _, call := range api.Calls() {
		if strings.HasPrefix(call, "DELETE") {
			t.Errorf("deleted: %v", call)
		}
	}
}

// With --json, a cancelled delete gives null, not the attachment list read to ask the question.
func TestAttachmentDeleteCancelledJSONIsNull(t *testing.T) {
	api := newAttachmentAPI(t, true)
	var query url.Values
	serveList(api, &query, attachmentJSON(attachmentID, "nda.pdf", "application/pdf", "RELATION", clitest.RelationID))
	var buf bytes.Buffer
	jsonout.Start(&buf)
	t.Cleanup(jsonout.Stop)

	if _, _, err := runCLI(t, "n\n", "otak", "attachment", "delete", "--relation", clitest.RelationID, "--id", attachmentID); err != nil {
		t.Fatal(err)
	}
	if err := jsonout.Flush(); err != nil {
		t.Fatal(err)
	}
	if strings.TrimSpace(buf.String()) != "null" {
		t.Errorf("result = %q, want null", buf.String())
	}
}

func TestAttachmentDeleteNeedsID(t *testing.T) {
	newAttachmentAPI(t, false)
	_, _, err := runCLI(t, "", "otak", "attachment", "delete", "--relation", clitest.RelationID, "--yes")
	if err == nil || !strings.Contains(err.Error(), `"id" not set`) {
		t.Fatalf("err = %v", err)
	}
}
