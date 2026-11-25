package attachment

import (
	"net/http"
	"net/url"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestAttachmentListOfPage(t *testing.T) {
	api := newAttachmentAPI(t)
	var query url.Values
	serveList(api, &query, attachmentJSON(attachmentID, "policy.pdf", "application/pdf"))

	out, _, err := runCLI(t, "", "kalkan", "attachment", "list", "--page", "/engineering/plans/")
	if err != nil {
		t.Fatal(err)
	}
	if query.Get("entityType") != "KALKAN_PAGE" || query.Get("entityId") != pageID {
		t.Errorf("query = %v", query)
	}
	lines := strings.Split(strings.TrimSpace(out), "\n")
	if len(lines) != 2 || !strings.HasPrefix(lines[0], "NAME") {
		t.Fatalf("out =\n%s", out)
	}
	for _, want := range []string{"policy.pdf", "1234", "application/pdf", "2026-11-17T09:00:00Z", attachmentID} {
		if !strings.Contains(lines[1], want) {
			t.Errorf("row lacks %q: %q", want, lines[1])
		}
	}
	if got := strings.Join(api.Calls(), ";"); got != "GET "+pagesPath+";GET "+attachmentsPath {
		t.Errorf("calls = %v", got)
	}
}

func TestAttachmentListEmpty(t *testing.T) {
	api := newAttachmentAPI(t)
	var query url.Values
	serveList(api, &query)

	out, _, err := runCLI(t, "", "kalkan", "attachment", "list", "--page", "engineering/plans")
	if err != nil || out != "No attachments.\n" {
		t.Fatalf("out %q, err %v", out, err)
	}
}

// With --json, the result is the attachment list, not the tree read to find the page.
func TestAttachmentListJSONIsTheList(t *testing.T) {
	api := newAttachmentAPI(t)
	var query url.Values
	serveList(api, &query, attachmentJSON(attachmentID, "policy.pdf", "application/pdf"))
	buf := captureJSON(t)

	if _, _, err := runCLI(t, "", "kalkan", "attachment", "list", "--page", "engineering/plans"); err != nil {
		t.Fatal(err)
	}
	got := flushJSON(t, buf)
	if !strings.HasPrefix(got, "[") || !strings.Contains(got, `"fileName": "policy.pdf"`) {
		t.Errorf("result = %s", got)
	}
}

// A caller below the VIEW level of the page's root gets the API's 403, which exits 3.
func TestAttachmentListForbiddenExits3(t *testing.T) {
	api := newAttachmentAPI(t)
	api.Respond("GET "+attachmentsPath, http.StatusForbidden, `{"code":"KLK_SEC_001","message":"No access"}`)

	_, _, err := runCLI(t, "", "kalkan", "attachment", "list", "--page", "engineering/plans")
	if err == nil || client.ExitCode(err) != 3 {
		t.Fatalf("err = %v", err)
	}
}
