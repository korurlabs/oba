package attachment

import (
	"net/http"
	"net/url"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

func TestAttachmentDeleteWithYes(t *testing.T) {
	api := newAttachmentAPI(t, false)
	var query url.Values
	serveList(api, &query, attachmentJSON(attachmentID, "policy.pdf", "application/pdf"))
	api.Respond("DELETE "+attachmentsPath+"/"+attachmentID, http.StatusOK, `{"message":"deleted"}`)

	out, _, err := runCLI(t, "", "kalkan", "attachment", "delete", "--page", "engineering/plans",
		"--id", strings.ToUpper(attachmentID), "--yes")
	if err != nil {
		t.Fatal(err)
	}
	if out != "Deleted attachment "+attachmentID+" (policy.pdf) of page engineering/plans\n" {
		t.Errorf("out = %q", out)
	}
	want := []string{"GET " + pagesPath, "GET " + attachmentsPath, "DELETE " + attachmentsPath + "/" + attachmentID}
	if got := api.Calls(); strings.Join(got, ";") != strings.Join(want, ";") {
		t.Errorf("calls = %v", got)
	}
	if query.Get("entityType") != "KALKAN_PAGE" || query.Get("entityId") != pageID {
		t.Errorf("query = %v", query)
	}
}

// The API keeps the attachments of a Kalkan page as a record and refuses their delete with 403.
func TestAttachmentDeleteRefusedByTheAPIExits3(t *testing.T) {
	api := newAttachmentAPI(t, false)
	var query url.Values
	serveList(api, &query, attachmentJSON(attachmentID, "policy.pdf", "application/pdf"))
	api.Respond("DELETE "+attachmentsPath+"/"+attachmentID, http.StatusForbidden,
		`{"code":"KLK_SEC_002","message":"Attachments of a Kalkan page are kept as a record and are never deleted."}`)

	_, _, err := runCLI(t, "", "kalkan", "attachment", "delete", "--page", "engineering/plans", "--id", attachmentID, "--yes")
	if err == nil || client.ExitCode(err) != 3 || !strings.Contains(err.Error(), "never deleted") {
		t.Fatalf("err = %v", err)
	}
}

func TestAttachmentDeleteRefusesAnIdOfAnotherPage(t *testing.T) {
	api := newAttachmentAPI(t, false)
	var query url.Values
	serveList(api, &query, attachmentJSON("other", "nda.pdf", "application/pdf"))

	_, _, err := runCLI(t, "", "kalkan", "attachment", "delete", "--page", "engineering/plans", "--id", attachmentID, "--yes")
	if err == nil || !strings.Contains(err.Error(), "attachment "+attachmentID+" is not an attachment of page engineering/plans") {
		t.Fatalf("err = %v", err)
	}
	noDelete(t, api)
}

func TestAttachmentDeleteWithoutTerminalNeedsYes(t *testing.T) {
	api := newAttachmentAPI(t, false)
	_, _, err := runCLI(t, "", "kalkan", "attachment", "delete", "--page", "engineering/plans", "--id", attachmentID)
	if err != terminal.ErrDeleteNeedsYes {
		t.Fatalf("err = %v", err)
	}
	if got := api.Calls(); len(got) != 0 {
		t.Errorf("calls = %v", got)
	}
}

func TestAttachmentDeleteAsksAndConfirms(t *testing.T) {
	api := newAttachmentAPI(t, true)
	var query url.Values
	serveList(api, &query, attachmentJSON(attachmentID, "policy.pdf", "application/pdf"))
	api.Respond("DELETE "+attachmentsPath+"/"+attachmentID, http.StatusOK, `{"message":"deleted"}`)

	out, _, err := runCLI(t, "y\n", "kalkan", "attachment", "delete", "--page", "engineering/plans", "--id", attachmentID)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Delete attachment policy.pdf of page engineering/plans?") ||
		!strings.HasSuffix(out, "Deleted attachment "+attachmentID+" (policy.pdf) of page engineering/plans\n") {
		t.Errorf("out = %q", out)
	}
}

func TestAttachmentDeleteAsksAndCancels(t *testing.T) {
	api := newAttachmentAPI(t, true)
	var query url.Values
	serveList(api, &query, attachmentJSON(attachmentID, "policy.pdf", "application/pdf"))

	out, _, err := runCLI(t, "n\n", "kalkan", "attachment", "delete", "--page", "engineering/plans", "--id", attachmentID)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(out, "Delete attachment policy.pdf of page engineering/plans?") || !strings.HasSuffix(out, "Cancelled.\n") {
		t.Errorf("out = %q", out)
	}
	noDelete(t, api)
}

// With --json, a cancelled delete gives null, not the attachment list read to ask the question.
func TestAttachmentDeleteCancelledJSONIsNull(t *testing.T) {
	api := newAttachmentAPI(t, true)
	var query url.Values
	serveList(api, &query, attachmentJSON(attachmentID, "policy.pdf", "application/pdf"))
	buf := captureJSON(t)

	if _, _, err := runCLI(t, "n\n", "kalkan", "attachment", "delete", "--page", "engineering/plans", "--id", attachmentID); err != nil {
		t.Fatal(err)
	}
	if got := flushJSON(t, buf); got != "null" {
		t.Errorf("result = %q, want null", got)
	}
}

func TestAttachmentDeleteNeedsID(t *testing.T) {
	newAttachmentAPI(t, false)
	_, _, err := runCLI(t, "", "kalkan", "attachment", "delete", "--page", "engineering/plans", "--yes")
	if err == nil || !strings.Contains(err.Error(), `"id" not set`) {
		t.Fatalf("err = %v", err)
	}
}
